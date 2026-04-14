package com.git.hui.jobclaw.core.agent.memory;

import com.git.hui.jobclaw.core.agent.ClientSelector;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Session summarizer that generates concise summaries of conversation history.
 *
 * <p>Features:
 * <ul>
 *   <li>Async summary generation to avoid blocking</li>
 *   <li>Fallback to empty summary on failure</li>
 *   <li>Configurable trigger conditions</li>
 *   <li>Prompt template based</li>
 * </ul>
 *
 * <p>AIDEV-NOTE: Core component for Phase 2 session summarization
 */
@Component
public class SessionSummarizer {

    private static final Logger log = LoggerFactory.getLogger(SessionSummarizer.class);

    private static final int MAX_SUMMARY_LENGTH = 100;
    private static final int MAX_CONVERSATION_FOR_SUMMARY = 30;

    private final String promptTemplate;
    private final ContextWindowProperties properties;

    public SessionSummarizer(
            ContextWindowProperties properties,
            @Value("classpath:/prompts/session-summary-prompt.md") Resource promptResource) {
        this.properties = properties;

        // Load prompt template
        try {
            this.promptTemplate = promptResource.getContentAsString(StandardCharsets.UTF_8);
            log.info("SessionSummarizer initialized with prompt template");
        } catch (IOException e) {
            log.error("Failed to load session summary prompt template", e);
            throw new RuntimeException("Failed to initialize SessionSummarizer", e);
        }
    }

    /**
     * Check if summary should be generated based on conversation history.
     *
     * @param messages current conversation messages
     * @return true if summary should be generated
     */
    public boolean shouldSummarize(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }

        // Trigger summary if message count exceeds threshold
        int messageCount = messages.size();
        int triggerThreshold = Math.max(properties.getKeepRecent() * 2, 10);

        boolean shouldSummarize = messageCount > triggerThreshold;

        if (shouldSummarize) {
            log.debug("Should summarize: {} messages > threshold {}", messageCount, triggerThreshold);
        }

        return shouldSummarize;
    }

    /**
     * Generate summary asynchronously.
     *
     * @param messages conversation messages to summarize
     * @return CompletableFuture with summary text
     */
    public CompletableFuture<String> summarizeAsync(String jobClawUserId, List<Message> messages) {
        return CompletableFuture.supplyAsync(() -> summarize(jobClawUserId, messages));
    }

    /**
     * Generate summary synchronously.
     *
     * @param messages conversation messages to summarize
     * @return summary text, or empty string if failed
     */
    public String summarize(String jobClawUserId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        try {
            log.info("Generating summary for {} messages", messages.size());

            // Prepare conversation text
            String conversationText = formatConversation(messages);

            // Build prompt
            String prompt = promptTemplate.replace("{conversation_history}", conversationText);

            // Call AI to generate summary
            ChatModel model = (ChatModel) SpringUtil.getBean(ClientSelector.class).getUserPreferredModel(jobClawUserId,
                    false);
            String summary = model.call(prompt);

            // Validate and clean summary
            summary = validateSummary(summary);

            log.info("Summary generated successfully ({} chars): {}", summary.length(),
                    summary.substring(0, Math.min(50, summary.length())));

            return summary;

        } catch (Exception e) {
            log.error("Failed to generate summary", e);
            // Fallback to empty summary
            return "";
        }
    }

    /**
     * Format conversation messages into text for summarization.
     *
     * @param messages conversation messages
     * @return formatted conversation text
     */
    private String formatConversation(List<Message> messages) {
        // Limit to recent messages to avoid overwhelming the model
        List<Message> recentMessages = messages.size() > MAX_CONVERSATION_FOR_SUMMARY
                ? messages.subList(messages.size() - MAX_CONVERSATION_FOR_SUMMARY, messages.size())
                : messages;

        return recentMessages.stream()
                .map(msg -> {
                    String role = msg.getMessageType().getValue();
                    String text = msg.getText();
                    // Truncate very long messages
                    if (text.length() > 200) {
                        text = text.substring(0, 200) + "...";
                    }
                    return role.toUpperCase() + ": " + text;
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Validate and clean the generated summary.
     *
     * @param summary raw summary text
     * @return validated summary
     */
    private String validateSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return "";
        }

        // Trim whitespace
        summary = summary.trim();

        // Remove any prefix like "Summary:" or "摘要:"
        summary = summary.replaceAll("^(摘要[:：]?|Summary[:：]?)\\s*", "");

        // Truncate if too long
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            summary = summary.substring(0, MAX_SUMMARY_LENGTH - 3) + "...";
            log.warn("Summary truncated from original length");
        }

        return summary;
    }

    /**
     * Create a system message containing the summary.
     *
     * @param summary summary text
     * @return system message with summary, or null if summary is empty
     */
    public Message createSummaryMessage(String summary) {
        if (summary == null || summary.isBlank()) {
            return null;
        }

        String systemPrompt = String.format(
                "[对话摘要] 以下是之前对话的摘要，请基于此继续对话：%s",
                summary
        );

        return new UserMessage(systemPrompt);
    }
}
