package com.git.hui.jobclaw.core.agent.llm;

import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 会自动注入用户偏好的LlmCaller
 * 适用于基于个人信息的推荐、对话场景
 *
 * @author YiHui
 * @date 2026/4/9
 */
@Slf4j
public class UserPreferenceBasedLlmCaller extends BizAgentLlmCaller {
    private final TaskManager taskManager;

    private final List<AutoDiscoveredTool<?>> autoDiscoveredTools;

    public UserPreferenceBasedLlmCaller(ModelProviders modelProviders,
                                        IIdentityAgent identityAgent,
                                        ChatMemory chatMemory,
                                        TaskManager taskManager,
                                        List<AutoDiscoveredTool<?>> autoDiscoveredTools,
                                        String systemPrompt,
                                        ToolCallback... tools) {
        super(chatMemory, identityAgent, modelProviders, systemPrompt, tools);
        this.taskManager = taskManager;
        this.autoDiscoveredTools = autoDiscoveredTools;
    }

    public Flux<LlmRspCell> stream(UserConversationInfo conversationInfo, ChannelReceiveMessage message) {
        String jobClawUserId = conversationInfo.jobClawUserId();
        Prompt prompt = buildSoulPrompt(jobClawUserId, message);
        ChatClient client = getClient(conversationInfo, prompt);
        return client.prompt(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationInfo.genId()))
                .toolContext(Map.of("jobClawUserId", jobClawUserId, "user", conversationInfo))
                .stream()
                .chatResponse()
                .map(LlmRspCell::of);
    }

    public String call(UserConversationInfo conversationInfo, ChannelReceiveMessage message) {
        // Execute with conversation memory
        String jobClawUserId = conversationInfo.jobClawUserId();
        Prompt prompt = buildSoulPrompt(jobClawUserId, message);
        ChatClient client = getClient(conversationInfo, prompt);
        return client.prompt(buildSoulPrompt(jobClawUserId, message))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationInfo.genId()))
                .toolContext(Map.of("jobClawUserId", jobClawUserId, "user", conversationInfo))
                .call()
                .content();
    }


    /**
     * 根据用户 + 会话，获取对应的LLM客户端
     * @param user 用户对话信息
     * @return
     */
    protected ChatClient getClient(UserConversationInfo user, Prompt prompt) {
        // 判断message中是否包含media
        ModelConfig.ModelType model = ModelConfig.ModelType.TEXT;
        if (prompt.getUserMessages().stream().anyMatch(m -> m.getMedia() != null)) {
            model = ModelConfig.ModelType.VISION;
        }

        boolean multiModal = model == ModelConfig.ModelType.VISION;
        String userId = user.jobClawUserId();
        String channel = user.channel();

        String key;
        if (StringUtils.isBlank(channel)) {
            key = multiModal ? userId + "_m" : userId;
        } else {
            key = multiModal ? userId + "_" + channel + "_m" : userId + "_" + channel;
        }
        var client = super.chatClientMap.get(key);
        if (client == null) {
            client = initClient(userId, multiModal);
            log.info("[{}] init UserPreferenceBasedLlmCaller for user: {}, channel: {}", user.agent(), user.jobClawUserId(), user.channel());
            super.chatClientMap.put(key, client);
        }
        return client;
    }

    public Prompt buildSoulPrompt(String jobClawUserId, String defaultSystemPrompt, String question) {
        List<Message> messages = new ArrayList<>();

        // Add system message with identity documents
        String systemPrompt = identityAgent.buildSystemPrompt(jobClawUserId);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt + "\n\n" + defaultSystemPrompt));
        } else if (StringUtils.isNotBlank(defaultSystemPrompt)) {
            messages.add(new SystemMessage(defaultSystemPrompt));
        }

        // Add user message
        messages.add(UserMessage.builder().text(question).build());

        return new Prompt(messages);
    }


    /**
     * Build prompt with system identity documents for multi-modal message.
     */
    public Prompt buildSoulPrompt(String jobClawUserId, ChannelReceiveMessage message) {
        return buildSoulPrompt(jobClawUserId, null, message);
    }

    public Prompt buildSoulPrompt(String jobClawUserId, String defaultSystemPrompt, ChannelReceiveMessage message) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        // Add system message with identity documents
        String systemPrompt = identityAgent.buildSystemPrompt(jobClawUserId);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt + "\n\n" + defaultSystemPrompt));
        } else if (StringUtils.isNotBlank(defaultSystemPrompt)) {
            messages.add(new SystemMessage(defaultSystemPrompt));
        }

        // Add user message with media
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }

        String textContent = (message.getMessage() != null && !message.getMessage().isBlank())
                ? message.getMessage()
                : "Please analyze the attached media.";

        var userMessage = UserMessage.builder().text(textContent);

        // Add images as media
        List<Media> mediaList = new ArrayList<>();

        if (message.getMedias() != null) {
            for (var image : message.getMedias()) {
                try {
                    Media media = createImageMedia(image);
                    if (media != null) {
                        mediaList.add(media);
                    }
                } catch (Exception e) {
                    log.warn("Failed to load image: {}", image.getFilePath(), e);
                }
            }
        }

        // Add files as media (if supported by the model)
        if (message.getFiles() != null) {
            for (var file : message.getFiles()) {
                try {
                    Media media = createFileMedia(file);
                    if (media != null) {
                        mediaList.add(media);
                    }
                } catch (Exception e) {
                    log.warn("Failed to load file: {}", file.getFilePath(), e);
                }
            }
        }

        // Add media to prompt if any
        if (!mediaList.isEmpty()) {
            userMessage.media(mediaList);
        }

        messages.add(userMessage.build());
        return new Prompt(messages);
    }


    /**
     * Create Media object from ImageContent
     */
    private Media createImageMedia(ChannelReceiveMessage.MediaMsg image) {
        if (image.getData() != null && image.getData().length > 0) {
            // Inline image data (byte array)
            MimeType mimeType = MimeType.valueOf(image.getMimeType());
            return new Media(mimeType, new ByteArrayResource(image.getData()));
        } else if (image.getFilePath() != null) {
            // Image from file path
            Path path = image.getFilePath();
            if (path.toFile().exists()) {
                MimeType mimeType = MimeType.valueOf(image.getMimeType());
                return new Media(mimeType, new FileSystemResource(path.toFile()));
            }
        }
        return null;
    }

    /**
     * Create Media object from FileContent
     */
    private Media createFileMedia(ChannelReceiveMessage.FileMsg file) {
        if (file.getFilePath() != null) {
            Path path = file.getFilePath();
            if (path.toFile().exists()) {
                MimeType mimeType = MimeType.valueOf(file.getMimeType());
                return new Media(mimeType, new FileSystemResource(path.toFile()));
            }
        }
        return null;
    }


    public static final String AGENT_MD = "AGENT.private.md";

    private ChatClient initClient(String userId, boolean multiModal) {
        try {
            String agentPrompt;
            if (StringUtils.isNotBlank(systemPrompt)) {
                agentPrompt = systemPrompt;
            } else {
                Resource agentMd = workspace.createRelative(AGENT_MD);
                if (!agentMd.exists()) {
                    agentMd = workspace.createRelative("AGENT.md");
                }
                agentPrompt = agentMd.getContentAsString(StandardCharsets.UTF_8);
            }

            var defaultSystem = buildSystemPrompt(agentPrompt);
            var model = modelProviders.getModel(userId, multiModal ? ModelConfig.ModelType.VISION : ModelConfig.ModelType.TEXT);
            var chatClientBuilder = ChatClient.builder((ChatModel) model);

            chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor())
                    .defaultSystem(defaultSystem)
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
            if (super.tools != null && super.tools.length > 0) {
                chatClientBuilder.defaultToolCallbacks(tools);
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
