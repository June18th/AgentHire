package com.git.hui.jobclaw.core.agent.llm;

import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 业务Agent LLM调用封装:（默认不会加载用户的偏好信息，如果某个业务Agent需要加载用户的 info.md/user.md 则推荐使用 UserPreferenceBasedLlmCaller）
 * - 使用 ChatMemory 进行统一的上下文管理，对话会持久化
 * @author YiHui
 * @date 2026/4/20
 */
@Slf4j
public class BizAgentLlmCaller extends SimpleLlmCaller {
    protected final ChatMemory chatMemory;
    protected final IIdentityAgent identityAgent;
    protected final Map<String, ChatClient> chatClientMap = new ConcurrentHashMap<>();

    protected final String systemPrompt;
    protected final ToolCallback[] tools;

    public BizAgentLlmCaller(ChatMemory chatMemory,
                             IIdentityAgent identityAgent,
                             ModelProviders modelProviders,
                             String systemPrompt, ToolCallback... tools) {
        super(modelProviders);
        this.identityAgent = identityAgent;
        this.chatMemory = chatMemory;
        this.systemPrompt = systemPrompt;
        this.tools = tools;
    }

    protected ChatClient getClient(UserConversationInfo user, Prompt prompt) {
        // 判断message中是否包含media
        ModelConfig.ModelType model = ModelConfig.ModelType.TEXT;
        if (prompt.getUserMessages().stream().anyMatch(m -> !CollectionUtils.isEmpty(m.getMedia()))) {
            model = ModelConfig.ModelType.VISION;
        }
        String key = user.jobClawUserId() + model.name();
        if (chatClientMap.containsKey(key)) {
            return chatClientMap.get(key);
        }


        var chatModel = (ChatModel) modelProviders.getModel(user.jobClawUserId(), model);

        var builder = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        SimpleLoggerAdvisor.builder().build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                );
        if (tools != null && tools.length > 0) {
            builder.defaultToolCallbacks(tools);
        }

        String identity = identityAgent.buildSoulPrompt(user.jobClawUserId());
        Consumer<ChatClient.PromptSystemSpec> sys;
        if (StringUtils.isBlank(identity)) {
            sys = buildSystemPrompt(systemPrompt);
        } else {
            sys = buildSystemPrompt(identity + "\n\n" + systemPrompt);
        }

        if (sys != null) {
            builder.defaultSystem(sys);
        }
        var client = builder.build();
        log.info("[{}] init BizAgentLlmCaller by register {} tools with systemPrompt len: {} for {}",
                user.agent(),
                tools == null ? 0 : tools.length,
                systemPrompt == null ? 0 : systemPrompt.length(),
                user.jobClawUserId());
        chatClientMap.put(key, client);
        return client;
    }


    public String call(UserConversationInfo user, Prompt prompt, ChannelReceiveMessage msg) {
        ChatClient client = getClient(user, prompt);

        return client.prompt(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user.genId()))
                .toolContext(Map.of("jobClawUserId", user.jobClawUserId(),
                        "user", user,
                        "msg", msg))
                .call()
                .content();
    }

    @Override
    public String call(UserConversationInfo user, Prompt prompt) {
        ChatClient client = getClient(user, prompt);

        return client.prompt(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user.genId()))
                .toolContext(Map.of("user", user))
                .call()
                .content();
    }

    public <T> Flux<T> stream(UserConversationInfo user, Prompt prompt, ChannelReceiveMessage msg, Function<ChatResponse, T> func) {
        ChatClient client = getClient(user, prompt);

        return client.prompt(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user.genId()))
                .toolContext(Map.of(
                        "user", user,
                        "msg", msg
                ))
                .stream()
                .chatResponse().map(func);

    }

    @Override
    public <T> Flux<T> stream(UserConversationInfo user, Prompt prompt, Function<ChatResponse, T> func) {
        ChatClient client = getClient(user, prompt);

        return client.prompt(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user.genId()))
                .toolContext(Map.of("user", user))
                .stream()
                .chatResponse().map(func);

    }

    public UserConversationInfo getUser(ToolContext toolContext) {
        return (UserConversationInfo) toolContext.getContext().get("user");
    }

    public ChannelReceiveMessage getMsg(ToolContext toolContext) {
        return (ChannelReceiveMessage) toolContext.getContext().get("msg");
    }

    public void refreshCache() {
        chatClientMap.clear();
    }
}
