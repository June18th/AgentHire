package com.git.hui.jobclaw.application.dao.repository;

import com.git.hui.jobclaw.application.dao.entity.JobApplicationStatusLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobApplicationStatusLogRepository extends JpaRepository<JobApplicationStatusLogEntity, Long> {
    List<JobApplicationStatusLogEntity> findByApplicationIdAndUserIdOrderByEventTimeDesc(Long applicationId, Long userId);
}
