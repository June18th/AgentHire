package com.git.hui.jobclaw.core.agent.identity.user.collector;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.git.hui.jobclaw.core.agent.ClientSelector;
import com.git.hui.jobclaw.core.agent.identity.CollectionState;
import com.git.hui.jobclaw.core.agent.identity.InfoCollector;
import com.git.hui.jobclaw.core.agent.identity.user.UserIdentityExtractor;
import com.git.hui.jobclaw.core.agent.identity.user.UserIdentityManager;
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
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI-based identity collector using LLM and tool calling.
 *
 * <p>This implementation uses an AI agent with the AskUserQuestionTool to:
 * <ul>
 *   <li>Dynamically generate questions based on conversation context</li>
 *   <li>Adapt questioning strategy based on user responses</li>
 *   <li>Generate comprehensive user profile using AI</li>
 *   <li>Provide more natural, conversational experience</li>
 * </ul>
 *
 * <p>Advantages over rule-based:
 * <ul>
 *   <li>More flexible and adaptive</li>
 *   <li>Can ask follow-up questions</li>
 *   <li>Better at handling unexpected responses</li>
 *   <li>Can infer information from context</li>
 * </ul>
 *
 * AIDEV-NOTE: AI-based implementation of identityCollector interface using LLM + tools
 */
@Component
public class AiBasedIdentityCollector implements InfoCollector {

    private static final Logger log = LoggerFactory.getLogger(AiBasedIdentityCollector.class);

    private final UserIdentityManager useridentityManager;
    private final UserIdentityExtractor useridentityExtractor;
    private final ChannelEventPublisher channelEventPublisher;

    // Track collection states per user
    private final Map<String, CollectionState> collectionStates = new ConcurrentHashMap<>();

    // Conversation history per user (for AI context)
    private final Map<String, List<Message>> conversationHistories = new ConcurrentHashMap<>();
    private BeanOutputConverter<AiCollectionResponse> beanOutputConverter;
    private String format;

    private String promptTemplate;

    public AiBasedIdentityCollector(UserIdentityManager useridentityManager,
                                    UserIdentityExtractor useridentityExtractor,
                                    ChannelEventPublisher channelEventPublisher,
                                    @Value("classpath:/prompts/user-identity-collect-prompt.md") Resource promptResource) {
        this.useridentityManager = useridentityManager;
        this.useridentityExtractor = useridentityExtractor;
        this.channelEventPublisher = channelEventPublisher;
        beanOutputConverter = new BeanOutputConverter<>(AiCollectionResponse.class);
        format = beanOutputConverter.getFormat();
        try {
            this.promptTemplate = promptResource.getContentAsString(StandardCharsets.UTF_8);
            log.info("UseridentityExtractor initialized with prompt template");
        } catch (IOException e) {
            log.error("Failed to load user identity extraction prompt template", e);
            throw new RuntimeException("Failed to initialize UseridentityExtractor", e);
        }
    }

    @Override
    public CollectorType getCollectorType() {
        return CollectorType.AI_BASED;
    }

    @Override
    public boolean shouldInitiateCollection(String jobClawUserId) {
        // Skip if already has identity
        if (useridentityManager.hasIdentity(jobClawUserId)) {
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
            log.debug("[AiBased] Skipping collection initiation for user: {}", jobClawUserId);
            return;
        }

        log.info("[AiBased] Initiating AI-driven collection for user: {} via channel: {}", jobClawUserId, channel);

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

        log.info("[AiBased] User answered: {} (user={})", userMessage, jobClawUserId);

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
        log.info("[AiBased] Starting AI conversation for user: {}", state.getJobClawUserId());

        // Create system prompt
        String systemPrompt = promptTemplate;

        // Create initial user message to trigger AI
        String initialPrompt = """
                用户刚刚开始对话，还没有任何画像信息。
                请友好地自我介绍，然后开始按照画像模板收集信息。
                                
                你需要收集的字段优先级：
                1. 【必填】毕业年份、学校、专业、期望城市、岗位类型、实习/正式
                2. 【选填】姓名、学历、期望行业、薪资、技术技能、实习经历、项目经历
                                
                请从最基础的信息开始（比如毕业年份和学校），一次只问一个问题。
                语气要友好自然，像朋友聊天，不要像审问。
                """;

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(initialPrompt));

        // Call AI with tool
        var aiResponse = callAiForCollection(state, systemPrompt, messages);
        if (aiResponse == null) {
            sendProactiveMessage(state, "现在大模型响应出了点问题，请稍后再试~");
        } else {
            sendProactiveMessage(state, aiResponse.question());
        }
    }

    /**
     * Continue AI conversation after user response
     */
    private void continueAiConversation(CollectionState state, List<Message> history) {
        String systemPrompt = promptTemplate;

        // Call AI and get structured response
        AiCollectionResponse aiResponse = callAiForCollection(state, systemPrompt, history);

        if (aiResponse == null) {
            log.error("[AiBased] AI returned null response for user: {}", state.getJobClawUserId());
            sendFallbackMessage(state);
            return;
        }

        // Check if AI decided to complete
        if (aiResponse.isComplete()) {
            log.info("[AiBased] AI decided to complete collection for user: {} (response: {})",
                    state.getJobClawUserId(), aiResponse);
            completeCollection(state, history);
            return;
        }

        // AI wants to ask another question
        if (aiResponse.question() != null && !aiResponse.question().isBlank()) {
            sendProactiveMessage(state, aiResponse.question());
            log.info("[AiBased] AI asking question: aiResponse={}, user={}", aiResponse, state.getJobClawUserId());
        } else {
            log.warn("[AiBased] AI returned empty question for user: {}", state.getJobClawUserId());
            sendFallbackMessage(state);
        }
    }

    /**
     * AI collection response with status
     */
    public record AiCollectionResponse(
            /** Whether collection is complete */
            @JsonPropertyDescription(value = "用户信息收集的状态，true 表示已经收集完数据，无需再次问答收集信息") boolean isComplete,
            /** Question to ask user (null if complete) */
            @JsonPropertyDescription(value = "询问用户以获取相关信息的问题，如：你预计什么时候毕业？；注意当问询结束之后，question应该为空") String question,
            @JsonPropertyDescription(value = "结束信息收集的原因，如：用户表现出不想继续回答的意愿") String completeReason,
            @JsonPropertyDescription(value = "已经收集到的字段，如：[\"name\", \"graduationYear\", \"university\"]") List<String> collectedFields
    ) {
    }


    /**
     * Call AI for collection and parse structured response
     */
    private AiCollectionResponse callAiForCollection(CollectionState state, String systemPrompt, List<Message> messages) {
        String jobClawUserId = state.getJobClawUserId();
        try {
            // Build conversation with system prompt
            List<Message> conversationWithSystem = new ArrayList<>();
            conversationWithSystem.add(new SystemMessage(systemPrompt));
            conversationWithSystem.addAll(messages);
            conversationWithSystem.add(new UserMessage(format));

            // Call AI
            log.info("[AiBased] AI raw req: {}", conversationWithSystem);
            var chatModel = (ChatModel) SpringUtil.getBean(ClientSelector.class).getUserPreferredModel(jobClawUserId,
                    false);
            String response = chatModel.call(Prompt.builder().messages(conversationWithSystem).chatOptions(
                            ToolCallingChatOptions.builder().temperature(0.7).build()).build())
                    .getResult().getOutput().getText();

            log.info("[AiBased] AI raw response for user {}: {}", jobClawUserId,
                    response.length() > 200 ? response.substring(0, 200) + "..." : response);

            // Parse JSON response
            try {
                return beanOutputConverter.convert(response);
            } catch (Exception e) {
                return new AiCollectionResponse(false, response, "出现异常:" + e.getMessage(), List.of());
            }
        } catch (Exception e) {
            log.error("[AiBased] Failed to call AI for user: {}", jobClawUserId, e);
            return null;
        }
    }

    /**
     * Send fallback message when AI fails
     */
    private void sendFallbackMessage(CollectionState state) {
        if (state != null) {
            sendProactiveMessage(state, "抱歉，我遇到了一些问题。让我换个方式了解你：你是哪所学校毕业的呢？");
        }
    }

    /**
     * Complete collection and generate identity.md
     */
    private void completeCollection(CollectionState state, List<Message> history) {
        log.info("[AiBased] Completing AI-driven collection for user: {}", state.getJobClawUserId());

        // Use UseridentityExtractor to generate identity.md from conversation
        String identity = useridentityExtractor.extract(state.getJobClawUserId(), "", history);

        if (identity == null || identity.isBlank()) {
            // Fallback to simple identity
            identity = buildSimpleidentity(state);
        }

        // Save identity
        useridentityManager.saveIdentity(state.getJobClawUserId(), identity);

        // Mark as completed
        state.complete();
        collectionStates.remove(state.getJobClawUserId());
        conversationHistories.remove(state.getJobClawUserId());

        // Send completion message
        String completionMsg = """
                🎉 太好了！通过刚才的聊天，我已经对你有了基本了解。
                                
                现在我可以为你提供更精准的求职推荐啦！
                如果以后有变化，随时告诉我，我会更新你的信息。
                                
                现在，有什么我可以帮你的吗？😊
                """;
        sendProactiveMessage(state, completionMsg);

        log.info("[AiBased] AI-driven collection completed for user: {}", state.getJobClawUserId());
    }

    /**
     * Build simple identity as fallback
     */
    private String buildSimpleidentity(CollectionState state) {
        Instant now = Instant.now();
        return """
                # User identity Profile
                                
                ## Basic Info
                - **jobClawUserId**: %s
                - **lastUpdated**: %s
                - **conversationCount**: 1
                - **collectedVia**: ai_based
                                
                ## Notes (备注)
                - 用户通过AI对话完成初始画像收集
                - 详细信息请查看对话历史
                """.formatted(state.getJobClawUserId(), now);
    }


    /**
     * Send proactive message to user
     */
    private void sendProactiveMessage(CollectionState state, String content) {
        try {
            channelEventPublisher.publishProactiveMessage("identity_AI_" + System.currentTimeMillis(),
                    state.getJobClawUserId(), state.getActiveChannel(), content);

            log.debug("[AiBased] Sent proactive message to user: {}", state.getJobClawUserId());
        } catch (Exception e) {
            log.error("[AiBased] Failed to send proactive message to user: {}", state.getJobClawUserId(), e);
        }
    }
}
