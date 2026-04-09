package com.git.hui.jobclaw.core.agent;

import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
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

/**
 *
 * @author YiHui
 * @date 2026/4/9
 */
@Slf4j
@Component
public class DefaultAgent implements Agent {

    private final ClientSelector clientSelector;

    public DefaultAgent(ClientSelector clientSelector) {
        this.clientSelector = clientSelector;
    }

    @Override
    public String respondTo(String conversationId, String question) {
        return clientSelector.getClient(conversationId, false).prompt(buildPrompt(question)).advisors(a -> a.param(
                ChatMemory.CONVERSATION_ID,
                conversationId)).call().content();
    }

    @Override
    public <T> T prompt(String conversationId, String input, Class<T> result) {
        return clientSelector.getClient(conversationId, false).prompt(buildPrompt(input)).advisors(a -> a.param(
                ChatMemory.CONVERSATION_ID,
                conversationId)).call().entity(result);
    }

    @Override
    public Flux<String> streamResponse(String conversationId, String question) {
        return clientSelector.getClient(conversationId, false).prompt(buildPrompt(question)).advisors(a -> a.param(
                ChatMemory.CONVERSATION_ID,
                conversationId)).stream().content();
    }

    @Override
    public String respondToMultiModal(String conversationId, ChannelReceiveMessage message) {
        // Execute with conversation memory
        return clientSelector.getClient(conversationId,
                hasMedia(message)).prompt(buildPrompt(message)).advisors(a -> a.param(ChatMemory.CONVERSATION_ID,
                conversationId)).call().content();
    }


    private boolean hasMedia(ChannelReceiveMessage message) {
        return !CollectionUtils.isEmpty(message.getMedias()) || !CollectionUtils.isEmpty(message.getFiles());
    }

    private Prompt buildPrompt(String question) {
        return new Prompt(UserMessage.builder().text(question).build());
    }

    private Prompt buildPrompt(ChannelReceiveMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }

        // Add text content if present, otherwise use a default prompt for media-only messages
        String textContent = (message.getMessage() != null && !message.getMessage().isBlank()) ? message.getMessage() : "Please analyze the attached media.";

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
        return new Prompt(userMessage.build());
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
