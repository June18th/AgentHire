package com.git.hui.jobclaw.core.router.intent.classifier;

import com.git.hui.jobclaw.core.agent.LlmCaller;
import com.git.hui.jobclaw.core.agent.llm.ClientSelector;
import com.git.hui.jobclaw.core.router.intent.IntentClassifier;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于大模型的意图分类器
 *
 * AIDEV-NOTE: 适用于模糊、多义性场景
 * 成本较高，响应较慢，作为兜底方案
 *
 * @author YiHui
 * @date 2026/4/17
 */
@Slf4j
@Component
public class LLMIntentClassifier implements IntentClassifier {

    private final ClientSelector clientSelector;

    private static final String PROMPT_TEMPLATE = """
            您是一个意图分类器，请分析用户的消息意图。

            可分类的意图类型：
            - COLLECT: 投递简历、收集岗位信息（用户想投递岗位、录入岗位信息）
            - RECOMMEND: 推荐岗位（用户想要推荐、想让系统帮忙找岗位）
            - SUBSCRIBE: 订阅推送（用户想订阅某个岗位的推送通知）
            - QUERY: 信息查询（用户想查询岗位状态、投递记录等）
            - PROFILE: 用户画像管理（用户想修改个人信息、偏好设置）
            - CHAT: 通用聊天Agent（当无法匹配上面意图时，采用这个通用的聊天Agent）
            - UNKNOWN: 无法确定

            用户消息：{{message}}

            请根据用户消息判断其意图，并返回JSON格式的分类结果：
            ```json
            {
              "intentType": "COLLECT",
              "confidence": 0.95,
              "reasoning": "用户提到了投递岗位"
            }
            ```

            注意：
            - intentType 的取值不应该在 [HELP, LIST_AGENTS, SWITCH_AGENT, REST] 这几个系统命令中取值
            - confidence 为置信度，范围 0.0-1.0
            - reasoning 为你的推理过程，1-2句话
            - 如果消息不明确，返回 UNKNOWN
            """;

    public LLMIntentClassifier(ClientSelector clientSelector) {
        this.clientSelector = clientSelector;
    }

    @Override
    public IntentClassificationRes classify(LlmCaller.UserConversationInfo userConversationInfo, String message, List<String> conversationHistory) {
        if (message == null || message.isBlank()) {
            return IntentClassificationRes.unknown("空消息");
        }

        try {
            String prompt = PROMPT_TEMPLATE.replace("{{message}}", message);

            // AIDEV-NOTE: 简化实现，同步调用
            // 实际场景应该使用流式响应或异步处理
            var model = clientSelector.getUserPreferredModel(userConversationInfo.jobClawUserId(), false);
            var response = ChatClient.builder((ChatModel) model).build().prompt(prompt).call().entity(IntentClassificationRes.class);
            if (List.of(PresetAgentIntro.HELP, PresetAgentIntro.LIST_AGENTS, PresetAgentIntro.SWITCH_AGENT, PresetAgentIntro.RESET)
                    .contains(response.intentType())) {
                return IntentClassificationRes.unknown("系统意图识别异常: " + response.intentType().getDescription());
            }

            return response;
        } catch (Exception e) {
            log.error("LLM意图识别失败: {}", message, e);
            return IntentClassificationRes.unknown("LLM调用失败: " + e.getMessage());
        }
    }
}