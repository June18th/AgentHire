package com.git.hui.jobclaw.web.model.res;

import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import org.springframework.util.StringUtils;

/**
 * Streaming payload for the web chat SSE endpoint.
 *
 * @author Codex
 */
public record ChatStreamVo(
        String type,
        String conversationId,
        String fullConversationId,
        String agentId,
        String thinking,
        String content,
        String tool,
        String toolResult,
        String error) {

    public static ChatStreamVo ready(String conversationId, String fullConversationId, String agentId) {
        return new ChatStreamVo("ready", conversationId, fullConversationId, agentId, null, null, null, null, null);
    }

    public static ChatStreamVo chunk(String conversationId, String agentId, LlmRspCell cell) {
        return new ChatStreamVo(
                "chunk",
                conversationId,
                null,
                agentId,
                cell.thinking(),
                cell.content(),
                cell.tool(),
                cell.toolResult(),
                null);
    }

    public static ChatStreamVo done(String conversationId, String fullConversationId, String agentId) {
        return new ChatStreamVo("done", conversationId, fullConversationId, agentId, null, null, null, null, null);
    }

    public static ChatStreamVo error(String conversationId, String agentId, String error) {
        return new ChatStreamVo("error", conversationId, null, agentId, null, null, null, null, error);
    }

    public boolean hasPayload() {
        return StringUtils.hasText(thinking)
                || StringUtils.hasText(content)
                || StringUtils.hasText(tool)
                || StringUtils.hasText(toolResult)
                || StringUtils.hasText(error);
    }
}
