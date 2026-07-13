package com.git.hui.jobclaw.llm.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.Data;

import java.time.Instant;

/**
 * 工具调用审计实体。
 * AI-GENERATED
 */
@Data
@Entity(name = "tool_call_audit")
public class ToolCallAuditEntity {
    @Id
    private String id;
    private String invocationId;
    private String jobClawUserId;
    private String conversationId;
    private String channel;
    private String agent;
    private String toolName;
    private String toolCallId;
    private Integer iteration;
    @Lob
    private String argsSummary;
    @Lob
    private String resultSummary;
    private String status;
    private Long durationMs;
    @Column(length = 2000)
    private String errorMessage;
    private Instant createTime;
}
