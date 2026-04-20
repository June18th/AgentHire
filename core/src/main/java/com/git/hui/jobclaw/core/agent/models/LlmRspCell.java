package com.git.hui.jobclaw.core.agent.models;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * 大模型返回的基础信息
 * @author YiHui
 * @date 2026/4/16
 */
@Slf4j
public record LlmRspCell(String thinking, String content, String tool) {

    public static LlmRspCell of(ChatResponse chunk) {
        // 思考内容
        var r = chunk.getResult().getOutput().getMetadata().get("reasoningContent");
        String text = chunk.getResult().getOutput().getText();

        if (log.isDebugEnabled()) {
            log.debug("[agent rsp] Reasoning: \nthin>>{} \ntext>>{}", r, text);
        }
        if (StringUtils.isBlank(text) && r != null) {
            return new LlmRspCell((String) r, null, null);
        }

        // 兜底策略：处理未正确转换的换行符
        text = normalizeNewlines(text);

        // fixme 工具的返回
        return new LlmRspCell(null, text, null);
    }

    /**
     * 标准化换行符，将字面量 \n 转换为真正的换行符
     * 用于处理大模型返回或工具调用结果中可能存在的转义问题
     *
     * @param text 原始文本
     * @return 标准化后的文本
     */
    private static String normalizeNewlines(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // 将字面量的 \n (两个字符: 反斜杠 + n) 替换为真正的换行符
        // 注意：这里使用 replaceAll 需要转义反斜杠
        return text.replace("\\n", "\n");
    }

}
