package com.git.hui.jobclaw.core.agent;

import com.git.hui.jobclaw.core.agent.identity.global.AgentIdentityManager;
import com.git.hui.jobclaw.core.agent.identity.info.UserAgentInfoManager;
import com.git.hui.jobclaw.core.agent.identity.soul.UserAgentSoulManager;
import com.git.hui.jobclaw.core.agent.identity.user.UserIdentityManager;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
@Component
public class DefaultAgent implements Agent {

    private final ClientSelector clientSelector;
    private final AgentIdentityManager agentIdentityManager;
    private final UserAgentSoulManager agentSoulManager;
    private final UserAgentInfoManager agentInfoManager;
    private final UserIdentityManager userIdentityManager;

    public DefaultAgent(ClientSelector clientSelector,
                       AgentIdentityManager agentIdentityManager,
                       UserAgentSoulManager agentSoulManager,
                       UserAgentInfoManager agentInfoManager,
                       UserIdentityManager userIdentityManager) {
        this.clientSelector = clientSelector;
        this.agentIdentityManager = agentIdentityManager;
        this.agentSoulManager = agentSoulManager;
        this.agentInfoManager = agentInfoManager;
        this.userIdentityManager = userIdentityManager;
    }

    private String buildChatMemConversationId(String jobClawUserId, String conversationId) {
        return jobClawUserId + "-" + conversationId;
    }

    @Override
    public String respondTo(String jobClawUserId, String conversationId, String question) {
        String finalConversationId = buildChatMemConversationId(jobClawUserId, conversationId);
        return clientSelector.getClient(jobClawUserId, conversationId, false)
                .prompt(buildPrompt(jobClawUserId, question))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, finalConversationId))
                .toolContext(Map.of("jobClawUserId", jobClawUserId))
                .call()
                .content();
    }

    @Override
    public <T> T prompt(String jobClawUserId, String conversationId, String input, Class<T> result) {
        String finalConversationId = buildChatMemConversationId(jobClawUserId, conversationId);
        return clientSelector.getClient(jobClawUserId, conversationId, false)
                .prompt(buildPrompt(jobClawUserId, input))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, finalConversationId))
                .toolContext(Map.of("jobClawUserId", jobClawUserId))
                .call()
                .entity(result);
    }

    @Override
    public Flux<String> streamResponse(String jobClawUserId, String conversationId, ChannelReceiveMessage message) {
        String finalConversationId = buildChatMemConversationId(jobClawUserId, conversationId);
        return clientSelector.getClient(jobClawUserId, conversationId, hasMedia(message))
                .prompt(buildPrompt(jobClawUserId, message))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, finalConversationId))
                .toolContext(Map.of("jobClawUserId", jobClawUserId))
                .stream().content();
    }

    @Override
    public String respondToMultiModal(String jobClawUserId, String conversationId, ChannelReceiveMessage message) {
        // Execute with conversation memory
        String finalConversationId = buildChatMemConversationId(jobClawUserId, conversationId);
        return clientSelector.getClient(jobClawUserId, conversationId, hasMedia(message))
                .prompt(buildPrompt(jobClawUserId, message))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, finalConversationId))
                .toolContext(Map.of("jobClawUserId", jobClawUserId))
                .call()
                .content();
    }


    private boolean hasMedia(ChannelReceiveMessage message) {
        return !CollectionUtils.isEmpty(message.getMedias()) || !CollectionUtils.isEmpty(message.getFiles());
    }

    /**
     * Build prompt with system identity documents for simple text question.
     */
    private Prompt buildPrompt(String jobClawUserId, String question) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        
        // Add system message with identity documents
        String systemPrompt = buildSystemPrompt(jobClawUserId);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        
        // Add user message
        messages.add(UserMessage.builder().text(question).build());
        
        return new Prompt(messages);
    }

    /**
     * Build prompt with system identity documents for multi-modal message.
     */
    private Prompt buildPrompt(String jobClawUserId, ChannelReceiveMessage message) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        
        // Add system message with identity documents
        String systemPrompt = buildSystemPrompt(jobClawUserId);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
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
     * Build system prompt by injecting identity documents.
     * 
     * Priority order: agent.md > soul.md > info.md > user.md
     */
    private String buildSystemPrompt(String jobClawUserId) {
        StringBuilder sb = new StringBuilder();
        
        // 1. Load global agent.md (operation manual)
        String agentMd = agentIdentityManager.loadAgentIdentity();
        if (agentMd != null && !agentMd.isBlank()) {
            sb.append("## Agent Operation Manual\n");
            sb.append(agentMd);
            sb.append("\n\n");
            log.debug("Injected agent.md for user: {} ({} chars)", jobClawUserId, agentMd.length());
        }
        
        // 2. Load user-level soul.md (personality)
        String soulMd = agentSoulManager.loadSoul(jobClawUserId);
        if (soulMd != null && !soulMd.isBlank()) {
            sb.append("## Your Soul & Personality\n");
            sb.append(soulMd);
            sb.append("\n\n");
            log.debug("Injected soul.md for user: {} ({} chars)", jobClawUserId, soulMd.length());
        }
        
        // 3. Load user-level info.md (identity card)
        String infoMd = agentInfoManager.loadInfo(jobClawUserId);
        if (infoMd != null && !infoMd.isBlank()) {
            sb.append("## Your Identity Card\n");
            sb.append(infoMd);
            sb.append("\n\n");
            log.debug("Injected info.md for user: {} ({} chars)", jobClawUserId, infoMd.length());
        }
        
        // 4. Load user profile user.md
        String userMd = userIdentityManager.loadIdentity(jobClawUserId);
        if (userMd != null && !userMd.isBlank()) {
            sb.append("## User Profile\n");
            sb.append(userMd);
            sb.append("\n\n");
            log.debug("Injected user.md for user: {} ({} chars)", jobClawUserId, userMd.length());
        }
        
        String result = sb.toString();
        if (result.isBlank()) {
            log.debug("No identity documents to inject for user: {}", jobClawUserId);
            return null;
        }
        
        log.debug("Built system prompt for user: {} ({} chars total)", jobClawUserId, result.length());
        return result;
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
}
