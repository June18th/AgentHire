package com.git.hui.jobclaw.core.monitor;

import java.time.Instant;

/**
 * Function Calling / 工具执行审计事件。
 * AI-GENERATED
 */
public record ToolCallRecord(
        String id,
        String invocationId,
        String jobClawUserId,
        String conversationId,
        String channel,
        String agent,
        String toolName,
        String toolCallId,
        Integer iteration,
        String argsSummary,
        String resultSummary,
        String status,
        Long durationMs,
        String errorMessage,
        Instant createTime
) {
}
