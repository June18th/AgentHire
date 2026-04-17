package com.git.hui.jobclaw.core.agent.impl;

import com.git.hui.jobclaw.core.agent.BizAgent;
import com.git.hui.jobclaw.core.agent.LlmCaller;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 默认业务Agent
 *
 * AIDEV-NOTE: 作为兜底Agent，处理无法识别的意图
 * 实际应该由LLM调用来处理通用对话
 *
 * @author YiHui
 * @date 2026/4/17
 */
@Slf4j
@Component
public class SimpleDefaultBizAgent implements BizAgent {

    private final LlmCaller llmCaller;

    public SimpleDefaultBizAgent(LlmCaller llmCaller) {
        this.llmCaller = llmCaller;
    }

    @Override
    public AgentIntro getAgentIntro() {
        return PresetAgentIntro.DEFAULT;
    }

    @Override
    public List<AgentIntro> getSupportedIntents() {
        // 支持所有意图类型
        return List.of();
    }

    @Override
    public String process(LlmCaller.UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        String userMessage = message.getMessage();

        // AIDEV-NOTE: 这里实际应该调用LLM来处理
        // 简化实现：返回一个提示
        return switch (userMessage.toLowerCase()) {
            case "help", "/help" -> """
                    您好！我是求职派助手，请问有什么可以帮助您的？
                                    
                    可用命令：
                    /agents - 返回可用的agent列表
                    /agent <名称> - 切换到指定Agent
                    /reset - 重置会话状态
                    /help - 显示帮助
                    """;
            default -> llmCaller.respondTo(userConversationInfo, userMessage);
        };
    }

    @Override
    public Flux<LlmRspCell> stream(LlmCaller.UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        return llmCaller.streamResponse(userConversationInfo, message);
    }

    @Override
    public int getPriority() {
        return Integer.MIN_VALUE; // 最低优先级
    }
}