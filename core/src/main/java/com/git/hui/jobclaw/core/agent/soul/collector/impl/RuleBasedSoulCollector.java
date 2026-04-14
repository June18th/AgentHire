package com.git.hui.jobclaw.core.agent.soul.collector.impl;

import com.git.hui.jobclaw.core.agent.soul.UserSoulManager;
import com.git.hui.jobclaw.core.agent.soul.collector.SoulCollectionState;
import com.git.hui.jobclaw.core.agent.soul.collector.SoulCollector;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rule-based soul collector using predefined question flow.
 *
 * <p>This implementation uses a fixed set of questions organized by category.
 * It's simple, predictable, and doesn't require AI model calls.
 *
 * <p>Features:
 * <ul>
 *   <li>Predefined question flow (8 questions)</li>
 *   <li>No AI model dependency</li>
 *   <li>Fast and predictable</li>
 *   <li>Allows users to skip questions</li>
 * </ul>
 *
 * AIDEV-NOTE: Rule-based implementation of SoulCollector interface
 */
@Component
public class RuleBasedSoulCollector implements SoulCollector {

    private static final Logger log = LoggerFactory.getLogger(RuleBasedSoulCollector.class);

    private final UserSoulManager userSoulManager;
    private final ChannelEventPublisher channelEventPublisher;

    // Track collection states per user
    private final Map<String, SoulCollectionState> collectionStates = new ConcurrentHashMap<>();

    // Predefined question flow
    private static final List<SoulCollectionState.CollectionQuestion> DEFAULT_QUESTION_FLOW = List.of(
            // Basic Info
            new SoulCollectionState.CollectionQuestion(
                    SoulCollectionState.QuestionCategory.BASIC_INFO,
                    "你好！我是求职派助手。为了更好地帮助你，可以告诉我你的姓名吗？😊",
                    "name",
                    false
            ),
            new SoulCollectionState.CollectionQuestion(
                    SoulCollectionState.QuestionCategory.BASIC_INFO,
                    "你是什么时候毕业的呢？（例如：2026年）",
                    "graduationYear",
                    true
            ),

            // Education
            new SoulCollectionState.CollectionQuestion(
                    SoulCollectionState.QuestionCategory.EDUCATION,
                    "你在哪所学校就读呢？",
                    "university",
                    true
            ),
            new SoulCollectionState.CollectionQuestion(
                    SoulCollectionState.QuestionCategory.EDUCATION,
                    "你的专业是什么？",
                    "major",
                    true
            ),

            // Job Preferences
            new SoulCollectionState.CollectionQuestion(
                    SoulCollectionState.QuestionCategory.JOB_PREFERENCES,
                    "你想找哪个城市的工作呢？（可以说多个城市，用逗号分隔）",
                    "location",
                    true
            ),
            new SoulCollectionState.CollectionQuestion(
                    SoulCollectionState.QuestionCategory.JOB_PREFERENCES,
                    "你期望的岗位类型是什么？（例如：Java开发、产品经理、数据分析师等）",
                    "jobType",
                    true
            ),
            new SoulCollectionState.CollectionQuestion(
                    SoulCollectionState.QuestionCategory.JOB_PREFERENCES,
                    "你是在找实习岗位还是正式工作呢？（回复\"实习\"或\"正式\"）",
                    "internship",
                    true
            ),

            // Skills
            new SoulCollectionState.CollectionQuestion(
                    SoulCollectionState.QuestionCategory.SKILLS,
                    "你掌握哪些技术技能呢？（例如：Java、Python、Spring Boot等，可以列出多个）",
                    "skills",
                    false
            )
    );

    public RuleBasedSoulCollector(
            UserSoulManager userSoulManager,
            ChannelEventPublisher channelEventPublisher) {
        this.userSoulManager = userSoulManager;
        this.channelEventPublisher = channelEventPublisher;
    }

    @Override
    public CollectorType getCollectorType() {
        return CollectorType.RULE_BASED;
    }

    @Override
    public boolean shouldInitiateCollection(String jobClawUserId) {
        // Skip if already has soul
        if (userSoulManager.hasSoul(jobClawUserId)) {
            return false;
        }

        // Skip if collection already in progress
        SoulCollectionState state = collectionStates.get(jobClawUserId);
        if (state != null && state.isInProgress()) {
            return false;
        }

        return true;
    }

    @Override
    public void initiateCollection(String jobClawUserId, String channel, String conversationId) {
        if (!shouldInitiateCollection(jobClawUserId)) {
            log.debug("Skipping soul collection initiation for user: {}", jobClawUserId);
            return;
        }

        log.info("[RuleBased] Initiating collection for user: {} via channel: {}", jobClawUserId, channel);

        // Create collection state
        SoulCollectionState state = new SoulCollectionState(jobClawUserId);
        state.start(channel, conversationId);
        state.setRemainingQuestions(new ArrayList<>(DEFAULT_QUESTION_FLOW));
        collectionStates.put(jobClawUserId, state);

        // Send first question
        askNextQuestion(state);
    }

    @Override
    public void processAnswer(String jobClawUserId, String userMessage, String channel, String conversationId) {
        SoulCollectionState state = collectionStates.get(jobClawUserId);
        if (state == null || !state.isInProgress()) {
            return;
        }

        // Update channel info if changed
        state.setActiveChannel(channel);
        state.setConversationId(conversationId);

        // Record answer
        SoulCollectionState.CollectionQuestion currentQ = state.getCurrentQuestion();
        if (currentQ != null) {
            state.recordAnswer(currentQ.field(), userMessage);
            log.info("[RuleBased] Recorded answer for user {}: {} = {}", jobClawUserId, currentQ.field(), userMessage);
        }

        // Check if user wants to skip
        if (isSkipMessage(userMessage)) {
            sendSkipAcknowledgement(state);
        }

        // Ask next question or complete
        if (state.getRemainingQuestions().isEmpty()) {
            completeCollection(state);
        } else {
            askNextQuestion(state);
        }
    }

    @Override
    public Optional<SoulCollectionState> getCollectionState(String jobClawUserId) {
        return Optional.ofNullable(collectionStates.get(jobClawUserId));
    }

    /**
     * Ask the next question in the flow
     */
    private void askNextQuestion(SoulCollectionState state) {
        if (state.getRemainingQuestions().isEmpty()) {
            return;
        }

        SoulCollectionState.CollectionQuestion nextQuestion = state.getRemainingQuestions().get(0);
        state.markQuestionAsked(nextQuestion);

        String question = formatQuestion(nextQuestion, state.getAskedQuestions().size());
        sendProactiveMessage(state, question);

        log.info("[RuleBased] Asked question to user {}: {}", state.getJobClawUserId(), nextQuestion.field());
    }

    /**
     * Format question with progress indicator
     */
    private String formatQuestion(SoulCollectionState.CollectionQuestion question, int progress) {
        return String.format("""
                        【%d/%d】%s
                                        
                        （如果暂时不想回答，可以回复"跳过"或"稍后"）
                        """,
                progress,
                DEFAULT_QUESTION_FLOW.size(),
                question.question()
        );
    }

    /**
     * Complete the collection and create initial SOUL.md
     */
    private void completeCollection(SoulCollectionState state) {
        log.info("[RuleBased] Completing collection for user: {}", state.getJobClawUserId());

        // Build initial soul from collected answers
        String initialSoul = buildInitialSoul(state);

        // Save soul
        userSoulManager.saveSoul(state.getJobClawUserId(), initialSoul);

        // Mark as completed
        state.complete();
        collectionStates.remove(state.getJobClawUserId());

        // Send completion message
        String completionMsg = """
                🎉 太好了！我已经了解了你的基本情况。
                                
                现在我可以为你提供更精准的求职推荐啦！
                如果以后有变化，随时告诉我，我会更新你的信息。
                                
                现在，有什么我可以帮你的吗？😊
                """;
        sendProactiveMessage(state, completionMsg);

        log.info("[RuleBased] Collection completed for user: {}", state.getJobClawUserId());
    }

    /**
     * Build initial SOUL.md from collected answers
     */
    private String buildInitialSoul(SoulCollectionState state) {
        Map<String, String> answers = state.getCollectedAnswers();
        Instant now = Instant.now();

        StringBuilder soul = new StringBuilder();
        soul.append("# User Soul Profile\n\n");
        soul.append("## Basic Info\n");
        soul.append("- **jobClawUserId**: ").append(state.getJobClawUserId()).append("\n");
        soul.append("- **lastUpdated**: ").append(now).append("\n");
        soul.append("- **conversationCount**: 1\n");
        soul.append("- **collectedVia**: rule_based\n\n");

        soul.append("## Preferences (偏好)\n");
        soul.append("### Job Preferences (求职偏好)\n");
        soul.append("- **location**: [").append(answers.getOrDefault("location", "")).append("]\n");
        soul.append("- **jobType**: [").append(answers.getOrDefault("jobType", "")).append("]\n");
        soul.append("- **industry**: []\n");
        soul.append("- **salary**: []\n");
        soul.append("- **internship**: ").append("实习".equals(answers.get("internship"))).append("\n\n");

        soul.append("### Communication Preferences (沟通偏好)\n");
        soul.append("- **language**: [中文]\n");
        soul.append("- **detailLevel**: [简洁]\n");
        soul.append("- **responseStyle**: [友好]\n\n");

        soul.append("## Profile (个人特征)\n");
        soul.append("### Education (教育背景)\n");
        soul.append("- **university**: [").append(answers.getOrDefault("university", "")).append("]\n");
        soul.append("- **major**: [").append(answers.getOrDefault("major", "")).append("]\n");
        soul.append("- **graduationYear**: [").append(answers.getOrDefault("graduationYear", "")).append("]\n");
        soul.append("- **degree**: []\n\n");

        soul.append("### Skills (技能)\n");
        soul.append("- **technical**: [").append(answers.getOrDefault("skills", "")).append("]\n");
        soul.append("- **soft**: []\n");
        soul.append("- **languages**: [中文]\n\n");

        soul.append("### Experience (经验)\n");
        soul.append("- **internships**: []\n");
        soul.append("- **projects**: []\n");
        soul.append("- **awards**: []\n\n");

        soul.append("## Key Facts (关键事实)\n");
        if (answers.containsKey("university") && answers.containsKey("graduationYear")) {
            soul.append("- ").append(answers.get("university")).append(answers.get("graduationYear")).append("届毕业生\n");
        }
        if (answers.containsKey("jobType") && answers.containsKey("location")) {
            soul.append("- 寻找").append(answers.get("location")).append("地区").append(answers.get("jobType")).append(
                    "岗位\n");
        }
        soul.append("\n");

        soul.append("## History (历史行为)\n");
        soul.append("### Recent Activities (最近活动)\n");
        soul.append("- ").append(now.toString().substring(0, 10)).append(": 完成初始画像收集\n\n");

        soul.append("### Applied Jobs (投递记录)\n");
        soul.append("- []\n\n");

        soul.append("## Notes (备注)\n");
        soul.append("- 用户通过规则式对话完成初始画像收集\n");

        return soul.toString();
    }

    /**
     * Check if user message indicates skipping
     */
    private boolean isSkipMessage(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase().trim();
        return lower.contains("跳过") || lower.contains("稍后") || lower.contains("不想回答") || lower.equals("skip");
    }

    /**
     * Send skip acknowledgement
     */
    private void sendSkipAcknowledgement(SoulCollectionState state) {
        String msg = "好的，我们跳过这个问题，继续下一个~ 😊";
        sendProactiveMessage(state, msg);
    }

    /**
     * Send proactive message to user
     */
    private void sendProactiveMessage(SoulCollectionState state, String content) {
        try {

            channelEventPublisher.publishProactiveMessage(
                    "SOUL_" + System.currentTimeMillis(),
                    state.getJobClawUserId(),
                    state.getActiveChannel(),
                    content
            );

            log.debug("[RuleBased] Sent proactive message to user: {}", state.getJobClawUserId());
        } catch (Exception e) {
            log.error("[RuleBased] Failed to send proactive message to user: {}", state.getJobClawUserId(), e);
        }
    }
}
