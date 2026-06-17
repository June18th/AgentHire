package com.git.hui.jobclaw.core.monitor;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.function.Function;
import java.util.function.Supplier;

public interface LlmMonitor {
    <T> T call(LlmCallContext context, Prompt prompt, Supplier<T> supplier);

    <T> Flux<T> stream(LlmCallContext context, Prompt prompt, Supplier<Flux<ChatResponse>> supplier,
                       Function<ChatResponse, T> mapper);

    ChatResponse directCall(Prompt prompt, Supplier<ChatResponse> supplier);
}
