package com.git.hui.jobclaw.core.agent.react;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;

/**
 * ReAct 推理循环的中间件接口
 * <p>
 * 在 ReAct 循环的各个阶段提供拦截点，用于：
 * - 日志记录 / 审计追踪
 * - 工具调用审批 / 权限控制
 * - 推理过程监控 / 限流
 * - 工具执行结果后处理
 * <p>
 * 所有方法均有默认空实现，按需覆写即可。
 *
 * @author YiHui
 * @date 2026/6/5
 */
public interface ReActMiddleware {

    /**
     * Reasoning 阶段前：LLM 即将进行推理
     *
     * @param messages 当前对话消息列表
     * @param iter     当前迭代次数（从0开始）
     */
    default void beforeReasoning(List<Message> messages, int iter) {
    }

    /**
     * Reasoning 阶段后：LLM 完成一次推理
     *
     * @param assistantMessage LLM 的推理结果（可能包含文本和/或工具调用）
     * @param iter             当前迭代次数
     */
    default void afterReasoning(AssistantMessage assistantMessage, int iter) {
    }

    /**
     * Acting 阶段前：即将执行工具
     *
     * @param toolCalls LLM 请求的工具调用列表
     * @param iter      当前迭代次数
     */
    default void beforeActing(List<AssistantMessage.ToolCall> toolCalls, int iter) {
    }

    /**
     * Acting 阶段后：工具执行完毕
     *
     * @param toolResponses 工具执行结果消息
     * @param iter          当前迭代次数
     */
    default void afterActing(ToolResponseMessage toolResponses, int iter) {
    }

    /**
     * 循环结束时：ReAct 推理循环完成
     *
     * @param totalIters    总迭代次数
     * @param finalResponse 最终回答内容
     */
    default void onComplete(int totalIters, String finalResponse) {
    }

    /**
     * 异常时：循环中发生错误
     *
     * @param error     异常
     * @param iter      发生错误时的迭代次数
     */
    default void onError(Exception error, int iter) {
    }
}
