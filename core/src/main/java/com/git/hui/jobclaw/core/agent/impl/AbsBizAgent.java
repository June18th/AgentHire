package com.git.hui.jobclaw.core.agent.impl;

import com.git.hui.jobclaw.core.agent.BizAgent;
import com.git.hui.jobclaw.core.agent.llm.ClientSelector;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author YiHui
 * @date 2026/4/20
 */
public abstract class AbsBizAgent implements BizAgent {
    protected final ClientSelector clientSelector;
    protected final Map<String, ChatClient> chatClientMap = new ConcurrentHashMap<>();

    public AbsBizAgent(ClientSelector clientSelector) {
        this.clientSelector = clientSelector;
    }

    protected ChatClient getChatClient(String jobClawUserId) {
        if (chatClientMap.containsKey(jobClawUserId)) {
            return chatClientMap.get(jobClawUserId);
        }
        return refreshChatClient(jobClawUserId);
    }

    protected ChatClient refreshChatClient(String jobClawUserId) {
        ChatModel model = (ChatModel) clientSelector.getUserPreferredModel(jobClawUserId, false);
        var client = ChatClient.builder(model)
                .defaultTools(this)
                .defaultSystem(getSystemPrompt())
                .build();
        chatClientMap.put(jobClawUserId, client);
        return client;
    }

    public abstract String getSystemPrompt();
}
