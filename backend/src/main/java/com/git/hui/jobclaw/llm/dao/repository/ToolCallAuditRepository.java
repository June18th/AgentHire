package com.git.hui.jobclaw.llm.dao.repository;

import com.git.hui.jobclaw.llm.dao.entity.ToolCallAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolCallAuditRepository extends JpaRepository<ToolCallAuditEntity, String> {
}
