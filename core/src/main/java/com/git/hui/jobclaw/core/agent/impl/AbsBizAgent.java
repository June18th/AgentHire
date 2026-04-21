package com.git.hui.jobclaw.core.agent.impl;

import com.git.hui.jobclaw.core.agent.BizAgent;
import com.git.hui.jobclaw.core.agent.llm.ClientSelector;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author YiHui
 * @date 2026/4/20
 */
public abstract class AbsBizAgent implements BizAgent {
    protected final ClientSelector clientSelector;
    private final ChatMemory chatMemory;
    protected final Map<String, ChatClient> chatClientMap = new ConcurrentHashMap<>();

    public AbsBizAgent(ClientSelector clientSelector, ChatMemory chatMemory) {
        this.clientSelector = clientSelector;
        this.chatMemory = chatMemory;
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
                .defaultToolCallbacks(getTools())
                .defaultSystem(getSystemPrompt())
                .defaultAdvisors(
                        SimpleLoggerAdvisor.builder().build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
        chatClientMap.put(jobClawUserId, client);
        return client;
    }

    public abstract String getSystemPrompt();

    public ToolCallback[] getTools() {
        return new ToolCallback[0];
    }
}
