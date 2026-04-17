package com.git.hui.jobclaw.core.router.intent.classifier;

import com.git.hui.jobclaw.core.agent.LlmCaller;
import com.git.hui.jobclaw.core.router.intent.IntentClassifier;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 组合意图分类器
 *
 * 实现三层识别策略：
 * 1. 命令匹配（最高优先级）
 * 2. 关键词匹配（高优先级）
 * 3. LLM识别（中优先级，作为兜底）
 *
 * AIDEV-NOTE: 这是一个组合器，组合多个分类器的结果
 *
 * @author YiHui
 * @date 2026/4/17
 */
@Slf4j
@Component
public class CompositeIntentClassifier implements IntentClassifier {

    private final KeywordIntentClassifier keywordClassifier;
    private final LLMIntentClassifier llmClassifier;

    // 置信度门槛
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.9;
    private static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.7;

    public CompositeIntentClassifier(
            KeywordIntentClassifier keywordClassifier,
            LLMIntentClassifier llmClassifier) {
        this.keywordClassifier = keywordClassifier;
        this.llmClassifier = llmClassifier;
    }

    @Override
    public IntentClassificationRes classify(LlmCaller.UserConversationInfo userConversationInfo, String message, List<String> conversationHistory) {
        if (message == null || message.isBlank()) {
            return IntentClassificationRes.unknown("空消息");
        }

        // L0: 命令匹配（最高优先级）
        IntentClassificationRes commandResult = tryCommandMatch(message);
        if (commandResult != null) {
            log.debug("L0命令匹配: {}", commandResult.intentType());
            return commandResult;
        }

        // L1: 关键词匹配
        IntentClassificationRes keywordResult = keywordClassifier.classify(userConversationInfo, message, conversationHistory);
        if (keywordResult.isHighlyConfident()) {
            log.debug("L1关键词匹配(高置信): {}", keywordResult.intentType());
            return keywordResult;
        }

        // 如果关键词匹配结果可信度尚可，尝试增强
        if (keywordResult.isConfident()) {
            // L2: LLM验证（可选）
            // AIDEV-NOTE: 为了节省成本和响应时间，这里直接使用关键词结果
            log.debug("L1关键词匹配(中置信): {}", keywordResult.intentType());
            return keywordResult;
        }

        // L1结果不可信，降级到LLM
        if (keywordResult.confidence() < MEDIUM_CONFIDENCE_THRESHOLD) {
            log.debug("L1未匹配，降级到LLM");
            IntentClassificationRes llmResult = llmClassifier.classify(userConversationInfo, message, conversationHistory);
            if (llmResult.isConfident()) {
                log.debug("LLM匹配: {}", llmResult.intentType());
                return llmResult;
            }
        }

        // 兜底：返回Unknown
        return IntentClassificationRes.unknown("所有策略均未识别");
    }

    private IntentClassificationRes tryCommandMatch(String message) {
        String normalized = message.trim().toLowerCase();

        // 系统命令
        if (normalized.startsWith("/help")) {
            return IntentClassificationRes.highConfidence(PresetAgentIntro.HELP, "命令匹配: /help");
        }
        if (normalized.startsWith("/agents")) {
            return IntentClassificationRes.highConfidence(PresetAgentIntro.LIST_AGENTS, "命令匹配: /agents");
        }
        if (normalized.startsWith("/reset")) {
            return IntentClassificationRes.highConfidence(PresetAgentIntro.RESET, "命令匹配: /reset");
        }
        if (normalized.startsWith("/agent")) {
            return IntentClassificationRes.highConfidence(PresetAgentIntro.SWITCH_AGENT, "命令匹配: /agent");
        }

        return null;
    }

    @Override
    public boolean isAgentSwitchCommand(String message) {
        return keywordClassifier.isAgentSwitchCommand(message);
    }

    @Override
    public Optional<String> parseAgentSwitchCommand(String message) {
        return keywordClassifier.parseAgentSwitchCommand(message);
    }
}