package com.git.hui.jobclaw.core.agent.identity.soul;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.git.hui.jobclaw.core.agent.ClientSelector;
import com.git.hui.jobclaw.core.agent.identity.CollectionState;
import com.git.hui.jobclaw.core.agent.identity.InfoCollector;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI-based soul collector using LLM to collect AI personality preferences.
 *
 * <p>This implementation uses an AI agent to:
 * <ul>
 *   <li>Dynamically generate questions about personality preferences</li>
 *   <li>Adapt questioning strategy based on user responses</li>
 *   <li>Collect communication style, tone, and relationship preferences</li>
 *   <li>Generate comprehensive soul.md using SoulExtractor</li>
 * </ul>
 *
 * AIDEV-NOTE: AI-based soul collector for Phase 2 implementation
 */
@Component
public class UserAgentSoulCollector implements InfoCollector {

    private static final Logger log = LoggerFactory.getLogger(UserAgentSoulCollector.class);

    private final UserAgentSoulManager soulManager;
    private final UserAgentSoulExtractor userAgentSoulExtractor;
    private final ChannelEventPublisher channelEventPublisher;

    // Track collection states per user
    private final Map<String, CollectionState> collectionStates = new ConcurrentHashMap<>();

    // Conversation history per user (for AI context)
    private final Map<String, List<Message>> conversationHistories = new ConcurrentHashMap<>();

    // AI output converter
    private BeanOutputConverter<SoulCollectionResponse> beanOutputConverter;
    private String format;

    // Prompt template
    private String promptTemplate;

    public UserAgentSoulCollector(UserAgentSoulManager soulManager,
                                  UserAgentSoulExtractor userAgentSoulExtractor,
                                  ChannelEventPublisher channelEventPublisher,
                                  @Value("classpath:/prompts/agent-soul-collect-prompt.md") Resource promptResource) {
        this.soulManager = soulManager;
        this.userAgentSoulExtractor = userAgentSoulExtractor;
        this.channelEventPublisher = channelEventPublisher;

        beanOutputConverter = new BeanOutputConverter<>(SoulCollectionResponse.class);
        format = beanOutputConverter.getFormat();

        try {
            this.promptTemplate = promptResource.getContentAsString(StandardCharsets.UTF_8);
            log.info("AiBasedSoulCollector initialized with prompt template");
        } catch (IOException e) {
            log.error("Failed to load soul collection prompt template", e);
            throw new RuntimeException("Failed to initialize AiBasedSoulCollector", e);
        }
    }

    @Override
    public CollectorType getCollectorType() {
        return CollectorType.AI_BASED;
    }

    @Override
    public boolean shouldInitiateCollection(String jobClawUserId) {
        // Skip if already has soul
        if (soulManager.hasSoul(jobClawUserId)) {
            return false;
        }

        // Skip if collection already in progress
        CollectionState state = collectionStates.get(jobClawUserId);
        if (state != null && state.isInProgress()) {
            return false;
        }

        return true;
    }

    @Override
    public void initiateCollection(String jobClawUserId, String channel, String conversationId) {
        if (!shouldInitiateCollection(jobClawUserId)) {
            log.debug("[Soul] Skipping collection initiation for user: {}", jobClawUserId);
            return;
        }

        log.info("[Soul] Initiating AI-driven soul collection for user: {} via channel: {}",
                jobClawUserId, channel);

        // Create collection state
        CollectionState state = new CollectionState(jobClawUserId);
        state.start(channel, conversationId);
        collectionStates.put(jobClawUserId, state);

        // Initialize conversation history
        conversationHistories.put(jobClawUserId, new ArrayList<>());

        // Start AI-driven conversation
        startAiConversation(state);
    }

    @Override
    public void processAnswer(String jobClawUserId, String userMessage, String channel, String conversationId) {
        CollectionState state = collectionStates.get(jobClawUserId);
        if (state == null || !state.isInProgress()) {
            return;
        }

        // Update channel info
        state.setActiveChannel(channel);
        state.setConversationId(conversationId);

        // Add user message to history
        List<Message> history = conversationHistories.get(jobClawUserId);
        history.add(new UserMessage(userMessage));

        log.info("[Soul] User answered: {} (user={})", userMessage, jobClawUserId);

        // Continue AI conversation
        continueAiConversation(state, history);
    }

    @Override
    public Optional<CollectionState> getCollectionState(String jobClawUserId) {
        return Optional.ofNullable(collectionStates.get(jobClawUserId));
    }

    /**
     * Start AI-driven conversation using LLM
     */
    private void startAiConversation(CollectionState state) {
        log.info("[Soul] Starting AI conversation for user: {}", state.getJobClawUserId());

        // Create initial user message to trigger AI
        String initialPrompt = """
                用户刚刚开始对话,还没有设置AI人格。
                请友好地自我介绍,然后开始收集用户期望的AI人格信息。
                                
                你需要收集的字段优先级:
                1. 【必填】agentName, communication_style, emotional_tone, proactive_level, detail_orientation, relationship_type
                2. 【选填】formality_level, humor_frequency, values
                                
                请从基础信息开始(比如给AI起个名字),一次只问一个问题。
                语气要友好自然,像朋友聊天,不要像审问。
                """;

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(initialPrompt));

        // Call AI and get structured response
        SoulCollectionResponse aiResponse = callAiForCollection(state, messages);
        if (aiResponse == null) {
            sendProactiveMessage(state, "现在大模型响应出了点问题,请稍后再试~");
        } else {
            sendProactiveMessage(state, aiResponse.question());
        }
    }

    /**
     * Continue AI conversation after user response
     */
    private void continueAiConversation(CollectionState state, List<Message> history) {
        // Call AI and get structured response
        SoulCollectionResponse aiResponse = callAiForCollection(state, history);

        if (aiResponse == null) {
            sendProactiveMessage(state, "现在大模型响应出了点问题,请稍后再试~");
            return;
        }

        // Check if collection is complete
        if (aiResponse.isComplete()) {
            completeCollection(state, history);
        } else {
            // Ask next question
            sendProactiveMessage(state, aiResponse.question());
        }
    }

    /**
     * Call AI for collection with structured output
     */
    private SoulCollectionResponse callAiForCollection(CollectionState state, List<Message> history) {
        try {
            // Build system prompt
            String systemPrompt = promptTemplate + "\n\n" + format;

            // Create messages list with system prompt
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            messages.addAll(history);

            // Add instruction to return structured JSON
            messages.add(new UserMessage("\n请根据对话历史,返回结构化的JSON响应,包含是否完成、下一个问题、已收集的信息。"));

            // Call AI model
            var model = (ChatModel) SpringUtil.getBean(ClientSelector.class)
                    .getUserPreferredModel(state.getJobClawUserId(), false);

            Prompt prompt = new Prompt(messages);
            String response = model.call(prompt).getResult().getOutput().getText();

            // Parse structured response
            SoulCollectionResponse parsedResponse = beanOutputConverter.convert(response);

            log.debug("[Soul] AI response parsed successfully for user: {}", state.getJobClawUserId());
            return parsedResponse;

        } catch (Exception e) {
            log.error("[Soul] Failed to call AI for collection: {}", state.getJobClawUserId(), e);
            return null;
        }
    }

    /**
     * Complete the soul collection and generate soul.md
     */
    private void completeCollection(CollectionState state, List<Message> history) {
        log.info("[Soul] Collection completed for user: {}, generating soul.md", state.getJobClawUserId());

        try {
            // Extract soul from conversation history
            String soulMd = userAgentSoulExtractor.extract(
                    state.getJobClawUserId(),
                    "", // No existing soul
                    history,
                    "" // No user profile yet
            ); // Wait for completion

            // Save soul.md
            if (soulMd != null && !soulMd.isBlank()) {
                soulManager.saveSoul(state.getJobClawUserId(), soulMd);
                log.info("[Soul] soul.md saved for user: {}", state.getJobClawUserId());

                // Send completion message
                sendProactiveMessage(state,
                        "太好了!我已经了解你期望的AI人格了。接下来我会进一步了解你的基本信息,以便更好地帮助你~");
            }

            // Mark state as completed
            state.complete();

        } catch (Exception e) {
            log.error("[Soul] Failed to complete collection for user: {}", state.getJobClawUserId(), e);
            sendProactiveMessage(state, "生成人格设定时出了点问题,但不影响后续对话,我们继续吧~");
            state.complete();
        }
    }

    /**
     * Send proactive message to user
     */
    private void sendProactiveMessage(CollectionState state, String content) {
        channelEventPublisher.publishProactiveMessage(
                "SOUL_" + System.currentTimeMillis(),
                state.getJobClawUserId(),
                state.getActiveChannel(),
                content
        );
    }

    /**
     * AI collection response structure
     */
    public record SoulCollectionResponse(
            @JsonPropertyDescription("Whether soul collection is complete")
            boolean isComplete,

            @JsonPropertyDescription("Next question to ask user (empty if complete)")
            String question,

            @JsonPropertyDescription("Summary of collected information")
            String collectedSummary
    ) {
    }
}
