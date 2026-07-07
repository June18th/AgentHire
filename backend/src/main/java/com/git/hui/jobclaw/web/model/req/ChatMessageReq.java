package com.git.hui.jobclaw.web.model.req;

/**
 * Web chat message request.
 *
 * @author Codex
 */
public record ChatMessageReq(String message, String conversationId, String agentId) {
}
