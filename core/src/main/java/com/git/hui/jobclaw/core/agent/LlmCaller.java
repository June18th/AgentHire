package com.git.hui.jobclaw.core.agent;

import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import reactor.core.publisher.Flux;

/**
 * 定义与大模型之间的交互，支持是用 SpringAI/SpringAI alibaba/LangChain4j/LangGraph4J 来实现具体的交互
 */
public interface LlmCaller {

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
}
