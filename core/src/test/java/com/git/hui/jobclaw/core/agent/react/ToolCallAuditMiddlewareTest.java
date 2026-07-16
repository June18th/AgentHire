package com.git.hui.jobclaw.core.agent.react;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.cache.LocalCacheManager;
import com.git.hui.jobclaw.core.monitor.ToolCallRecord;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolCallAuditMiddlewareTest {

    @Test
    void publishesSanitizedSuccessfulToolCall() {
        List<Object> events = new ArrayList<>();
        ToolCallAuditMiddleware middleware = middleware(events);
        setContext(middleware, "chat-1");

        middleware.beforeActing(List.of(new AssistantMessage.ToolCall(
                "call-1", "function", "searchJobs",
                "{\"email\":\"dev@example.com\",\"phone\":\"13812345678\","
                        + "\"apiKey\":\"key-123\",\"password\":\"pass-456\"}"
        )), 2, "chat-1");
        middleware.afterActing(response("call-1", "searchJobs", "Bearer secret-token result"), 2, "chat-1");

        assertThat(events).hasSize(1);
        ToolCallRecord record = (ToolCallRecord) events.getFirst();
        assertThat(record.invocationId()).isEqualTo("chat-1");
        assertThat(record.jobClawUserId()).isEqualTo("user-1");
        assertThat(record.channel()).isEqualTo("test");
        assertThat(record.agent()).isEqualTo("job-fetch");
        assertThat(record.toolName()).isEqualTo("searchJobs");
        assertThat(record.iteration()).isEqualTo(2);
        assertThat(record.status()).isEqualTo("SUCCESS");
        assertThat(record.argsSummary()).contains("***@***", "***").doesNotContain("dev@example.com", "13812345678");
        assertThat(record.argsSummary()).doesNotContain("key-123", "pass-456");
        assertThat(record.resultSummary()).isEqualTo("Bearer *** result");
    }

    @Test
    void publishesPendingCallsAsFailedAndClearsStateOnError() {
        List<Object> events = new ArrayList<>();
        ToolCallAuditMiddleware middleware = middleware(events);
        setContext(middleware, "chat-2");
        middleware.beforeActing(List.of(new AssistantMessage.ToolCall(
                "call-2", "function", "loadPage", "{}"
        )), 1, "chat-2");

        middleware.onError(new IllegalStateException("browser failed, token=secret-123"), 1, "chat-2");
        middleware.onError(new IllegalStateException("duplicate"), 1, "chat-2");

        assertThat(events).hasSize(1);
        ToolCallRecord record = (ToolCallRecord) events.getFirst();
        assertThat(record.status()).isEqualTo("FAILED");
        assertThat(record.errorMessage()).isEqualTo("browser failed, token=***");
        assertThat(record.errorMessage()).doesNotContain("secret-123");
        assertThat(record.toolName()).isEqualTo("loadPage");
    }

    private static ToolCallAuditMiddleware middleware(List<Object> events) {
        ApplicationEventPublisher publisher = events::add;
        return new ToolCallAuditMiddleware(publisher, new LocalCacheManager());
    }

    private static void setContext(ToolCallAuditMiddleware middleware, String chatId) {
        UserConversationInfo user = new UserConversationInfo("user-1", "test", "conversation-1", false)
                .setAgent("job-fetch");
        ToolCallingChatOptions options = mock(ToolCallingChatOptions.class);
        when(options.getToolContext()).thenReturn(Map.of("user", user));
        Prompt prompt = mock(Prompt.class);
        when(prompt.getOptions()).thenReturn(options);
        ChatClientRequest request = mock(ChatClientRequest.class);
        when(request.prompt()).thenReturn(prompt);
        middleware.setContext(request, chatId);
    }

    private static ToolResponseMessage response(String id, String name, String data) {
        ToolResponseMessage message = mock(ToolResponseMessage.class);
        when(message.getResponses()).thenReturn(List.of(new ToolResponseMessage.ToolResponse(id, name, data)));
        return message;
    }
}
