package com.git.hui.jobclaw.core.agent.models;

import com.git.hui.jobclaw.core.utils.MD5Utils;

public record UserConversationInfo(String jobClawUserId, String channel, String conversationId, boolean group) {
    public static UserConversationInfo parse(String conversationId) {
        // 原始的 conversationId 是按照 jobClawUserId:channel:conversationId:group 的格式进行组装的，所以我们首先进行解析，将会话的JobClawUserId依然保存，用于用户会话的隔离

        String[] parts = conversationId.split(":", 4);
        return new UserConversationInfo(parts[0], parts[1], parts[2], "1".equals(parts[3]));
    }

    public static String generateConversationId(String jobClawUserId, String channel, String conversationId, boolean group) {
        // 由于用户传入的 conversationId 可能存在各种格式，为了统一，我们使用md5进行修剪
        return jobClawUserId + ":" + channel + ":" + MD5Utils.md5(conversationId) + ":" + (group ? "1" : "0");
    }

    public String genId() {
        return generateConversationId(jobClawUserId, channel, conversationId, group);
    }
}