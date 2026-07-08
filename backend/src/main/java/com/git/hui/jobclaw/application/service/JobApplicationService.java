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
import com.git.hui.jobclaw.web.model.res.JobApplicationBriefVo;
import com.git.hui.jobclaw.web.model.res.JobApplicationEventVo;
import com.git.hui.jobclaw.web.model.res.JobApplicationVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JobApplicationService {
    private static final int DELETED = -1;
    private static final int NORMAL = 1;
    private static final List<String> IMPORTANT_EVENT_TYPES = List.of("WRITTEN_TEST", "INTERVIEW", "HR", "OFFER");

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
        return PageListVo.of(attachNextKeyEvents(JobApplicationConvert.toVoList(page.getList()), userId), page.getTotal(), req.getPage(), req.getSize());
    }

    public JobApplicationVo detail(Long userId, Long id) {
        JobApplicationEntity entity = requireOwned(userId, id);
        JobApplicationVo vo = JobApplicationConvert.toVo(entity);
        vo.setStatusLogs(JobApplicationConvert.toStatusLogVoList(
                statusLogRepository.findByApplicationIdAndUserIdOrderByEventTimeDesc(id, userId)));
        List<JobApplicationEventVo> events = enrichEvents(
                eventRepository.findByApplicationIdAndUserIdOrderByEventTimeAsc(id, userId), userId);
        vo.setEvents(events);
        vo.setNextKeyEvent(pickNextKeyEvent(events));
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
        if (created && JobApplicationStatusEnum.SUBMITTED.getCode().equals(entity.getCurrentStatus())) {
            ensureDefaultFollowUpAfterSubmission(entity);
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
        if (target == JobApplicationStatusEnum.SUBMITTED) {
            ensureDefaultFollowUpAfterSubmission(entity);
        } else {
            ensureDefaultFollowUpAfterProcessAdvance(entity, target, now);
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
        return attachNextKeyEvents(JobApplicationConvert.toVoList(applicationRepository.findByUserIdAndJobIdInAndStateNot(userId, jobIds, DELETED)), userId);
    }

    public List<JobApplicationVo> actionItems(Long userId, Integer limit) {
        return actionItems(userId, limit, null);
    }

    public List<JobApplicationVo> actionItems(Long userId, Integer limit, String scope) {
        Assert.notNull(userId, "Please login first");
        int size = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        return attachNextKeyEvents(JobApplicationConvert.toVoList(applicationRepository.findByUserIdAndStateNotAndCurrentStatusNotIn(
                        userId, DELETED, JobApplicationRepository.TERMINAL_STATUS_CODES)), userId)
                .stream()
                .filter(item -> !"NONE".equals(item.getActionPriority()))
                .filter(item -> matchesActionScope(item, scope))
                .sorted(actionItemComparator())
                .limit(size)
                .toList();
    }

    public JobApplicationBriefVo brief(Long userId, Integer limit) {
        Assert.notNull(userId, "Please login first");
        int size = limit == null ? 5 : Math.max(1, Math.min(limit, 20));
        List<JobApplicationVo> all = attachNextKeyEvents(JobApplicationConvert.toVoList(applicationRepository.findByUserIdAndStateNot(userId, DELETED)), userId);
        List<JobApplicationVo> actionItems = all.stream()
                .filter(this::hasActionPriority)
                .sorted(actionItemComparator())
                .toList();
        int active = (int) all.stream().filter(item -> !Boolean.TRUE.equals(item.getTerminal())).count();
        int overdueFollowUps = (int) all.stream().filter(item -> Boolean.TRUE.equals(item.getFollowUpOverdue())).count();
        int dueToday = (int) all.stream().filter(item -> "DUE_TODAY".equals(item.getDeadlineRisk())).count();
        int dueSoon = (int) all.stream().filter(item -> "DUE_SOON".equals(item.getDeadlineRisk())).count();
        int thisWeek = (int) all.stream().filter(item -> "THIS_WEEK".equals(item.getDeadlineRisk())).count();
        int staleSubmitted = (int) all.stream().filter(this::isStaleSubmitted).count();
        int submittedAndLater = (int) all.stream().filter(item -> List.of(
                JobApplicationStatusEnum.SUBMITTED.getCode(),
                JobApplicationStatusEnum.WRITTEN_TEST.getCode(),
                JobApplicationStatusEnum.INTERVIEW_1.getCode(),
                JobApplicationStatusEnum.INTERVIEW_2.getCode(),
                JobApplicationStatusEnum.HR_INTERVIEW.getCode(),
                JobApplicationStatusEnum.OFFER.getCode(),
                JobApplicationStatusEnum.ACCEPTED.getCode()
        ).contains(item.getCurrentStatus())).count();
        int interview = (int) all.stream().filter(item -> List.of(
                JobApplicationStatusEnum.WRITTEN_TEST.getCode(),
                JobApplicationStatusEnum.INTERVIEW_1.getCode(),
                JobApplicationStatusEnum.INTERVIEW_2.getCode(),
                JobApplicationStatusEnum.HR_INTERVIEW.getCode()
        ).contains(item.getCurrentStatus())).count();
        int offer = (int) all.stream().filter(item -> List.of(
                JobApplicationStatusEnum.OFFER.getCode(),
                JobApplicationStatusEnum.ACCEPTED.getCode()
        ).contains(item.getCurrentStatus())).count();
        LocalDate today = LocalDate.now();
        List<JobApplicationEventEntity> upcomingEventEntities = listImportantEvents(userId, today, today.plusDays(8));
        Date tomorrowStart = Date.from(today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        int todayEvents = (int) upcomingEventEntities.stream()
                .filter(event -> event.getEventTime() != null && event.getEventTime().before(tomorrowStart))
                .count();
        int next7DayEvents = upcomingEventEntities.size();
        List<JobApplicationEventVo> upcomingEvents = enrichEvents(upcomingEventEntities, userId).stream()
                .limit(Math.min(size, 5))
                .toList();

        return new JobApplicationBriefVo()
                .setTotal(all.size())
                .setActive(active)
                .setActionCount(actionItems.size())
                .setPriorityA(countPriority(actionItems, "A"))
                .setPriorityB(countPriority(actionItems, "B"))
                .setPriorityC(countPriority(actionItems, "C"))
                .setOverdueFollowUps(overdueFollowUps)
                .setDueToday(dueToday)
                .setDueSoon(dueSoon)
                .setThisWeek(thisWeek)
                .setSubmittedAndLater(submittedAndLater)
                .setStaleSubmitted(staleSubmitted)
                .setInterview(interview)
                .setOffer(offer)
                .setTodayEvents(todayEvents)
                .setNext7DayEvents(next7DayEvents)
                .setSummary(buildBriefSummary(actionItems.size(), overdueFollowUps, dueToday, dueSoon, staleSubmitted, interview, offer, todayEvents))
                .setUpcomingEvents(upcomingEvents)
                .setTopActions(actionItems.stream().limit(size).toList());
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
        JobApplicationEntity application = requireOwned(userId, req.getApplicationId());

        Date now = new Date();
        Date eventTime = req.getEventTime() == null ? now : req.getEventTime();
        JobApplicationEventEntity event = new JobApplicationEventEntity()
                .setApplicationId(req.getApplicationId())
                .setUserId(userId)
                .setEventType(req.getEventType())
                .setEventTitle(req.getEventTitle())
                .setEventTime(eventTime)
                .setEventResult(req.getEventResult())
                .setNote(req.getNote())
                .setCreateTime(now);
        JobApplicationEventEntity saved = eventRepository.saveAndFlush(event);
        syncApplicationAfterEvent(application, req.getEventType(), eventTime, now);
        return JobApplicationConvert.toVo(saved, application);
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
        entity.setNextFollowUpAt(req.getNextFollowUpAt() == null ? defaultFollowUpAfter(now) : req.getNextFollowUpAt());
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
        return enrichEvents(
                eventRepository.findByUserIdAndEventTypeInAndEventTimeBetweenOrderByEventTimeAsc(userId, IMPORTANT_EVENT_TYPES, start, end),
                userId);
    }

    private Comparator<JobApplicationVo> actionItemComparator() {
        return Comparator.comparingInt((JobApplicationVo item) -> actionPriorityRank(item.getActionPriority()))
                .thenComparing(item -> item.getDaysUntilDeadline() == null ? Integer.MAX_VALUE : item.getDaysUntilDeadline())
                .thenComparing(item -> item.getNextFollowUpAt() == null ? Long.MAX_VALUE : item.getNextFollowUpAt())
                .thenComparing(item -> item.getPriority() == null ? 0 : -item.getPriority())
                .thenComparing(item -> item.getUpdateTime() == null ? 0L : -item.getUpdateTime());
    }

    private boolean hasActionPriority(JobApplicationVo item) {
        return item != null && item.getActionPriority() != null && !"NONE".equals(item.getActionPriority());
    }

    private boolean matchesActionScope(JobApplicationVo item, String scope) {
        if (!StringUtils.hasText(scope) || item == null) {
            return true;
        }
        return switch (scope.trim().toUpperCase()) {
            case "A" -> "A".equals(item.getActionPriority());
            case "OVERDUE_FOLLOW_UP" -> Boolean.TRUE.equals(item.getFollowUpOverdue());
            case "DUE_TODAY" -> "DUE_TODAY".equals(item.getDeadlineRisk());
            case "DUE_SOON" -> "DUE_SOON".equals(item.getDeadlineRisk());
            case "STALE_SUBMITTED" -> isStaleSubmitted(item);
            default -> true;
        };
    }

    private int countPriority(List<JobApplicationVo> items, String priority) {
        return (int) items.stream().filter(item -> priority.equals(item.getActionPriority())).count();
    }

    private boolean isStaleSubmitted(JobApplicationVo item) {
        if (item == null || !JobApplicationStatusEnum.SUBMITTED.getCode().equals(item.getCurrentStatus())) {
            return false;
        }
        return item.getNextFollowUpAt() == null && item.getSubmittedAt() != null
                && item.getSubmittedAt() <= System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
    }

    private List<JobApplicationEventEntity> listImportantEvents(Long userId, LocalDate startInclusive, LocalDate endExclusive) {
        Date start = Date.from(startInclusive.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(endExclusive.atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<JobApplicationEventEntity> events = eventRepository.findByUserIdAndEventTypeInAndEventTimeBetweenOrderByEventTimeAsc(
                userId, IMPORTANT_EVENT_TYPES, start, end);
        return events == null ? List.of() : events;
    }

    private List<JobApplicationVo> attachNextKeyEvents(List<JobApplicationVo> applications, Long userId) {
        if (applications == null || applications.isEmpty()) {
            return List.of();
        }
        List<Long> applicationIds = applications.stream()
                .map(JobApplicationVo::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (applicationIds.isEmpty()) {
            return applications;
        }
        List<JobApplicationEventEntity> eventEntities = eventRepository.findByUserIdAndApplicationIdInAndEventTypeInAndEventTimeGreaterThanEqualOrderByEventTimeAsc(
                userId, applicationIds, IMPORTANT_EVENT_TYPES, new Date());
        Map<Long, JobApplicationEventVo> nextEventMap = enrichEvents(eventEntities, userId).stream()
                .collect(Collectors.toMap(JobApplicationEventVo::getApplicationId, Function.identity(), (first, ignored) -> first));
        applications.forEach(application -> application.setNextKeyEvent(nextEventMap.get(application.getId())));
        return applications;
    }

    private List<JobApplicationEventVo> enrichEvents(List<JobApplicationEventEntity> events, Long userId) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<Long> applicationIds = events.stream()
                .map(JobApplicationEventEntity::getApplicationId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        List<JobApplicationEntity> applicationEntities = applicationIds.isEmpty()
                ? List.of()
                : applicationRepository.findByUserIdAndIdInAndStateNot(userId, applicationIds, DELETED);
        Map<Long, JobApplicationEntity> applications = applicationEntities == null
                ? Map.of()
                : applicationEntities.stream()
                .collect(Collectors.toMap(JobApplicationEntity::getId, Function.identity(), (left, right) -> left));
        return events.stream()
                .map(event -> JobApplicationConvert.toVo(event, applications.get(event.getApplicationId())))
                .toList();
    }

    private JobApplicationEventVo pickNextKeyEvent(List<JobApplicationEventVo> events) {
        if (events == null || events.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        return events.stream()
                .filter(event -> event.getApplicationId() != null)
                .filter(event -> IMPORTANT_EVENT_TYPES.contains(event.getEventType()))
                .filter(event -> event.getEventTime() != null && event.getEventTime() >= now)
                .min(Comparator.comparing(JobApplicationEventVo::getEventTime))
                .orElse(null);
    }

    private String buildBriefSummary(int actionCount, int overdueFollowUps, int dueToday, int dueSoon, int staleSubmitted, int interview, int offer, int todayEvents) {
        if (overdueFollowUps > 0) {
            return "有 " + overdueFollowUps + " 条跟进已到期，建议优先处理。";
        }
        if (todayEvents > 0) {
            return "今天有 " + todayEvents + " 个笔面试或 Offer 安排，建议提前确认时间、材料和沟通要点。";
        }
        if (actionCount == 0) {
            return "当前没有必须马上处理的投递事项，可以继续筛选岗位或补充材料。";
        }
        if (dueToday > 0) {
            return "有 " + dueToday + " 个岗位今天截止，建议先确认材料并完成投递。";
        }
        if (dueSoon > 0) {
            return "有 " + dueSoon + " 个岗位临近截止，建议尽快安排投递。";
        }
        if (staleSubmitted > 0) {
            return "有 " + staleSubmitted + " 条投递超过 7 天未跟进，建议检查通知渠道并设置提醒。";
        }
        if (interview > 0) {
            return "当前有 " + interview + " 条笔面试流程，建议记录安排和复盘。";
        }
        if (offer > 0) {
            return "当前有 " + offer + " 条 Offer 相关记录，建议补充沟通结果和选择理由。";
        }
        return "当前有 " + actionCount + " 条待处理事项，建议按优先级逐条推进。";
    }

    private int actionPriorityRank(String priority) {
        if ("A".equals(priority)) {
            return 0;
        }
        if ("B".equals(priority)) {
            return 1;
        }
        if ("C".equals(priority)) {
            return 2;
        }
        return 3;
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

    private void ensureDefaultFollowUpAfterSubmission(JobApplicationEntity entity) {
        if (entity.getSubmittedAt() == null || entity.getNextFollowUpAt() != null) {
            return;
        }
        entity.setNextFollowUpAt(defaultFollowUpAfter(entity.getSubmittedAt()));
    }

    private void ensureDefaultFollowUpAfterProcessAdvance(JobApplicationEntity entity, JobApplicationStatusEnum target, Date now) {
        if (target == null || target.isTerminal()) {
            return;
        }
        if (entity.getNextFollowUpAt() != null && entity.getNextFollowUpAt().after(now)) {
            return;
        }
        if (target == JobApplicationStatusEnum.WRITTEN_TEST || target == JobApplicationStatusEnum.INTERVIEW_1
                || target == JobApplicationStatusEnum.INTERVIEW_2 || target == JobApplicationStatusEnum.HR_INTERVIEW) {
            entity.setNextFollowUpAt(Date.from(now.toInstant().plus(3, ChronoUnit.DAYS)));
            return;
        }
        if (target == JobApplicationStatusEnum.OFFER) {
            entity.setNextFollowUpAt(Date.from(now.toInstant().plus(2, ChronoUnit.DAYS)));
        }
    }

    private void syncApplicationAfterEvent(JobApplicationEntity entity, String eventType, Date eventTime, Date now) {
        JobApplicationStatusEnum status = JobApplicationStatusEnum.of(entity.getCurrentStatus());
        if (status != null && status.isTerminal()) {
            return;
        }
        JobApplicationStatusEnum targetStatus = targetStatusForEvent(eventType, status);
        boolean statusChanged = targetStatus != null && status != null && status != targetStatus && status.canTransitTo(targetStatus);
        if (statusChanged) {
            entity.setCurrentStatus(targetStatus.getCode());
        }
        boolean followUpChanged = applyDefaultFollowUpAfterEvent(entity, eventType, eventTime, now);
        if (statusChanged || followUpChanged) {
            entity.setUpdateTime(now);
            JobApplicationEntity saved = applicationRepository.saveAndFlush(entity);
            if (statusChanged) {
                appendStatusLog(saved, status.getCode(), targetStatus.getCode(), "sync status from application event", now);
            }
        }
    }

    private JobApplicationStatusEnum targetStatusForEvent(String eventType, JobApplicationStatusEnum current) {
        if ("WRITTEN_TEST".equals(eventType)) {
            return JobApplicationStatusEnum.WRITTEN_TEST;
        }
        if ("INTERVIEW".equals(eventType)) {
            if (current == JobApplicationStatusEnum.SUBMITTED || current == JobApplicationStatusEnum.WRITTEN_TEST) {
                return JobApplicationStatusEnum.INTERVIEW_1;
            }
            return current == JobApplicationStatusEnum.INTERVIEW_1 || current == JobApplicationStatusEnum.INTERVIEW_2
                    ? current
                    : null;
        }
        if ("HR".equals(eventType)) {
            return JobApplicationStatusEnum.HR_INTERVIEW;
        }
        if ("OFFER".equals(eventType)) {
            return JobApplicationStatusEnum.OFFER;
        }
        return null;
    }

    private boolean applyDefaultFollowUpAfterEvent(JobApplicationEntity entity, String eventType, Date eventTime, Date now) {
        if (entity.getNextFollowUpAt() != null && entity.getNextFollowUpAt().after(now)) {
            return false;
        }
        Date baseTime = eventTime.after(now) ? eventTime : now;
        if ("WRITTEN_TEST".equals(eventType) || "INTERVIEW".equals(eventType) || "HR".equals(eventType)) {
            entity.setNextFollowUpAt(Date.from(baseTime.toInstant().plus(1, ChronoUnit.DAYS)));
            return true;
        }
        if ("OFFER".equals(eventType)) {
            entity.setNextFollowUpAt(Date.from(baseTime.toInstant().plus(2, ChronoUnit.DAYS)));
            return true;
        }
        return false;
    }

    private Date defaultFollowUpAfter(Date baseTime) {
        return Date.from(baseTime.toInstant().plus(7, ChronoUnit.DAYS));
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
