package com.git.hui.jobclaw.core.router.intent;

import com.git.hui.jobclaw.core.agent.LlmCaller;
import com.git.hui.jobclaw.core.router.intent.classifier.IntentClassificationRes;

import java.util.List;
import java.util.Optional;

/**
 * 意图分类器接口
 * 
 * 职责：分析用户消息，识别意图类型
 * 
 * AIDEV-NOTE: 实现类应该支持组合模式，支持多个分类器级联
 * 
 * @author YiHui
 * @date 2026/4/17
 */
public interface IntentClassifier {
    
    /**
     * 识别用户意图
     * 
     * @param message 用户消息
     * @param conversationHistory 对话历史（用于上下文理解，可为空）
     * @return 识别结果
     */
    IntentClassificationRes classify(LlmCaller.UserConversationInfo userConversationInfo, String message, List<String> conversationHistory);
    
    /**
     * 判断是否为Agent切换命令
     * AIDEV-NOTE: 命令格式 `/agent <agentId>` 或 `/agent`
     */
    boolean isAgentSwitchCommand(String message);
    
    /**
     * 解析Agent切换命令，获取目标Agent ID
     * 
     * @param message 用户消息
     * @return Agent ID，如果消息不是切换命令则返回空
     */
    Optional<String> parseAgentSwitchCommand(String message);
    
    /**
     * 判断是否为系统命令（如 /help, /reset）
     */
    default boolean isSystemCommand(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String trimmed = message.trim().toLowerCase();
        return trimmed.startsWith("/help") 
                || trimmed.startsWith("/agents")
                || trimmed.startsWith("/reset")
                || trimmed.equals("/agent");
    }
    
    /**
     * 解析系统命令（帮助、重置等）
     * 
     * @param message 用户消息
     * @return 系统命令类型，如果不是系统命令返回空
     */
    default Optional<PresetAgentIntro> parseSystemCommand(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String trimmed = message.trim().toLowerCase();
        
        if (trimmed.startsWith("/help")) {
            return Optional.of(PresetAgentIntro.HELP);
        }
        if (trimmed.startsWith("/agents")) {
            return Optional.of(PresetAgentIntro.LIST_AGENTS);
        }
        if (trimmed.startsWith("/reset")) {
            return Optional.of(PresetAgentIntro.RESET);
        }
        // /agent 不带参数表示查看当前Agent
        if (trimmed.equals("/agent")) {
            return Optional.of(PresetAgentIntro.SWITCH_AGENT);
        }
        
        return Optional.empty();
    }
}