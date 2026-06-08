package com.git.hui.jobclaw.core.agent.react;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ReAct 中间件的日志实现
 * <p>
 * 在 ReAct 循环的各个阶段记录详细日志，包括：
 * - 推理前后的消息内容
 * - 工具调用的请求和响应
 * - 循环的完成和错误信息
 *
 * @author YiHui
 * @date 2026/6/5
 */
@Component
public class LoggingReActMiddleware implements ReActMiddleware {

    private static final Logger log = LoggerFactory.getLogger(LoggingReActMiddleware.class);

    @Override
    public void beforeReasoning(List<Message> messages, int iter) {
        log.info("[ReAct-{}] Reasoning阶段开始，当前消息数: {}", iter, messages.size());
        if (log.isDebugEnabled() && !messages.isEmpty()) {
            log.debug("[ReAct-{}] 最后一条消息内容: {}", iter, messages.get(messages.size() - 1).getText());
        }
    }

    @Override
    public void afterReasoning(AssistantMessage assistantMessage, int iter) {
        log.info("[ReAct-{}] Reasoning阶段完成", iter);
        if (assistantMessage != null) {
            log.info("[ReAct-{}] 推理结果文本: {}", iter, assistantMessage.getText());
            if (assistantMessage.getToolCalls() != null && !assistantMessage.getToolCalls().isEmpty()) {
                log.info("[ReAct-{}] 工具调用数量: {}", iter, assistantMessage.getToolCalls().size());
                assistantMessage.getToolCalls().forEach(toolCall -> 
                    log.info("[ReAct-{}] 工具调用 - ID: {}, 名称: {}, 参数: {}", 
                            iter, toolCall.id(), toolCall.name(), toolCall.arguments())
                );
            }
        }
    }

    @Override
    public void beforeActing(List<AssistantMessage.ToolCall> toolCalls, int iter) {
        log.info("[ReAct-{}] Acting阶段开始，即将执行工具", iter);
        if (toolCalls != null && !toolCalls.isEmpty()) {
            toolCalls.forEach(toolCall -> 
                log.info("[ReAct-{}] 准备执行工具 - ID: {}, 名称: {}, 参数: {}", 
                        iter, toolCall.id(), toolCall.name(), toolCall.arguments())
            );
        }
    }

    @Override
    public void afterActing(ToolResponseMessage toolResponses, int iter) {
        log.info("[ReAct-{}] Acting阶段完成", iter);
        if (toolResponses != null && toolResponses.getResponses() != null) {
            toolResponses.getResponses().forEach(response -> 
                log.info("[ReAct-{}] 工具执行结果 - ID: {}, 内容: {}", 
                        iter, response.id(), response.responseData())
            );
        }
    }

    @Override
    public void onComplete(int totalIters, String finalResponse) {
        log.info("[ReAct] 推理循环完成，总迭代次数: {}", totalIters);
        log.info("[ReAct] 最终回答: {}", finalResponse);
    }

    @Override
    public void onError(Exception error, int iter) {
        log.error("[ReAct-{}] 发生错误: {}", iter, error.getMessage(), error);
    }
}
