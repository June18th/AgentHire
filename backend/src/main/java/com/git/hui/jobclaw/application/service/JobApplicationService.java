package com.git.hui.jobclaw.application.service;

import com.git.hui.jobclaw.application.convert.JobApplicationConvert;
import com.git.hui.jobclaw.application.dao.entity.JobApplicationEntity;
import com.git.hui.jobclaw.application.dao.entity.JobApplicationEventEntity;
import com.git.hui.jobclaw.application.dao.entity.JobApplicationStatusLogEntity;
import com.git.hui.jobclaw.application.dao.repository.JobApplicationEventRepository;
import com.git.hui.jobclaw.application.dao.repository.JobApplicationRepository;
import com.git.hui.jobclaw.application.dao.repository.JobApplicationStatusLogRepository;
import com.git.hui.jobclaw.constants.application.JobApplicationStatusEnum;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.oc.dao.entity.OcInfoEntity;
import com.git.hui.jobclaw.oc.dao.repository.OcRepository;
import com.git.hui.jobclaw.web.model.req.JobApplicationEventSaveReq;
import com.git.hui.jobclaw.web.model.req.JobApplicationFollowUpReq;
import com.git.hui.jobclaw.web.model.req.JobApplicationSaveReq;
import com.git.hui.jobclaw.web.model.req.JobApplicationSearchReq;
import com.git.hui.jobclaw.web.model.req.JobApplicationStatusUpdateReq;
import com.git.hui.jobclaw.web.model.res.JobApplicationEventVo;
import com.git.hui.jobclaw.web.model.res.JobApplicationVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

@Service
public class JobApplicationService {
    private static final int DELETED = -1;
    private static final int NORMAL = 1;

    private final JobApplicationRepository applicationRepository;
    private final JobApplicationStatusLogRepository statusLogRepository;
    private final JobApplicationEventRepository eventRepository;
    private final OcRepository ocRepository;

    public JobApplicationService(JobApplicationRepository applicationRepository,
                                 JobApplicationStatusLogRepository statusLogRepository,
                                 JobApplicationEventRepository eventRepository,
                                 OcRepository ocRepository) {
        this.applicationRepository = applicationRepository;
        this.statusLogRepository = statusLogRepository;
        this.eventRepository = eventRepository;
        this.ocRepository = ocRepository;
    }

    public PageListVo<JobApplicationVo> list(Long userId, JobApplicationSearchReq req) {
        Assert.notNull(userId, "Please login first");
        req.autoInitPage();
        PageListVo<JobApplicationEntity> page = applicationRepository.findList(userId, req);
        return PageListVo.of(JobApplicationConvert.toVoList(page.getList()), page.getTotal(), req.getPage(), req.getSize());
    }

    public JobApplicationVo detail(Long userId, Long id) {
        JobApplicationEntity entity = requireOwned(userId, id);
        JobApplicationVo vo = JobApplicationConvert.toVo(entity);
        vo.setStatusLogs(JobApplicationConvert.toStatusLogVoList(
                statusLogRepository.findByApplicationIdAndUserIdOrderByEventTimeDesc(id, userId)));
        vo.setEvents(JobApplicationConvert.toEventVoList(
                eventRepository.findByApplicationIdAndUserIdOrderByEventTimeAsc(id, userId)));
        return vo;
    }

    @Transactional
    public JobApplicationVo save(Long userId, JobApplicationSaveReq req) {
        Assert.notNull(userId, "Please login first");
        Assert.notNull(req, "Request body is required");

        JobApplicationEntity entity;
        boolean created = false;
        if (req.getId() != null) {
            entity = requireOwned(userId, req.getId());
        } else if (req.getJobId() != null) {
            entity = applicationRepository.findFirstByUserIdAndJobIdAndStateNotOrderByIdDesc(userId, req.getJobId(), DELETED)
                    .orElseGet(() -> {
                        JobApplicationEntity item = new JobApplicationEntity();
                        item.setUserId(userId);
                        item.setJobId(req.getJobId());
                        return item;
                    });
            created = entity.getId() == null;
        } else {
            entity = new JobApplicationEntity();
            entity.setUserId(userId);
            created = true;
        }

        Date now = new Date();
        fillJobSnapshot(entity, req);
        copyEditableFields(entity, req);
        normalizeRequiredDefaults(entity);
        if (created) {
            JobApplicationStatusEnum initialStatus = JobApplicationStatusEnum.of(req.getCurrentStatus());
            if (initialStatus == null) {
                initialStatus = JobApplicationStatusEnum.INTERESTED;
            }
            entity.setCurrentStatus(initialStatus.getCode());
            entity.setState(NORMAL);
            entity.setCreateTime(now);
        }
        entity.setUpdateTime(now);
        if (JobApplicationStatusEnum.SUBMITTED.getCode().equals(entity.getCurrentStatus()) && entity.getSubmittedAt() == null) {
            entity.setSubmittedAt(now);
        }

        JobApplicationEntity saved = applicationRepository.saveAndFlush(entity);
        if (created) {
            appendStatusLog(saved, null, saved.getCurrentStatus(), "create application", now);
        }
        return JobApplicationConvert.toVo(saved);
    }

    @Transactional
    public JobApplicationVo changeStatus(Long userId, JobApplicationStatusUpdateReq req) {
        Assert.notNull(req, "Request body is required");
        Assert.notNull(req.getId(), "Application id is required");
        JobApplicationEntity entity = requireOwned(userId, req.getId());

        JobApplicationStatusEnum current = JobApplicationStatusEnum.of(entity.getCurrentStatus());
        if (current == null) {
            current = JobApplicationStatusEnum.INTERESTED;
        }
        JobApplicationStatusEnum target = JobApplicationStatusEnum.of(req.getTargetStatus());
        Assert.notNull(target, "Target status is required");
        if (!current.canTransitTo(target)) {
            throw new IllegalArgumentException("Invalid application status transition: " + current.getCode() + " -> " + target.getCode());
        }
        if (current == target) {
            return JobApplicationConvert.toVo(entity);
        }

        Date now = new Date();
        entity.setCurrentStatus(target.getCode());
        entity.setUpdateTime(now);
        if (target == JobApplicationStatusEnum.SUBMITTED && entity.getSubmittedAt() == null) {
            entity.setSubmittedAt(now);
        }
        JobApplicationEntity saved = applicationRepository.saveAndFlush(entity);
        appendStatusLog(saved, current.getCode(), target.getCode(), req.getReason(), now);
        return JobApplicationConvert.toVo(saved);
    }

    @Transactional
    public JobApplicationVo reopen(Long userId, JobApplicationStatusUpdateReq req) {
        Assert.notNull(req, "Request body is required");
        Assert.notNull(req.getId(), "Application id is required");
        JobApplicationEntity entity = requireOwned(userId, req.getId());

        JobApplicationStatusEnum current = JobApplicationStatusEnum.of(entity.getCurrentStatus());
        Assert.isTrue(current != null && current.isTerminal(), "Only terminal application can be reopened");

        JobApplicationStatusEnum target = JobApplicationStatusEnum.of(req.getTargetStatus());
        if (target == null || target.isTerminal()) {
            target = JobApplicationStatusEnum.PREPARING;
        }

        Date now = new Date();
        entity.setCurrentStatus(target.getCode());
        entity.setUpdateTime(now);
        JobApplicationEntity saved = applicationRepository.saveAndFlush(entity);
        appendStatusLog(saved, current.getCode(), target.getCode(),
                StringUtils.hasText(req.getReason()) ? req.getReason() : "reopen application", now);
        return JobApplicationConvert.toVo(saved);
    }

    public List<JobApplicationVo> listByJobIds(Long userId, List<Long> jobIds) {
        Assert.notNull(userId, "Please login first");
        if (jobIds == null || jobIds.isEmpty()) {
            return List.of();
        }
        return JobApplicationConvert.toVoList(applicationRepository.findByUserIdAndJobIdInAndStateNot(userId, jobIds, DELETED));
    }

    @Transactional
    public boolean delete(Long userId, Long id) {
        JobApplicationEntity entity = requireOwned(userId, id);
        entity.setState(DELETED);
        entity.setUpdateTime(new Date());
        applicationRepository.saveAndFlush(entity);
        return true;
    }

    @Transactional
    public JobApplicationEventVo addEvent(Long userId, JobApplicationEventSaveReq req) {
        Assert.notNull(req, "Request body is required");
        Assert.notNull(req.getApplicationId(), "Application id is required");
        requireOwned(userId, req.getApplicationId());

        Date now = new Date();
        JobApplicationEventEntity event = new JobApplicationEventEntity()
                .setApplicationId(req.getApplicationId())
                .setUserId(userId)
                .setEventType(req.getEventType())
                .setEventTitle(req.getEventTitle())
                .setEventTime(req.getEventTime() == null ? now : req.getEventTime())
                .setEventResult(req.getEventResult())
                .setNote(req.getNote())
                .setCreateTime(now);
        return JobApplicationConvert.toVo(eventRepository.saveAndFlush(event));
    }

    @Transactional
    public JobApplicationVo completeFollowUp(Long userId, JobApplicationFollowUpReq req) {
        Assert.notNull(req, "Request body is required");
        Assert.notNull(req.getId(), "Application id is required");
        JobApplicationEntity entity = requireOwned(userId, req.getId());

        JobApplicationStatusEnum status = JobApplicationStatusEnum.of(entity.getCurrentStatus());
        if (status != null && status.isTerminal()) {
            throw new IllegalArgumentException("Terminal application cannot be followed up");
        }

        Date now = new Date();
        entity.setNextFollowUpAt(req.getNextFollowUpAt());
        entity.setUpdateTime(now);
        JobApplicationEntity saved = applicationRepository.saveAndFlush(entity);

        JobApplicationEventEntity event = new JobApplicationEventEntity()
                .setApplicationId(saved.getId())
                .setUserId(userId)
                .setEventType("FOLLOW_UP")
                .setEventTitle("已完成跟进")
                .setEventTime(now)
                .setEventResult("DONE")
                .setNote(req.getNote())
                .setCreateTime(now);
        eventRepository.save(event);
        return JobApplicationConvert.toVo(saved);
    }

    public List<JobApplicationEventVo> listEvents(Long userId, Long applicationId) {
        requireOwned(userId, applicationId);
        return JobApplicationConvert.toEventVoList(eventRepository.findByApplicationIdAndUserIdOrderByEventTimeAsc(applicationId, userId));
    }

    public List<JobApplicationEventVo> listEventsByDay(Long userId, Date start, Date end) {
        Assert.notNull(userId, "Please login first");
        Assert.notNull(start, "Start time is required");
        Assert.notNull(end, "End time is required");
        List<String> types = List.of("WRITTEN_TEST", "INTERVIEW", "HR", "OFFER");
        return JobApplicationConvert.toEventVoList(
                eventRepository.findByUserIdAndEventTypeInAndEventTimeBetweenOrderByEventTimeAsc(userId, types, start, end));
    }

    private JobApplicationEntity requireOwned(Long userId, Long id) {
        Assert.notNull(userId, "Please login first");
        Assert.notNull(id, "Application id is required");
        JobApplicationEntity entity = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application record not found: " + id));
        if (!userId.equals(entity.getUserId()) || entity.getState() != null && entity.getState() == DELETED) {
            throw new IllegalArgumentException("Application record not found: " + id);
        }
        return entity;
    }

    private void fillJobSnapshot(JobApplicationEntity entity, JobApplicationSaveReq req) {
        if (req.getJobId() == null) {
            return;
        }
        entity.setJobId(req.getJobId());
        ocRepository.findById(req.getJobId()).ifPresent(job -> fillFromJob(entity, job));
    }

    private void fillFromJob(JobApplicationEntity entity, OcInfoEntity job) {
        if (!StringUtils.hasText(entity.getCompanyName())) {
            entity.setCompanyName(job.getCompanyName());
        }
        if (!StringUtils.hasText(entity.getPosition())) {
            entity.setPosition(job.getPosition());
        }
        if (!StringUtils.hasText(entity.getApplyUrl())) {
            entity.setApplyUrl(job.getRelatedLink());
        }
        if (!StringUtils.hasText(entity.getCompanyType())) {
            entity.setCompanyType(job.getCompanyType());
        }
        if (!StringUtils.hasText(entity.getDeadline())) {
            entity.setDeadline(job.getDeadline());
        }
    }

    private void copyEditableFields(JobApplicationEntity entity, JobApplicationSaveReq req) {
        if (StringUtils.hasText(req.getCompanyName())) {
            entity.setCompanyName(req.getCompanyName());
        }
        if (StringUtils.hasText(req.getPosition())) {
            entity.setPosition(req.getPosition());
        }
        if (StringUtils.hasText(req.getApplyUrl())) {
            entity.setApplyUrl(req.getApplyUrl());
        }
        if (StringUtils.hasText(req.getCompanyType())) {
            entity.setCompanyType(req.getCompanyType());
        }
        if (StringUtils.hasText(req.getSource())) {
            entity.setSource(req.getSource());
        }
        if (req.getPriority() != null) {
            entity.setPriority(req.getPriority());
        }
        if (StringUtils.hasText(req.getDeadline())) {
            entity.setDeadline(req.getDeadline());
        }
        if (req.getSubmittedAt() != null) {
            entity.setSubmittedAt(req.getSubmittedAt());
        }
        if (req.getNextFollowUpAt() != null) {
            entity.setNextFollowUpAt(req.getNextFollowUpAt());
        }
        if (req.getRemark() != null) {
            entity.setRemark(req.getRemark());
        }
    }

    private void appendStatusLog(JobApplicationEntity entity, String fromStatus, String toStatus, String reason, Date eventTime) {
        JobApplicationStatusLogEntity log = new JobApplicationStatusLogEntity()
                .setApplicationId(entity.getId())
                .setUserId(entity.getUserId())
                .setFromStatus(fromStatus)
                .setToStatus(toStatus)
                .setOperatorType("USER")
                .setOperatorId(entity.getUserId())
                .setReason(reason == null ? "" : reason)
                .setEventTime(eventTime);
        statusLogRepository.save(log);
    }

    private void normalizeRequiredDefaults(JobApplicationEntity entity) {
        if (entity.getCompanyName() == null) {
            entity.setCompanyName("");
        }
        if (entity.getPosition() == null) {
            entity.setPosition("");
        }
        if (entity.getApplyUrl() == null) {
            entity.setApplyUrl("");
        }
        if (entity.getCompanyType() == null) {
            entity.setCompanyType("");
        }
        if (entity.getSource() == null) {
            entity.setSource("");
        }
        if (entity.getPriority() == null) {
            entity.setPriority(0);
        }
        if (entity.getDeadline() == null) {
            entity.setDeadline("");
        }
        if (entity.getRemark() == null) {
            entity.setRemark("");
        }
    }
}
