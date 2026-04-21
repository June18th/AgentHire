package com.git.hui.jobclaw.core.agent.llm;

import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.LlmCaller;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Default agent implementation with identity document injection.
 *
 * AIDEV-NOTE: Modified in Phase 4 to inject agent.md/soul.md/info.md/user.md into System Prompt
 *
 * @author YiHui
 * @date 2026/4/9
 */
@Slf4j
public class SpringAiBasedLlmCaller implements LlmCaller {

    private final ClientSelector clientSelector;

    private final IIdentityAgent identityAgent;

    public SpringAiBasedLlmCaller(ClientSelector clientSelector,
                                  IIdentityAgent identityAgent) {
        this.clientSelector = clientSelector;
        this.identityAgent = identityAgent;
    }


    @Override
    public String respondTo(UserConversationInfo conversationInfo, String question) {
        String jobClawUserId = conversationInfo.jobClawUserId();
        return clientSelector.getClient(jobClawUserId, conversationInfo.channel(), false)
                .prompt(clientSelector.buildSoulPrompt(jobClawUserId, question))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationInfo.genId()))
                .toolContext(Map.of("jobClawUserId", jobClawUserId))
                .call()
                .content();
    }

    @Override
    public <T> T prompt(UserConversationInfo conversationInfo, String input, Class<T> result) {
        String jobClawUserId = conversationInfo.jobClawUserId();
        return clientSelector.getClient(conversationInfo.jobClawUserId(), conversationInfo.channel(), false)
                .prompt(clientSelector.buildSoulPrompt(jobClawUserId, input))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationInfo.genId()))
                .toolContext(Map.of("jobClawUserId", jobClawUserId))
                .call()
                .entity(result);
    }

    @Override
    public Flux<LlmRspCell> streamResponse(UserConversationInfo conversationInfo, ChannelReceiveMessage message) {
        String jobClawUserId = conversationInfo.jobClawUserId();
        return clientSelector.getClient(jobClawUserId, conversationInfo.channel(), hasMedia(message))
                .prompt(clientSelector.buildSoulPrompt(jobClawUserId, message))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationInfo.genId()))
                .toolContext(Map.of("jobClawUserId", jobClawUserId))
                .stream()
                .chatResponse()
                .map(LlmRspCell::of);
    }

    @Override
    public String respondToMultiModal(UserConversationInfo conversationInfo, ChannelReceiveMessage message) {
        // Execute with conversation memory
        String jobClawUserId = conversationInfo.jobClawUserId();
        return clientSelector.getClient(jobClawUserId, conversationInfo.channel(), hasMedia(message))
                .prompt(clientSelector.buildSoulPrompt(jobClawUserId, message))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationInfo.genId()))
                .toolContext(Map.of("jobClawUserId", jobClawUserId))
                .call()
                .content();
    }


    private boolean hasMedia(ChannelReceiveMessage message) {
        return !CollectionUtils.isEmpty(message.getMedias()) || !CollectionUtils.isEmpty(message.getFiles());
    }
}
