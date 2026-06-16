package com.git.hui.jobclaw.core.agent.react;

import com.git.hui.jobclaw.core.utils.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import com.git.hui.jobclaw.core.monitor.LlmMonitor;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义 ReAct (Reasoning-Acting) Advisor
 * <p>
 * 替代 Spring AI 内置的 ToolCallAdvisor，自行控制 reasoning → acting 的完整循环。
 * 核心设计：
 * <ol>
 *   <li>第一次推理通过 Advisor Chain 调用（保留 Memory 等前置 Advisor 的效果）</li>
 *   <li>后续迭代直接调用 ChatModel（避免重复触发 Memory 等 Advisor）</li>
 *   <li>工具通过 ToolCallback.call(args, toolContext) 手动执行，参照 DefaultToolCallingManager
 *       从 ToolCallingChatOptions 构建 ToolContext 并始终传入，确保工具可访问上下文信息</li>
 *   <li>各阶段触发 Middleware 生命周期钩子</li>
 * </ol>
 * <p>
 * AIDEV-NOTE: 使用此 Advisor 时应移除 ToolCallAdvisor，避免工具被重复执行。
 * 需要设置 internalToolExecutionEnabled(false) 以禁用 Spring AI 的自动工具执行。
 *
 * @author YiHui
 * @date 2026/6/5
 */
@Slf4j
public class ReActAdvisor implements CallAdvisor, StreamAdvisor {

    private final ChatModel chatModel;
    private final List<ReActMiddleware> middlewares;
    private final int maxIterations;
    private final int order;

    private ReActAdvisor(Builder builder) {
        this.chatModel = builder.chatModel;
        this.middlewares = builder.middlewares;
        this.maxIterations = builder.maxIterations;
        this.order = builder.order;
    }

    // ==================== CallAdvisor ====================

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        log.debug("[ReAct] adviseCall start, maxIterations={}", maxIterations);

        // 第一次推理走 Advisor Chain（保留 Memory 等前置效果）
        ChatClientResponse firstResponse = chain.nextCall(request);
        ChatResponse chatResponse = firstResponse.chatResponse();

        if (chatResponse == null || chatResponse.getResult() == null) {
            return firstResponse;
        }

        AssistantMessage output = chatResponse.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();

        // 无工具调用 → 直接返回
        if (toolCalls == null || toolCalls.isEmpty()) {
            notifyComplete(1, output.getText());
            return firstResponse;
        }

        // 进入 ReAct 循环
        List<Message> messages = new ArrayList<>(request.prompt().getInstructions());
        messages.add(output);

        // Phase 3: 传递请求上下文给中间件
        notifySetContext(request);
        ReactLoopResult loopResult = runReactLoop(messages, toolCalls, request, 1);
        if (loopResult != null && loopResult.finalResponse != null) {
            notifyComplete(loopResult.iterations(),
                    loopResult.finalResponse.chatResponse().getResult().getOutput().getText());
            return firstResponse.mutate()
                    .chatResponse(loopResult.finalResponse.chatResponse())
                    .build();
        }

        log.warn("[ReAct] Max iterations ({}) reached", maxIterations);
        return firstResponse;
    }

    // ==================== StreamAdvisor ====================

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        log.debug("[ReAct] adviseStream start, maxIterations={}", maxIterations);

        // AIDEV-NOTE: 先收集所有 chunks，再统一处理。
        // 不能用 aggregateChatClientResponse（Consumer 副作用无法向下游追加新 chunks）。
        // 工具调用场景的响应较短，缓冲延迟可接受。
        return chain.nextStream(request)
                .collectList()
                .flatMapMany(chunks -> {
                    if (chunks.isEmpty()) {
                        return Flux.<ChatClientResponse>empty();
                    }

                    // 检查聚合后的响应是否包含工具调用
                    ChatClientResponse firstResponse = aggregateChunks(chunks);
                    ChatResponse chatResponse = firstResponse.chatResponse();
                    if (chatResponse == null || chatResponse.getResult() == null) {
                        log.warn("[ReAct] aggregateChunks returned null response, emitting {} raw chunks", chunks.size());
                        return Flux.fromIterable(chunks);
                    }

                    AssistantMessage output = chatResponse.getResult().getOutput();
                    List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
                    log.info("[ReAct] Aggregated {} chunks, toolCalls={}, textLen={}",
                            chunks.size(),
                            toolCalls != null ? toolCalls.size() : 0,
                            output.getText() != null ? output.getText().length() : 0);

                    // 无工具调用 → 直接发射原始 chunks
                    if (toolCalls == null || toolCalls.isEmpty()) {
                        notifyComplete(1, output.getText());
                        return Flux.fromIterable(chunks);
                    }

                    // 进入 ReAct 循环
                    List<Message> messages = new ArrayList<>(request.prompt().getInstructions());
                    messages.add(output);

                    // Phase 3: 传递请求上下文给中间件
                    notifySetContext(request);
                    try {
                        ReactLoopResult loopResult = runReactLoop(messages, toolCalls, request, 1);
                        if (loopResult != null && loopResult.finalResponse != null) {
                            List<ChatClientResponse> result = new ArrayList<>(chunks);
                            // 发射工具执行结果，使其流经 LlmRspCell
                            ChatClientResponse toolResultResp = buildToolResultResponse(loopResult.toolResponses, request);
                            result.add(toolResultResp);
                            // 发射模型最终响应
                            result.add(loopResult.finalResponse);
                            log.info("[ReAct] Stream emitting {} items ({} original + toolResult + final), toolResult metadata={}",
                                    result.size(), chunks.size(),
                                    toolResultResp.chatResponse().getResult().getOutput().getMetadata());
                            notifyComplete(loopResult.iterations(),
                                    loopResult.finalResponse.chatResponse().getResult().getOutput().getText());
                            return Flux.fromIterable(result);
                        } else {
                            log.warn("[ReAct] runReactLoop returned null (loopResult={}), falling back to raw chunks", loopResult);
                        }
                    } catch (Exception e) {
                        log.error("[ReAct] Stream ReAct loop failed", e);
                        notifyError(e, -1);
                    }

                    return Flux.fromIterable(chunks);
                });
    }

    // ==================== ReAct 循环核心 ====================

    /**
     * ReAct 循环的结果容器，包含最终响应和工具执行结果
     *
     * @param finalResponse 模型最终响应（无工具调用时的 ChatClientResponse）
     * @param toolResponses 所有迭代的工具执行结果
     * @param iterations    实际迭代次数
     */
    private record ReactLoopResult(ChatClientResponse finalResponse,
                                    List<ToolResponseMessage> toolResponses,
                                    int iterations) {}

    /**
     * 同步执行 ReAct 循环（工具调用本质上是阻塞的）
     *
     * @return ReactLoopResult 包含最终响应、工具执行结果和迭代次数；达到最大迭代次数时返回 null
     */
    private ReactLoopResult runReactLoop(List<Message> messages,
                                          List<AssistantMessage.ToolCall> toolCalls,
                                          ChatClientRequest request,
                                          int startIter) {
        List<ToolResponseMessage> allToolResponses = new ArrayList<>();

        for (int iter = startIter; iter < maxIterations; iter++) {
            // ---- Acting: 执行工具 ----
            ToolResponseMessage toolResponses = executeTools(toolCalls, request, iter);
            if (toolResponses == null) return null;
            allToolResponses.add(toolResponses);
            messages.add(toolResponses);

            // ---- Reasoning: 再次推理 ----
            notifyBeforeReasoning(messages, iter);
            ChatResponse nextResponse = callModelDirect(messages, request);
            if (nextResponse == null || nextResponse.getResult() == null) return null;

            AssistantMessage output = nextResponse.getResult().getOutput();
            notifyAfterReasoning(output, iter);
            messages.add(output);

            toolCalls = output.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                ChatClientResponse finalResponse = ChatClientResponse.builder()
                        .chatResponse(nextResponse)
                        .context(request.context())
                        .build();
                return new ReactLoopResult(finalResponse, allToolResponses, iter + 1);
            }
        }

        log.warn("[ReAct] Max iterations ({}) reached in loop", maxIterations);
        return null;
    }

    // ==================== 工具执行 ====================

    /**
     * 手动将流式 chunks 聚合为一个完整的 ChatClientResponse
     * <p>
     * AIDEV-NOTE: 不能使用 ChatClientMessageAggregator.aggregateChatClientResponse()，
     * 因为它是 pass-through 操作（doOnNext 发射原始项，doOnComplete 才调用 Consumer 副作用），
     * blockFirst() 只会拿到第一个原始 chunk，而非聚合结果。
     * 这里手动合并所有 chunk 的文本、元数据和工具调用信息。
     */
    private ChatClientResponse aggregateChunks(List<ChatClientResponse> chunks) {
        StringBuilder textBuilder = new StringBuilder();
        List<AssistantMessage.ToolCall> toolCalls = null;
        Map<String, Object> metadata = new HashMap<>();
        int chunkWithToolCalls = 0;

        for (int i = 0; i < chunks.size(); i++) {
            ChatClientResponse chunk = chunks.get(i);
            ChatResponse cr = chunk.chatResponse();
            if (cr == null || cr.getResult() == null) continue;
            AssistantMessage output = cr.getResult().getOutput();

            // 拼接文本片段
            String text = output.getText();
            if (text != null) textBuilder.append(text);

            // 工具调用通常在最后一个 chunk 中出现，取最后一个非空值
            var chunkToolCalls = output.getToolCalls();
            if (chunkToolCalls != null && !chunkToolCalls.isEmpty()) {
                chunkWithToolCalls++;
                toolCalls = chunkToolCalls;
                log.debug("[ReAct] Chunk[{}/{}] has {} toolCall(s): {}",
                        i, chunks.size(), chunkToolCalls.size(),
                        chunkToolCalls.stream().map(tc -> tc.name() + "(" + tc.id() + ")").toList());
            }

            // 合并元数据（reasoningContent 等）
            if (output.getMetadata() != null) {
                metadata.putAll(output.getMetadata());
            }
        }

        if (chunkWithToolCalls > 0) {
            log.info("[ReAct] Found toolCalls in {}/{} chunks, tools={}",
                    chunkWithToolCalls, chunks.size(),
                    toolCalls.stream().map(tc -> tc.name()).toList());
        } else {
            // 没有检测到工具调用，输出首尾 chunk 信息用于排查
            log.info("[ReAct] No toolCalls in {} chunks. First chunk text='{}', Last chunk text='{}'",
                    chunks.size(),
                    getChunkDebugText(chunks, 0),
                    getChunkDebugText(chunks, chunks.size() - 1));
        }

        AssistantMessage aggregatedMsg = AssistantMessage.builder()
                .content(textBuilder.toString())
                .properties(metadata)
                .toolCalls(toolCalls != null ? toolCalls : List.of())
                .build();

        ChatResponse aggregatedResponse = new ChatResponse(List.of(new Generation(aggregatedMsg)));
        return ChatClientResponse.builder()
                .chatResponse(aggregatedResponse)
                .context(chunks.get(0).context())
                .build();
    }

    /**
     * 获取 chunk 的调试文本摘要
     */
    private String getChunkDebugText(List<ChatClientResponse> chunks, int index) {
        if (index < 0 || index >= chunks.size()) return "N/A";
        var cr = chunks.get(index).chatResponse();
        if (cr == null || cr.getResult() == null) return "null";
        var output = cr.getResult().getOutput();
        String text = output.getText();
        var tc = output.getToolCalls();
        String toolInfo = (tc != null && !tc.isEmpty()) ? "toolCalls=" + tc.size() : "noTools";
        String preview = text != null ? (text.length() > 80 ? text.substring(0, 80) + "..." : text) : "null";
        return "[" + toolInfo + "] " + preview;
    }

    /**
     * 构建包含工具执行结果的合成 ChatClientResponse
     * <p>
     * AIDEV-NOTE: 将工具执行结果包装为 AssistantMessage 并设置 "toolResult" 元数据标记，
     * 使其能够流经 LlmRspCell::of 并被正确识别为工具执行结果。
     */
    private ChatClientResponse buildToolResultResponse(List<ToolResponseMessage> toolResponses,
                                                        ChatClientRequest request) {
        var sb = new StringBuilder();
        for (var tr : toolResponses) {
            for (var resp : tr.getResponses()) {
                if (sb.length() > 0) sb.append("\n---\n");
                sb.append("[").append(resp.name()).append("]: ");
                String data = resp.responseData();
                sb.append(data != null && data.length() > 500 ? data.substring(0, 500) + "..." : data);
            }
        }

        AssistantMessage msg = AssistantMessage.builder()
                .content(sb.toString())
                .properties(Map.of("toolResult", true))
                .build();
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(msg)));
        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(request.context())
                .build();
    }

    /**
     * 手动执行工具调用：通过 ToolCallback.call(args, toolContext) 逐个执行
     * <p>
     * AIDEV-NOTE: 参照 DefaultToolCallingManager.executeToolCall() 的实现，
     * 始终从 ToolCallingChatOptions 构建 ToolContext 并传入工具调用，
     * 确保工具可以访问请求上下文、用户信息等关键数据。
     */
    private ToolResponseMessage executeTools(List<AssistantMessage.ToolCall> toolCalls,
                                              ChatClientRequest request,
                                              int iter) {
        notifyBeforeActing(toolCalls, iter);
        log.info("[ReAct] Iteration {}: executing {} tool(s)", iter, toolCalls.size());

        try {
            ToolCallback[] callbacks = resolveToolCallbacks(request);
            // 参照 DefaultToolCallingManager.buildToolContext()，从 ChatOptions 构建 ToolContext
            ToolContext toolContext = buildToolContext(request.prompt());
            List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();

            for (var toolCall : toolCalls) {
                String toolName = toolCall.name();
                String toolArgs = toolCall.arguments();
                String toolCallId = toolCall.id();

                log.info("[ReAct] Calling tool '{}' (id={})", toolName, toolCallId);

                ToolCallback callback = findCallback(toolName, callbacks);
                if (callback == null) {
                    String errorMsg = String.format("Tool '%s' not found", toolName);
                    log.warn("[ReAct] {}", errorMsg);
                    responses.add(new ToolResponseMessage.ToolResponse(toolCallId, toolName, errorMsg));
                    continue;
                }

                try {
                    // 始终传入 ToolContext，与 DefaultToolCallingManager 保持一致
                    String result = callback.call(toolArgs, toolContext);
                    log.info("[ReAct] Tool '{}' returned {} chars", toolName, result != null ? result.length() : 0);
                    responses.add(new ToolResponseMessage.ToolResponse(toolCallId, toolName,
                            result != null ? result : "(empty)"));
                } catch (Exception e) {
                    log.error("[ReAct] Tool '{}' execution failed", toolName, e);
                    responses.add(new ToolResponseMessage.ToolResponse(toolCallId, toolName,
                            "Tool execution failed: " + e.getMessage()));
                }
            }

            ToolResponseMessage toolResponse = ToolResponseMessage.builder()
                    .responses(responses)
                    .build();

            notifyAfterActing(toolResponse, iter);
            return toolResponse;

        } catch (Exception e) {
            log.error("[ReAct] Tool execution failed at iteration {}", iter, e);
            notifyError(e, iter);
            return null;
        }
    }

    /**
     * 根据工具名称查找对应的 ToolCallback
     */
    private ToolCallback findCallback(String toolName, ToolCallback[] callbacks) {
        for (ToolCallback cb : callbacks) {
            if (cb.getToolDefinition() != null && toolName.equals(cb.getToolDefinition().name())) {
                return cb;
            }
        }
        return null;
    }

    /**
     * 构建 ToolContext，参照 DefaultToolCallingManager.buildToolContext() 实现。
     * <p>
     * 从 Prompt 的 ChatOptions 中提取 ToolCallingChatOptions.getToolContext()，
     * 将其封装为 ToolContext 对象传入工具调用，确保工具可以访问上下文数据。
     *
     * @param prompt 当前请求的 Prompt
     * @return ToolContext 实例（即使为空 Map 也会返回有效的 ToolContext）
     */
    private ToolContext buildToolContext(Prompt prompt) {
        Map<String, Object> toolContextMap = Map.of();
        ChatOptions options = prompt.getOptions();
        if (options instanceof ToolCallingChatOptions toolCallingChatOptions) {
            if (!CollectionUtils.isEmpty(toolCallingChatOptions.getToolContext())) {
                toolContextMap = new HashMap<>(toolCallingChatOptions.getToolContext());
            }
        }
        return new ToolContext(toolContextMap);
    }

    /**
     * 从 request 的 ChatOptions 中解析已注册的工具回调
     */
    private ToolCallback[] resolveToolCallbacks(ChatClientRequest request) {
        ChatOptions options = request.prompt().getOptions();
        if (options instanceof ToolCallingChatOptions tcOptions && tcOptions.getToolCallbacks() != null) {
            List<ToolCallback> list = tcOptions.getToolCallbacks();
            return list.toArray(new ToolCallback[0]);
        }
        return new ToolCallback[0];
    }

    // ==================== 直接模型调用 ====================

    /**
     * 直接调用 ChatModel（不经过 Advisor Chain）
     * 用于 ReAct 循环中的后续推理，避免重复触发 Memory 等 Advisor
     */
    private ChatResponse callModelDirect(List<Message> messages, ChatClientRequest request) {
        try {
            ChatOptions options = request.prompt().getOptions();
            // 确保禁用自动工具执行
            ToolCallback[] toolArray = resolveToolCallbacks(request);
            ChatOptions directOptions = ToolCallingChatOptions.builder()
                    .internalToolExecutionEnabled(false)
                    .toolCallbacks(toolArray)
                    .build();

            Prompt prompt = new Prompt(messages, directOptions);
            LlmMonitor monitor = SpringUtil.getBeanOrNull(LlmMonitor.class);
            return monitor == null ? chatModel.call(prompt) : monitor.directCall(prompt, () -> chatModel.call(prompt));
        } catch (Exception e) {
            log.error("[ReAct] Direct model call failed", e);
            notifyError(e, -1);
            return null;
        }
    }

    // ==================== Middleware 通知 ====================

    private void notifySetContext(ChatClientRequest request) {
        for (var mw : middlewares) {
            try { mw.setContext(request); } catch (Exception e) { log.warn("Middleware setContext error", e); }
        }
    }

    private void notifyBeforeReasoning(List<Message> messages, int iter) {
        for (var mw : middlewares) {
            try { mw.beforeReasoning(messages, iter); } catch (Exception e) { log.warn("Middleware error", e); }
        }
    }

    private void notifyAfterReasoning(AssistantMessage msg, int iter) {
        for (var mw : middlewares) {
            try { mw.afterReasoning(msg, iter); } catch (Exception e) { log.warn("Middleware error", e); }
        }
    }

    private void notifyBeforeActing(List<AssistantMessage.ToolCall> toolCalls, int iter) {
        for (var mw : middlewares) {
            try { mw.beforeActing(toolCalls, iter); } catch (Exception e) { log.warn("Middleware error", e); }
        }
    }

    private void notifyAfterActing(ToolResponseMessage msg, int iter) {
        for (var mw : middlewares) {
            try { mw.afterActing(msg, iter); } catch (Exception e) { log.warn("Middleware error", e); }
        }
    }

    private void notifyComplete(int totalIters, String finalResponse) {
        for (var mw : middlewares) {
            try { mw.onComplete(totalIters, finalResponse); } catch (Exception e) { log.warn("Middleware error", e); }
        }
    }

    private void notifyError(Exception error, int iter) {
        for (var mw : middlewares) {
            try { mw.onError(error, iter); } catch (Exception e) { log.warn("Middleware error", e); }
        }
    }

    // ==================== Advisor 元数据 ====================

    @Override
    public String getName() {
        return "ReActAdvisor";
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ChatModel chatModel;
        private List<ReActMiddleware> middlewares = new ArrayList<>();
        private int maxIterations = 10;
        private int order = 100; // 高于 ToolCallAdvisor 的默认优先级

        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public Builder addMiddleware(ReActMiddleware middleware) {
            this.middlewares.add(middleware);
            return this;
        }

        public Builder middlewares(List<ReActMiddleware> middlewares) {
            this.middlewares = new ArrayList<>(middlewares);
            return this;
        }

        public Builder maxIterations(int maxIterations) {
            if (maxIterations <= 0) {
                throw new IllegalArgumentException("maxIterations must be positive, got: " + maxIterations);
            }
            this.maxIterations = maxIterations;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public ReActAdvisor build() {
            if (chatModel == null) {
                throw new IllegalStateException("chatModel is required");
            }
            return new ReActAdvisor(this);
        }

        public Builder autoInjectMiddleware() {
            try {
                var middlewareBeans = SpringUtil.getContext().getBeansOfType(ReActMiddleware.class);
                middlewareBeans.values().forEach(this::addMiddleware);
            } catch (Exception e) {
                log.debug("No ReActMiddleware beans found", e);
            }
            return this;
        }
    }
}
