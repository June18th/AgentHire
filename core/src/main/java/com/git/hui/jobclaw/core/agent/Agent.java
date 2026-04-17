package com.git.hui.jobclaw.core.agent;

import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.utils.MD5Utils;
import reactor.core.publisher.Flux;

public interface Agent {

    /**
     * 简单的纯文本交互(向后兼容)
     */
    String respondTo(UserConversationInfo conversationInfo, String question);

    /**
     * Agent的结构化结果
     */
    <T> T prompt(UserConversationInfo conversationInfo, String input, Class<T> result);

    /**
     * 流式响应Agent的回复。
     * 返回可被响应式消费的文本块Flux流。
     *
     * @param conversationInfo 会话标识符,用于记忆上下文
     * @param msg 用户的问题或消息
     * @return 发出LLM生成的文本块的Flux流
     */
    Flux<LlmRspCell> streamResponse(UserConversationInfo conversationInfo, ChannelReceiveMessage msg);

    /**
     * 支持文本、图片、文件等的多模态交互。
     * 这是现代多模态LLM的首选方法。
     *
     * @param conversationInfo 会话标识符,用于记忆上下文
     * @param message 包含文本和媒体的多模态消息
     * @return Agent的文本回复
     */
    String respondToMultiModal(UserConversationInfo conversationInfo, ChannelReceiveMessage message);


    record UserConversationInfo(String jobClawUserId, String channel, String conversationId) {
        public static UserConversationInfo parse(String conversationId) {
            // 原始的 conversationId 是按照 jobClawUserId:channel:conversationId 的格式进行组装的，所以我们首先进行解析，将会话的JobClawUserId依然保存，用于用户会话的隔离

            String[] parts = conversationId.split(":", 3);
            return new UserConversationInfo(parts[0], parts[1], parts[2]);
        }

        public static String generateConversationId(String jobClawUserId, String channel, String conversationId) {
            // 由于用户传入的 conversationId 可能存在各种格式，为了统一，我们使用md5进行修剪
            return jobClawUserId + ":" + channel + ":" + MD5Utils.md5(conversationId);
        }

        public String genId() {
            return generateConversationId(jobClawUserId, channel, conversationId);
        }
    }
}
