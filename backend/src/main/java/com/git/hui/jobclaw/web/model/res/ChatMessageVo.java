package com.git.hui.jobclaw.web.model.res;

/**
 * Web chat message response.
 *
 * @author Codex
 */
public record ChatMessageVo(String conversationId, String fullConversationId, String agentId, String content) {
}
