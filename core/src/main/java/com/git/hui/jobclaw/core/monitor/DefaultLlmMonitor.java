package com.git.hui.jobclaw.core.monitor;

import com.git.hui.jobclaw.core.providers.ModelProviders;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Component
public class DefaultLlmMonitor implements LlmMonitor {
    private static final ThreadLocal<InvocationState> CURRENT = new ThreadLocal<>();
    private final ApplicationEventPublisher publisher;
    private final MeterRegistry meterRegistry;

    public DefaultLlmMonitor(ApplicationEventPublisher publisher, MeterRegistry meterRegistry) {
        this.publisher = publisher;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public <T> T call(LlmCallContext context, Prompt prompt, Supplier<T> supplier) {
        InvocationState state = new InvocationState(context);
        CURRENT.set(state);
        try {
            T result = request(prompt, supplier);
            publishInvocation(state, "SUCCESS", null);
            return result;
        } catch (RuntimeException e) {
            publishInvocation(state, "FAILED", e);
            throw e;
        } finally {
            CURRENT.remove();
            ModelProviders.clearCurrentModelInfo();
        }
    }

    @Override
    public <T> Flux<T> stream(LlmCallContext context, Prompt prompt, Supplier<Flux<ChatResponse>> supplier,
                              Function<ChatResponse, T> mapper) {
        return Flux.defer(() -> {
            InvocationState state = new InvocationState(context);
            CURRENT.set(state);
            long start = System.nanoTime();
            StreamResponseCapture capture = new StreamResponseCapture();
            try {
                return supplier.get()
                        .doOnNext(capture::accept)
                        .map(mapper)
                        .doOnError(e -> {
                            publishStreamRequest(state, prompt, capture, "FAILED", start, e);
                            publishInvocation(state, "FAILED", e);
                        })
                        .doOnComplete(() -> {
                            publishStreamRequest(state, prompt, capture, "SUCCESS", start, null);
                            publishInvocation(state, "SUCCESS", null);
                        })
                        .doOnCancel(() -> {
                            publishStreamRequest(state, prompt, capture, "CANCELLED", start, null);
                            publishInvocation(state, "CANCELLED", null);
                        })
                        .doFinally(signal -> {
                            CURRENT.remove();
                            ModelProviders.clearCurrentModelInfo();
                        });
            } catch (RuntimeException e) {
                publishStreamRequest(state, prompt, capture, "FAILED", start, e);
                publishInvocation(state, "FAILED", e);
                CURRENT.remove();
                ModelProviders.clearCurrentModelInfo();
                throw e;
            }
        });
    }

    @Override
    public ChatResponse directCall(Prompt prompt, Supplier<ChatResponse> supplier) {
        return request(prompt, supplier);
    }

    private <T> T request(Prompt prompt, Supplier<T> supplier) {
        InvocationState state = CURRENT.get();
        if (state == null) {
            return supplier.get();
        }
        long start = System.nanoTime();
        try {
            T result = supplier.get();
            publishRequest(state, prompt, result instanceof ChatResponse r ? r : null, "SUCCESS", start, null);
            return result;
        } catch (RuntimeException e) {
            publishRequest(state, prompt, null, "FAILED", start, e);
            throw e;
        }
    }

    private void publishStreamRequest(InvocationState state, Prompt prompt, StreamResponseCapture capture,
                                      String outcome, long start, Throwable error) {
        publishRequest(state, prompt, capture.usageResponse.get(), capture.responseText.toString(), outcome, start, error);
    }

    private void publishRequest(InvocationState state, Prompt prompt, ChatResponse response, String outcome, long start, Throwable error) {
        publishRequest(state, prompt, response, response == null ? null : response.getResult().getOutput().getText(),
                outcome, start, error);
    }

    private void publishRequest(InvocationState state, Prompt prompt, ChatResponse response, String responseText,
                                String outcome, long start, Throwable error) {
        long duration = (System.nanoTime() - start) / 1_000_000;
        state.requests.incrementAndGet();
        state.duration.addAndGet(duration);
        LlmCallContext c = state.context;
        String promptText = prompt.getInstructions().stream().map(Message::getText).reduce("", (a, b) -> a + "\n" + b);
        Long inputTokens = null, outputTokens = null, totalTokens = null;
        if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            var usage = response.getMetadata().getUsage();
            inputTokens = usage.getPromptTokens() == null ? null : usage.getPromptTokens().longValue();
            outputTokens = usage.getCompletionTokens() == null ? null : usage.getCompletionTokens().longValue();
            totalTokens = usage.getTotalTokens() == null ? null : usage.getTotalTokens().longValue();
            state.usageKnown.set(true);
            state.inputTokens.addAndGet(inputTokens == null ? 0 : inputTokens);
            state.outputTokens.addAndGet(outputTokens == null ? 0 : outputTokens);
            state.totalTokens.addAndGet(totalTokens == null ? 0 : totalTokens);
        }
        var modelInfo = ModelProviders.currentModelInfo();
        String provider = modelInfo == null ? "unknown" : modelInfo.getProvider();
        String model = modelInfo == null ? "unknown" : modelInfo.getModelName();
        String modelType = modelInfo == null || modelInfo.getType() == null ? "CHAT" : modelInfo.getType().name();
        BigDecimal cost = calculateCost(modelInfo, inputTokens, outputTokens);
        state.addCost(cost);
        LlmRequestRecord record = new LlmRequestRecord(UUID.randomUUID().toString(), c.invocationId(), c.jobClawUserId(),
                c.conversationId(), c.channel(), c.agent(), c.operation(), c.mode(), provider, model, modelType,
                outcome, duration, inputTokens, outputTokens, totalTokens, cost, error == null ? null : error.getMessage(),
                sample(promptText, outcome), sample(responseText, outcome),
                Instant.now());
        safePublish(record);
        meterRegistry.counter("jobclaw.llm.requests", "agent", tag(c.agent()), "operation", tag(c.operation()),
                "provider", tag(provider), "model", tag(model), "mode", tag(c.mode()), "outcome", outcome).increment();
        Timer.builder("jobclaw.llm.request.duration").tags("agent", tag(c.agent()), "operation", tag(c.operation()),
                "provider", tag(provider), "model", tag(model), "mode", tag(c.mode()), "outcome", outcome)
                .register(meterRegistry).record(java.time.Duration.ofMillis(duration));
        if (totalTokens != null) meterRegistry.counter("jobclaw.llm.tokens", "agent", tag(c.agent()), "operation", tag(c.operation()),
                "provider", tag(provider), "model", tag(model), "mode", tag(c.mode()), "outcome", outcome).increment(totalTokens);
        if (cost != null) meterRegistry.counter("jobclaw.llm.estimated.cost", "agent", tag(c.agent()), "operation", tag(c.operation()),
                "provider", tag(provider), "model", tag(model), "mode", tag(c.mode()), "outcome", outcome).increment(cost.doubleValue());
    }

    private void publishInvocation(InvocationState state, String outcome, Throwable error) {
        if (!state.published.compareAndSet(0, 1)) return;
        LlmCallContext c = state.context;
        safePublish(new LlmInvocationRecord(c.invocationId(), c.jobClawUserId(), c.conversationId(), c.channel(),
                c.agent(), c.operation(), c.mode(), outcome, state.duration.get(), state.requests.get(),
                state.usageKnown.get() ? state.inputTokens.get() : null,
                state.usageKnown.get() ? state.outputTokens.get() : null,
                state.usageKnown.get() ? state.totalTokens.get() : null,
                state.cost, error == null ? null : error.getMessage(), state.started));
        meterRegistry.counter("jobclaw.llm.invocations", "agent", tag(c.agent()), "operation", tag(c.operation()),
                "mode", tag(c.mode()), "outcome", outcome).increment();
    }

    private void safePublish(Object event) {
        try {
            publisher.publishEvent(event);
        } catch (Exception e) {
            meterRegistry.counter("jobclaw.llm.telemetry.dropped").increment();
            log.warn("Failed to publish LLM monitor event", e);
        }
    }

    private String sample(String text, String outcome) {
        if (text == null || (!"FAILED".equals(outcome) && Math.random() >= 0.01)) return null;
        String cleaned = text.replaceAll("(?i)Bearer\\s+\\S+", "Bearer ***")
                .replaceAll("[\\w.+-]+@[\\w.-]+", "***@***")
                .replaceAll("1[3-9]\\d{9}", "***")
                .replaceAll("\\b\\d{17}[\\dXx]\\b", "***");
        return cleaned.substring(0, Math.min(cleaned.length(), 4000));
    }

    private static String tag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private BigDecimal calculateCost(com.git.hui.jobclaw.core.providers.ModelConfig.ModelInfo info, Long input, Long output) {
        if (info == null || (input == null && output == null)) return null;
        BigDecimal cost = BigDecimal.ZERO;
        boolean known = false;
        if (input != null && info.getInputPricePerMillionTokens() != null) {
            cost = cost.add(info.getInputPricePerMillionTokens().multiply(BigDecimal.valueOf(input)));
            known = true;
        }
        if (output != null && info.getOutputPricePerMillionTokens() != null) {
            cost = cost.add(info.getOutputPricePerMillionTokens().multiply(BigDecimal.valueOf(output)));
            known = true;
        }
        return known ? cost.divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP) : null;
    }

    private static class InvocationState {
        final LlmCallContext context;
        final Instant started = Instant.now();
        final AtomicInteger requests = new AtomicInteger();
        final AtomicLong duration = new AtomicLong();
        final AtomicLong inputTokens = new AtomicLong();
        final AtomicLong outputTokens = new AtomicLong();
        final AtomicLong totalTokens = new AtomicLong();
        final AtomicBoolean usageKnown = new AtomicBoolean();
        BigDecimal cost;
        final AtomicInteger published = new AtomicInteger();
        InvocationState(LlmCallContext context) { this.context = context; }
        synchronized void addCost(BigDecimal value) {
            if (value != null) cost = cost == null ? value : cost.add(value);
        }
    }

    private static class StreamResponseCapture {
        final AtomicReference<ChatResponse> usageResponse = new AtomicReference<>();
        final StringBuilder responseText = new StringBuilder();

        void accept(ChatResponse response) {
            if (response == null) return;

            log.info("Response metadata: {}, usage: {}",
                    response.getMetadata(),
                    response.getMetadata() != null ? response.getMetadata().getUsage() : null);

            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                usageResponse.set(response);
            }
            if (response.getResult() != null && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null) {
                responseText.append(response.getResult().getOutput().getText());
            }
        }
    }
}
