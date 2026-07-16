package com.git.hui.jobclaw.core.agent.react;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.cache.LocalCacheManager;
import com.git.hui.jobclaw.core.monitor.ToolCallRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * ReAct 工具调用审计中间件，结构化发布 ToolCallRecord 事件供落库。
 * AI-GENERATED
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "agent.tools.audit.enabled", havingValue = "true", matchIfMissing = true)
public class ToolCallAuditMiddleware implements ReActMiddleware {

    private static final String CACHE_NAME = "toolCallAuditState";
    private static final int MAX_SUMMARY_LEN = 2000;
    private static final Pattern AUTH_VALUE = Pattern.compile(
            "(?i)(\\b(?:authorization|proxy-authorization)\\s*[:=]\\s*)(?:Bearer|Basic)\\s+[^\\s,;}&]+"
    );
    private static final Pattern COOKIE_HEADER = Pattern.compile(
            "(?i)(\\b(?:cookie|set-cookie)\\s*:\\s*)[^\\r\\n]+"
    );
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(?i)([\"']?(?:x[-_]?api[-_]?key|api[-_]?key|password|passwd|secret|"
                    + "(?:access|refresh)[-_]?token|token|authorization|cookie|set-cookie)[\"']?\\s*[:=]\\s*)"
                    + "(?:\"[^\"]*\"|'[^']*'|[^\\s,;&}]+)"
    );

    private final ApplicationEventPublisher publisher;
    private final LocalCacheManager cacheManager;

    public ToolCallAuditMiddleware(ApplicationEventPublisher publisher, LocalCacheManager cacheManager) {
        this.publisher = publisher;
        this.cacheManager = cacheManager;
        cacheManager.getCache(CACHE_NAME, Duration.ofMinutes(30), 5000);
    }

    @Override
    public void setContext(ChatClientRequest request, String chatId) {
        UserConversationInfo user = extractUser(request);
        AuditState state = new AuditState();
        state.user = user;
        state.invocationId = chatId;
        cacheManager.put(CACHE_NAME, chatId, state);
    }

    @Override
    public void beforeActing(List<AssistantMessage.ToolCall> toolCalls, int iter, String chatId) {
        AuditState state = cacheManager.get(CACHE_NAME, chatId);
        if (state == null || toolCalls == null) {
            return;
        }
        state.iteration = iter;
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            state.pendingCalls.put(toolCall.id(), new PendingCall(
                    toolCall.id(),
                    toolCall.name(),
                    toolCall.arguments(),
                    System.nanoTime()
            ));
        }
    }

    @Override
    public void afterActing(ToolResponseMessage toolResponses, int iter, String chatId) {
        AuditState state = cacheManager.get(CACHE_NAME, chatId);
        if (state == null || toolResponses == null || toolResponses.getResponses() == null) {
            return;
        }
        for (ToolResponseMessage.ToolResponse response : toolResponses.getResponses()) {
            PendingCall pending = state.pendingCalls.remove(response.id());
            long durationMs = pending == null ? 0L : (System.nanoTime() - pending.startNanos) / 1_000_000;
            String result = response.responseData();
            String status = resolveStatus(result);
            String errorMessage = "FAILED".equals(status) ? sample(result, MAX_SUMMARY_LEN) : null;
            publishRecord(state, pending, response.name(), response.id(), iter, durationMs, status, result, errorMessage);
        }
    }

    @Override
    public void onComplete(int totalIters, String finalResponse, String chatId) {
        cacheManager.remove(CACHE_NAME, chatId);
    }

    @Override
    public void onError(Exception error, int iter, String chatId) {
        AuditState state = cacheManager.get(CACHE_NAME, chatId);
        if (state != null) {
            for (PendingCall pending : state.pendingCalls.values()) {
                long durationMs = (System.nanoTime() - pending.startNanos) / 1_000_000;
                publishRecord(state, pending, pending.toolName(), pending.toolCallId(), iter, durationMs,
                        "FAILED", null, error.getMessage());
            }
            state.pendingCalls.clear();
        }
        cacheManager.remove(CACHE_NAME, chatId);
    }

    private void publishRecord(AuditState state,
                               PendingCall pending,
                               String toolName,
                               String toolCallId,
                               int iter,
                               long durationMs,
                               String status,
                               String result,
                               String errorMessage) {
        UserConversationInfo user = state.user;
        String agent = user != null && user.agent() != null ? user.agent() : "unknown";
        String userId = user != null ? user.jobClawUserId() : "unknown";
        String conversationId = user != null ? user.genId() : null;
        String channel = user != null ? user.channel() : null;
        String args = pending == null ? null : sample(pending.args(), MAX_SUMMARY_LEN);

        ToolCallRecord record = new ToolCallRecord(
                UUID.randomUUID().toString(),
                state.invocationId,
                userId,
                conversationId,
                channel,
                agent,
                toolName,
                toolCallId,
                iter,
                args,
                sample(result, MAX_SUMMARY_LEN),
                status,
                durationMs,
                sample(errorMessage, MAX_SUMMARY_LEN),
                Instant.now()
        );
        try {
            publisher.publishEvent(record);
        } catch (Exception e) {
            log.warn("发布工具调用审计事件失败: tool={}, user={}", toolName, userId, e);
        }
    }

    private static String resolveStatus(String result) {
        if (result == null) {
            return "SUCCESS";
        }
        String lower = result.toLowerCase();
        if (lower.startsWith("tool execution failed")
                || lower.startsWith("error:")
                || lower.startsWith("error ")
                || lower.contains("not found")) {
            return "FAILED";
        }
        return "SUCCESS";
    }

    private static String sample(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        String cleaned = COOKIE_HEADER.matcher(text).replaceAll("$1***");
        cleaned = AUTH_VALUE.matcher(cleaned).replaceAll("$1***");
        cleaned = SENSITIVE_VALUE.matcher(cleaned).replaceAll("$1***");
        cleaned = cleaned.replaceAll("(?i)Bearer\\s+\\S+", "Bearer ***")
                .replaceAll("[\\w.+-]+@[\\w.-]+", "***@***")
                .replaceAll("1[3-9]\\d{9}", "***");
        return cleaned.length() <= maxLen ? cleaned : cleaned.substring(0, maxLen) + "...";
    }

    private UserConversationInfo extractUser(ChatClientRequest request) {
        ChatOptions options = request.prompt().getOptions();
        if (options instanceof ToolCallingChatOptions toolOptions) {
            Map<String, Object> ctx = toolOptions.getToolContext();
            if (ctx != null) {
                Object user = ctx.get("user");
                if (user instanceof UserConversationInfo info) {
                    return info;
                }
            }
        }
        return null;
    }

    private static final class AuditState {
        private UserConversationInfo user;
        private String invocationId;
        private int iteration;
        private final Map<String, PendingCall> pendingCalls = new ConcurrentHashMap<>();
    }

    private record PendingCall(String toolCallId, String toolName, String args, long startNanos) {
    }
}
