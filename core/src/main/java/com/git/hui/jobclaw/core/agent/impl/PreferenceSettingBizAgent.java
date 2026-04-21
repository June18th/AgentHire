package com.git.hui.jobclaw.core.agent.impl;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.git.hui.jobclaw.core.agent.LlmCaller;
import com.git.hui.jobclaw.core.agent.llm.ClientSelector;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.apis.permission.AgentPermission;
import com.git.hui.jobclaw.core.channel.ChannelBinder;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 个人偏好设置的业务Agent
 * - 支持修改默认的模型
 * - 支持添加不同渠道的机器人
 *
 * @author YiHui
 * @date 2026/4/18
 */
@Component
public class PreferenceSettingBizAgent extends AbsBizAgent {

    private final List<ChannelBinder> channelBinders;
    private final ConfigurationManager configurationManager;
    private final AiUserPreferenceProperties aiUserPreferenceProperties;


    public PreferenceSettingBizAgent(ClientSelector clientSelector,
                                     List<ChannelBinder> channelBinders,
                                     ConfigurationManager configurationManager,
                                     AiUserPreferenceProperties aiUserPreferenceProperties,
                                     ChatMemory chatMemory) {
        super(clientSelector, chatMemory);
        this.channelBinders = channelBinders;
        this.configurationManager = configurationManager;
        this.aiUserPreferenceProperties = aiUserPreferenceProperties;
    }

    @Override
    public AgentPermission permission() {
        return AgentPermission.TOTAL;
    }
    @Override
    public AgentIntro getAgentIntro() {
        return PresetAgentIntro.PREFERENCE_SETTING;
    }

    @Override
    public List<AgentIntro> getSupportedIntents() {
        return List.of(PresetAgentIntro.PREFERENCE_SETTING, PresetAgentIntro.DEFAULT);
    }

    @Override
    public String getSystemPrompt() {
        return """
                你专门为用户的设置服务，你可以通过提供的工具来查询、修改、新增用户的偏好配置
                请根据用户的意图，选择合适的工具来执行具体的设置相关请求
                """;
    }

    @Override
    public String process(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        ChatClient client = getChatClient(userConversationInfo.jobClawUserId());
        return client.prompt(message.getMessage())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userConversationInfo.genId()))
                .toolContext(Map.of("jobClawUserId", userConversationInfo.jobClawUserId()))
                .call()
                .content();
    }

    @Override
    public Flux<LlmRspCell> stream(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        ChatClient client = getChatClient(userConversationInfo.jobClawUserId());
        return client.prompt(message.getMessage())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userConversationInfo.genId()))
                .toolContext(Map.of("jobClawUserId", userConversationInfo.jobClawUserId()))
                .stream()
                .chatResponse()
                .map(LlmRspCell::of);
    }


    @Tool(description = "修改用户的默认模型")
    public void updateActiveModel(
            @JsonPropertyDescription("模型提供方，如 zhipu, silicon, ali, openai, doubao")
            String provider,
            @JsonPropertyDescription("模型名称，如 GLM-5")
            String model,
            @JsonPropertyDescription("模型类型, 如 TXT, VISION, IMAGE, VIDEO, EMBEDDING, ASR, TTS")
            ModelConfig.ModelType type,
            ToolContext toolContext) {
        String jobClawUserId = (String) toolContext.getContext().get("jobClawUserId");
        int index = -1;
        for (int i = 0; i < aiUserPreferenceProperties.getPreference().size(); i++) {
            if (aiUserPreferenceProperties.getPreference().get(i).getUserId().equals(jobClawUserId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            // 不应该存在找不到的用户，每个用户在创建账号的时候，就需要初始化用户维度的偏好设置信息
            throw new IllegalArgumentException("你还没有偏好配置哦，请先到后台进行初始化吧~");
        }
        String prefix = "agent.ai.preference[" + index + "]";
        Map<String, Object> map = new HashMap<>();
        map.put(prefix + ".models." + type.name().toLowerCase(), provider + "#" + model);
        configurationManager.updateProperties(map);
    }

    @Tool(description = "添加大模型访问的APIKEY")
    public void addModelApiKey(
            @JsonPropertyDescription("模型提供方，如 zhipu, silicon, ali, openai, doubao")
            String provider,
            @JsonPropertyDescription("模型APIKEY")
            String apiKey,
            ToolContext toolContext) {
        String jobClawUserId = (String) toolContext.getContext().get("jobClawUserId");
        int index = -1;
        for (int i = 0; i < aiUserPreferenceProperties.getPreference().size(); i++) {
            if (aiUserPreferenceProperties.getPreference().get(i).getUserId().equals(jobClawUserId)) {
                index = i;
                break;
            }
        }

        String prefix = "agent.ai.preference[" + index + "]";
        Map<String, Object> map = new HashMap<>();
        map.put(prefix + ".providers." + provider + ".api-key", apiKey);
        configurationManager.updateProperties(map);
    }

    @Tool(description = "查询显示用户的当前个人偏好设置")
    public AiUserPreferenceProperties.UserPreferenceEntry showMyPreference(ToolContext toolContext) {
        String jobClawUserId = (String) toolContext.getContext().get("jobClawUserId");
        for (var item : aiUserPreferenceProperties.getPreference()) {
            if (item.getUserId().equals(jobClawUserId)) {
                // 显示用户的偏好设置，但是需要对 ModelApiKey 进行脱敏处理
                return securityReturn(item);
            }
        }

        return null;
    }

    private AiUserPreferenceProperties.UserPreferenceEntry securityReturn(AiUserPreferenceProperties.UserPreferenceEntry entry) {
        AiUserPreferenceProperties.UserPreferenceEntry ret = new AiUserPreferenceProperties.UserPreferenceEntry();
        ret.setUserId(entry.getUserId());
        ret.setCollector(entry.getCollector());
        ret.setChannels(entry.getChannels());

        AiUserPreferenceProperties.UserModelPreference models = new AiUserPreferenceProperties.UserModelPreference();
        models.setVision(entry.getModels().getVision());
        models.setText(entry.getModels().getText());
        Map<String, AiUserPreferenceProperties.UserProviderConfig> providers = new HashMap<>();
        for (var kv : entry.getModels().getProviders().entrySet()) {
            var config = kv.getValue();

            var userProviderConfig = new AiUserPreferenceProperties.UserProviderConfig();
            userProviderConfig.setApiKey(securityReturn(config.getApiKey()));
            if (!CollectionUtils.isEmpty(config.getModels())) {
                var modelKeys = config.getModels().stream().map(model -> {
                    Map<String, String> sub = new HashMap<>();
                    model.forEach((mk, mv) -> {
                        sub.put(mk, securityReturn(mv));
                    });
                    return sub;
                }).toList();
                userProviderConfig.setModels(modelKeys);
            } else {
                userProviderConfig.setModels(List.of());
            }
            providers.put(kv.getKey(), userProviderConfig);
        }
        models.setProviders(providers);
        ret.setModels(models);
        return ret;
    }

    private String securityReturn(String key) {
        // 脱敏处理
        if (key.length() < 3) {
            return "***";
        }
        if (key.length() < 5) {
            return key.charAt(0) + "***" + key.substring(key.length() - 1);
        } else {
            return key.substring(0, 2) + "***" + key.substring(key.length() - 2);
        }
    }

}
