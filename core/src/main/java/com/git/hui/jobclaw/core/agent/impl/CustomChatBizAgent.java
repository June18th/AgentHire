package com.git.hui.jobclaw.core.agent.impl;

import com.git.hui.jobclaw.core.agent.BizAgent;
import com.git.hui.jobclaw.core.agent.LlmCaller;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 通用的聊天Agent，主要用于用户未指定特定业务Agent时，与用户继续普通的聊天场景
 * @author YiHui
 * @date 2026/4/17
 */
@Component
public class CustomChatBizAgent implements BizAgent {

    private final LlmCaller llmCaller;

    public CustomChatBizAgent(LlmCaller llmCaller) {
        this.llmCaller = llmCaller;
    }


    @Override
    public AgentIntro getAgentIntro() {
        return PresetAgentIntro.CHAT;
    }

    @Override
    public List<AgentIntro> getSupportedIntents() {
        return List.of(PresetAgentIntro.CHAT);
    }

    @Override
    public String process(LlmCaller.UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        return llmCaller.respondToMultiModal(userConversationInfo, message);
    }

    @Override
    public Flux<LlmRspCell> stream(LlmCaller.UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        return llmCaller.streamResponse(userConversationInfo, message);
    }
}
