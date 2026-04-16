package com.git.hui.jobclaw.core.agent;

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

        // fixme 工具的返回
        return new LlmRspCell(null, text, null);
    }

}
