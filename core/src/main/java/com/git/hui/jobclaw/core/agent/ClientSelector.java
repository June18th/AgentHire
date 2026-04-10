package com.git.hui.jobclaw.core.agent;

import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.core.tools.CheckListTool;
import com.git.hui.jobclaw.core.tools.McpTool;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springaicommunity.agent.utils.AgentEnvironment;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端选择器
 * @author YiHui
 * @date 2026/4/9
 */
@Component
public class ClientSelector {
    private final ModelProviders modelProviders;

    private final ChatMemory chatMemory;

    /**
     * 根据用户缓存的用户偏好配置，支持根据不同的用户，设置不同的启用模型
     */
    private Map<String, ChatClient> userCacheClient;

    @Value("${agent.workspace:Unknown}")
    private Resource workspace;
    public static final String AGENT_MD = "AGENT.private.md";

    public ClientSelector(ModelProviders modelProviders,
                          ChatMemory chatMemory) {
        this.modelProviders = modelProviders;
        this.chatMemory = chatMemory;
        this.userCacheClient = new ConcurrentHashMap<>();
    }


    public ChatClient getClient(String userId, boolean multiModal) {
        String key = multiModal ? userId + "_m" : userId;
        var client = userCacheClient.get(key);
        if (client == null) {
            client = initClient(userId, multiModal);
            userCacheClient.put(key, client);
        }
        return client;
    }


    private ChatClient initClient(String userId, boolean multiModal) {
        try {

            Resource agentMd = workspace.createRelative(AGENT_MD);
            if (!agentMd.exists()) {
                agentMd = workspace.createRelative("AGENT.md");
            }
            String agentPrompt = agentMd.getContentAsString(StandardCharsets.UTF_8) + System.lineSeparator()
                    + workspace.createRelative("INFO.md").getContentAsString(StandardCharsets.UTF_8) + System.lineSeparator();


            // 查询用户的偏好配置
            ModelConfig.ModelInfo prefer = modelProviders.getUserPreferredModel(userId,
                    multiModal ? ModelConfig.ModelType.VISION : ModelConfig.ModelType.TEXT);
            if (prefer == null) {
                // todo 用户必须有一个默认的模型配置，否则应该自动设置一个，这里先直接抛异常
                throw new RuntimeException("用户没有配置模型，请先配置模型");
            }
            var model = modelProviders.getModel(prefer.getProvider(), prefer.getModelName(), prefer.getApiKey());

            var chatClientBuilder = ChatClient.builder((ChatModel) model);

            chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor())
                    .defaultSystem(p -> p.text(agentPrompt).param(AgentEnvironment.ENVIRONMENT_INFO_KEY,
                            AgentEnvironment.info()))
//                    .defaultToolCallbacks(mcpToolProvider.getToolCallbacks())
                    .defaultToolCallbacks(SkillsTool.builder().addSkillsDirectory(skillsDir(workspace).toString()).build())
                    .defaultTools(
                            CheckListTool.builder().build(),
                            McpTool.builder().configurationManager(SpringUtil.getBean(ConfigurationManager.class)).build(),
                            //Bash execution tool
                            //ShellTools.builder().build(),// built-in shell tools
                            // Read, Write and Edit files tool // fixme 这里需要限制访问权限
                            FileSystemTools.builder().build(),// built-in file system tools
                            // Smart web fetch tool
                            SmartWebFetchTool.builder(chatClientBuilder.clone().build()).build())
                    .defaultAdvisors(
                            ToolCallAdvisor.builder().build(),
                            MessageChatMemoryAdvisor.builder(chatMemory).build()
                    );
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
