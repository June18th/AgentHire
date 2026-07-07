package com.git.hui.jobclaw.application.dao.repository;

import com.git.hui.jobclaw.application.dao.entity.JobApplicationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;

public interface JobApplicationEventRepository extends JpaRepository<JobApplicationEventEntity, Long> {
    List<JobApplicationEventEntity> findByApplicationIdAndUserIdOrderByEventTimeAsc(Long applicationId, Long userId);

    List<JobApplicationEventEntity> findByUserIdAndApplicationIdInAndEventTypeInAndEventTimeGreaterThanEqualOrderByEventTimeAsc(
            Long userId, List<Long> applicationIds, List<String> eventTypes, Date start);

    List<JobApplicationEventEntity> findByUserIdAndEventTypeInAndEventTimeBetweenOrderByEventTimeAsc(
            Long userId, List<String> eventTypes, Date start, Date end);
}
