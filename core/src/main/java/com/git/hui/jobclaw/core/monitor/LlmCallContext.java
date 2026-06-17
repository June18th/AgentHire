package com.git.hui.jobclaw.core.monitor;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;

import java.util.UUID;

public record LlmCallContext(String invocationId, String jobClawUserId, String conversationId,
                             String channel, String agent, String operation, String mode) {

    public static LlmCallContext of(UserConversationInfo user, String operation, String mode) {
        return new LlmCallContext(UUID.randomUUID().toString(), user.jobClawUserId(), user.genId(),
                user.channel(), user.agent(), operation, mode);
    }
}
