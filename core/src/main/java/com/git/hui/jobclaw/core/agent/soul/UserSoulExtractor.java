package com.git.hui.jobclaw.core.agent.soul;

import com.git.hui.jobclaw.core.agent.ClientSelector;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Extracts and updates user soul/profile from conversation history.
 *
 * <p>Features:
 * <ul>
 *   <li>Async soul extraction to avoid blocking</li>
 *   <li>Incremental update based on existing soul</li>
 *   <li>Fallback to existing soul on failure</li>
 *   <li>Prompt template based</li>
 * </ul>
 *
 */
@Component
public class UserSoulExtractor {

    private static final Logger log = LoggerFactory.getLogger(UserSoulExtractor.class);

    private static final int MAX_CONVERSATION_FOR_EXTRACTION = 50;
    private final String promptTemplate;

    public UserSoulExtractor(
            @Value("classpath:/prompts/user-soul-extraction-prompt.md") Resource promptResource) {
        try {
            this.promptTemplate = promptResource.getContentAsString(StandardCharsets.UTF_8);
            log.info("UserSoulExtractor initialized with prompt template");
        } catch (IOException e) {
            log.error("Failed to load user soul extraction prompt template", e);
            throw new RuntimeException("Failed to initialize UserSoulExtractor", e);
        }
    }

    /**
     * Extract user soul asynchronously.
     *
     * @param jobClawUserId user ID
     * @param currentSoul current soul profile (may be empty)
     * @param messages new conversation messages
     * @return CompletableFuture with updated soul markdown
     */
    public CompletableFuture<String> extractAsync(String jobClawUserId, String currentSoul, List<Message> messages) {
        return CompletableFuture.supplyAsync(() -> extract(jobClawUserId, currentSoul, messages));
    }

    /**
     * Extract user soul synchronously.
     *
     * @param jobClawUserId user ID
     * @param currentSoul current soul profile (may be empty)
     * @param messages new conversation messages
     * @return updated soul markdown, or existing soul if failed
     */
    public String extract(String jobClawUserId, String currentSoul, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            log.debug("No messages to extract soul from");
            return currentSoul;
        }

        try {
            log.info("Extracting soul for user: {} ({} messages)", jobClawUserId, messages.size());

            // Prepare conversation text
            String conversationText = formatConversation(messages);

            // Use existing soul or empty
            String existingSoul = currentSoul != null ? currentSoul : "无现有画像";

            // Build prompt
            String prompt = promptTemplate
                    .replace("{current_soul}", existingSoul)
                    .replace("{conversation_history}", conversationText);

            // Call AI to extract soul
            var model = (ChatModel) SpringUtil.getBean(ClientSelector.class).getUserPreferredModel(jobClawUserId, false);
            String updatedSoul = model.call(prompt);

            // Validate and clean soul
            updatedSoul = validateSoul(updatedSoul, jobClawUserId);

            log.info("Soul extracted successfully for user: {} ({} chars)",
                    jobClawUserId, updatedSoul.length());

            return updatedSoul;

        } catch (Exception e) {
            log.error("Failed to extract soul for user: {}", jobClawUserId, e);
            // Fallback to existing soul
            return currentSoul;
        }
    }

    /**
     * Format conversation messages into text for extraction.
     *
     * @param messages conversation messages
     * @return formatted conversation text
     */
    private String formatConversation(List<Message> messages) {
        // Limit to recent messages to avoid overwhelming the model
        List<Message> recentMessages = messages.size() > MAX_CONVERSATION_FOR_EXTRACTION
                ? messages.subList(messages.size() - MAX_CONVERSATION_FOR_EXTRACTION, messages.size())
                : messages;

        return recentMessages.stream()
                .map(msg -> {
                    String role = msg.getMessageType().getValue();
                    String text = msg.getText();
                    // Truncate very long messages
                    if (text.length() > 300) {
                        text = text.substring(0, 300) + "...";
                    }
                    return role.toUpperCase() + ": " + text;
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Validate and clean the extracted soul.
     *
     * @param soul raw soul markdown
     * @param jobClawUserId user ID
     * @return validated soul
     */
    private String validateSoul(String soul, String jobClawUserId) {
        if (soul == null || soul.isBlank()) {
            return "";
        }

        // Trim whitespace
        soul = soul.trim();

        // Ensure it starts with # User Soul Profile
        if (!soul.startsWith("# User Soul Profile")) {
            soul = "# User Soul Profile\n\n" + soul;
        }

        // Ensure jobClawUserId is present
        if (!soul.contains("**jobClawUserId**")) {
            soul = soul.replace(
                    "## Basic Info",
                    "## Basic Info\n- **jobClawUserId**: " + jobClawUserId
            );
        }

        // Update timestamp
        soul = soul.replaceAll(
                "- \\*\\*lastUpdated\\*\\*: .+",
                "- **lastUpdated**: " + Instant.now().toString()
        );

        return soul;
    }
}
