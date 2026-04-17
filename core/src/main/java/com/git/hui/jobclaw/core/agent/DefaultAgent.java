package com.git.hui.jobclaw.core.agent;

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

    private final IIdentityAgent identityAgent;

    public DefaultAgent(ClientSelector clientSelector,
                        IIdentityAgent identityAgent) {
        this.clientSelector = clientSelector;
        this.identityAgent = identityAgent;
    }


    @Override
    public String respondTo(UserConversationInfo conversationInfo, String question) {
        String jobClawUserId = conversationInfo.jobClawUserId();
        return clientSelector.getClient(jobClawUserId, conversationInfo.channel(), false)
                .prompt(buildPrompt(jobClawUserId, question))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationInfo.genId()))
                .toolContext(Map.of("jobClawUserId", jobClawUserId))
                .call()
                .content();
    }

    @Override
    public <T> T prompt(UserConversationInfo conversationInfo, String input, Class<T> result) {
        String jobClawUserId = conversationInfo.jobClawUserId();
        return clientSelector.getClient(conversationInfo.jobClawUserId(), conversationInfo.channel(), false)
                .prompt(buildPrompt(jobClawUserId, input))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationInfo.genId()))
                .toolContext(Map.of("jobClawUserId", jobClawUserId))
                .call()
                .entity(result);
    }

    @Override
    public Flux<LlmRspCell> streamResponse(UserConversationInfo conversationInfo, ChannelReceiveMessage message) {
        String jobClawUserId = conversationInfo.jobClawUserId();
        return clientSelector.getClient(jobClawUserId, conversationInfo.channel(), hasMedia(message))
                .prompt(buildPrompt(jobClawUserId, message))
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
                .prompt(buildPrompt(jobClawUserId, message))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationInfo.genId()))
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
        String systemPrompt = identityAgent.buildSystemPrompt(jobClawUserId);
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
        String systemPrompt = identityAgent.buildSystemPrompt(jobClawUserId);
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
