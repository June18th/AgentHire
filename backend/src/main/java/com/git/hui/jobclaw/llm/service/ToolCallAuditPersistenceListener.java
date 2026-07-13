package com.git.hui.jobclaw.llm.service;

import com.git.hui.jobclaw.core.monitor.ToolCallRecord;
import com.git.hui.jobclaw.llm.dao.entity.ToolCallAuditEntity;
import com.git.hui.jobclaw.llm.dao.repository.ToolCallAuditRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 工具调用审计事件持久化监听器。
 * AI-GENERATED
 */
@Slf4j
@Component
public class ToolCallAuditPersistenceListener {

    private final ToolCallAuditRepository repository;

    public ToolCallAuditPersistenceListener(ToolCallAuditRepository repository) {
        this.repository = repository;
    }

    @Async
    @EventListener
    public void save(ToolCallRecord record) {
        try {
            ToolCallAuditEntity entity = new ToolCallAuditEntity();
            entity.setId(record.id());
            entity.setInvocationId(record.invocationId());
            entity.setJobClawUserId(record.jobClawUserId());
            entity.setConversationId(record.conversationId());
            entity.setChannel(record.channel());
            entity.setAgent(record.agent());
            entity.setToolName(record.toolName());
            entity.setToolCallId(record.toolCallId());
            entity.setIteration(record.iteration());
            entity.setArgsSummary(record.argsSummary());
            entity.setResultSummary(record.resultSummary());
            entity.setStatus(record.status());
            entity.setDurationMs(record.durationMs());
            entity.setErrorMessage(record.errorMessage());
            entity.setCreateTime(record.createTime());
            repository.save(entity);
        } catch (Exception e) {
            log.warn("持久化工具调用审计失败: tool={}, user={}", record.toolName(), record.jobClawUserId(), e);
        }
    }
}
