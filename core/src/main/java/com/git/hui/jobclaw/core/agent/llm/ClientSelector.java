package com.git.hui.jobclaw.core.agent.llm;

import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import com.git.hui.jobclaw.core.configuration.event.PropertiesRefreshedEvent;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.core.tasks.TaskManager;
import com.git.hui.jobclaw.core.tools.AutoDiscoveredTool;
import com.git.hui.jobclaw.core.tools.CheckListTool;
import com.git.hui.jobclaw.core.tools.McpTool;
import com.git.hui.jobclaw.core.tools.TaskTool;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springaicommunity.agent.utils.AgentEnvironment;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端选择器
 * @author YiHui
 * @date 2026/4/9
 */
@Slf4j
@Component
public class ClientSelector {
    private final ModelProviders modelProviders;

    private final ChatMemory chatMemory;

    private final TaskManager taskManager;

    /**
     * todo: 这里需要监听用户的模型偏好变更事件，然后需要重新初始化对应的Client
     *
     * 根据用户缓存的用户偏好配置，支持根据不同的用户，设置不同的启用模型
     */
    private Map<String, ChatClient> userCacheClient;

    @Value("${agent.workspace:Unknown}")
    private Resource workspace;

    private final List<AutoDiscoveredTool<?>> autoDiscoveredTools;

    public static final String AGENT_MD = "AGENT.private.md";

    public ClientSelector(ModelProviders modelProviders,
                          ChatMemory chatMemory,
                          TaskManager taskManager,
                          @Autowired(required = false) List<AutoDiscoveredTool<?>> autoDiscoveredTools) {
        this.modelProviders = modelProviders;
        this.chatMemory = chatMemory;
        this.taskManager = taskManager;
        this.userCacheClient = new ConcurrentHashMap<>();
        this.autoDiscoveredTools = autoDiscoveredTools != null ? autoDiscoveredTools : List.of();
    }


    @Async
    @EventListener
    public void registerUserPreferenceChangeCallback(PropertiesRefreshedEvent event) {
        // 这里用于注册用户偏好配置变更之后的回调逻辑，比如当用户重置userCacheClient缓存
        if (AiUserPreferenceProperties.class.equals(event.getPropertiesClz())) {
            userCacheClient.clear();
            log.info("[ClientSelector] User preference changed, clear user cache");
        }
    }


    /**
     * 根据用户 + 会话，获取对应的LLM客户端
     * @param userId JobClaw 的用户，用于获取模型偏好配置
     * @param channel 对话通道，针对用户对不同的聊天渠道，设置不同的模型偏好
     * @param multiModal 是否支持多模态
     * @return
     */
    public ChatClient getClient(String userId, String channel, boolean multiModal) {
        String key;
        if (StringUtils.isBlank(channel)) {
            key = multiModal ? userId + "_m" : userId;
        } else {
            key = multiModal ? userId + "_" + channel + "_m" : userId + "_" + channel;
        }
        var client = userCacheClient.get(key);
        if (client == null) {
            client = initClient(userId, multiModal);
            userCacheClient.put(key, client);
        }
        return client;
    }

    public Model getUserPreferredModel(String userId, boolean multiModal) {
        var model = modelProviders.getModel(userId,
                multiModal ? ModelConfig.ModelType.VISION : ModelConfig.ModelType.TEXT);
        return model;
    }


    private ChatClient initClient(String userId, boolean multiModal) {
        try {

            Resource agentMd = workspace.createRelative(AGENT_MD);
            if (!agentMd.exists()) {
                agentMd = workspace.createRelative("AGENT.md");
            }
            String agentPrompt = agentMd.getContentAsString(StandardCharsets.UTF_8) + System.lineSeparator()
                    + workspace.createRelative("INFO.md").getContentAsString(StandardCharsets.UTF_8) + System.lineSeparator();

            var model = getUserPreferredModel(userId, multiModal);
            var chatClientBuilder = ChatClient.builder((ChatModel) model);

            chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor())
                    .defaultSystem(p -> p.text(agentPrompt).param(AgentEnvironment.ENVIRONMENT_INFO_KEY,
                            AgentEnvironment.info()))
//                    .defaultToolCallbacks(mcpToolProvider.getToolCallbacks())
                    .defaultToolCallbacks(SkillsTool.builder().addSkillsDirectory(skillsDir(workspace).toString()).build())
                    .defaultTools(
                            CheckListTool.builder().build(),
                            TaskTool.builder().taskManager(taskManager).build(),
                            McpTool.builder().configurationManager(SpringUtil.getBean(ConfigurationManager.class)).build(),
                            //Bash execution tool
                            ShellTools.builder().build(),// built-in shell tools
                            // Read, Write and Edit files tool // fixme 这里需要限制访问权限
                            FileSystemTools.builder().build(),// built-in file system tools
                            // Smart web fetch tool
                            SmartWebFetchTool.builder(chatClientBuilder.clone().build()).build())
                    .defaultAdvisors(
                            ToolCallAdvisor.builder().build(),
                            MessageChatMemoryAdvisor.builder(chatMemory).build()
                    );


            if (autoDiscoveredTools != null && !autoDiscoveredTools.isEmpty()) {
                autoDiscoveredTools.forEach(autoDiscoveredTool -> chatClientBuilder.defaultTools(autoDiscoveredTool.tool()));
            }
            return chatClientBuilder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Path skillsDir(Resource workspace) throws IOException {
        Path skillsDir = workspace.getFile().toPath().resolve("skills");
        Files.createDirectories(skillsDir);
        return skillsDir;
    }
}
