package com.git.hui.jobclaw.application.service;

import com.git.hui.jobclaw.application.dao.entity.JobApplicationEntity;
import com.git.hui.jobclaw.application.dao.entity.JobApplicationEventEntity;
import com.git.hui.jobclaw.application.dao.entity.JobApplicationStatusLogEntity;
import com.git.hui.jobclaw.application.dao.repository.JobApplicationEventRepository;
import com.git.hui.jobclaw.application.dao.repository.JobApplicationRepository;
import com.git.hui.jobclaw.application.dao.repository.JobApplicationStatusLogRepository;
import com.git.hui.jobclaw.constants.application.JobApplicationStatusEnum;
import com.git.hui.jobclaw.oc.dao.repository.OcRepository;
import com.git.hui.jobclaw.web.model.req.JobApplicationEventSaveReq;
import com.git.hui.jobclaw.web.model.req.JobApplicationFollowUpReq;
import com.git.hui.jobclaw.web.model.req.JobApplicationSaveReq;
import com.git.hui.jobclaw.web.model.req.JobApplicationStatusUpdateReq;
import com.git.hui.jobclaw.web.model.res.JobApplicationBriefVo;
import com.git.hui.jobclaw.web.model.res.JobApplicationReviewVo;
import com.git.hui.jobclaw.web.model.res.JobApplicationVo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobApplicationServiceTest {

    @Test
    void briefSummarizesActionSignalsForAgentUse() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationService service = newService(applicationRepository);
        Long userId = 7L;
        when(applicationRepository.findByUserIdAndStateNot(userId, -1)).thenReturn(List.of(
                base(userId, JobApplicationStatusEnum.PREPARING)
                        .setCompanyName("Alpha")
                        .setPosition("Java backend")
                        .setDeadline(LocalDate.now().toString()),
                base(userId, JobApplicationStatusEnum.SUBMITTED)
                        .setCompanyName("Beta")
                        .setPosition("Platform")
                        .setNextFollowUpAt(Date.from(LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())),
                base(userId, JobApplicationStatusEnum.INTERVIEW_1)
                        .setCompanyName("Gamma")
                        .setPosition("Server engineer")
        ));

        JobApplicationBriefVo brief = service.brief(userId, 2);

        assertThat(brief.getTotal()).isEqualTo(3);
        assertThat(brief.getActive()).isEqualTo(3);
        assertThat(brief.getActionCount()).isEqualTo(3);
        assertThat(brief.getPriorityA()).isEqualTo(2);
        assertThat(brief.getOverdueFollowUps()).isEqualTo(1);
        assertThat(brief.getDueToday()).isEqualTo(1);
        assertThat(brief.getProcessNeedsFollowUp()).isEqualTo(1);
        assertThat(brief.getInterview()).isEqualTo(1);
        assertThat(brief.getTopActions()).hasSize(2);
        assertThat(brief.getTopActions()).allMatch(item -> "A".equals(item.getActionPriority()));
    }

    @Test
    void briefSummarizesStaleSubmittedApplications() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationService service = newService(applicationRepository);
        Long userId = 7L;
        when(applicationRepository.findByUserIdAndStateNot(userId, -1)).thenReturn(List.of(
                base(userId, JobApplicationStatusEnum.SUBMITTED)
                        .setCompanyName("Delta")
                        .setPosition("Backend")
                        .setSubmittedAt(Date.from(LocalDate.now().minusDays(8).atStartOfDay(ZoneId.systemDefault()).toInstant()))
        ));

        JobApplicationBriefVo brief = service.brief(userId, 5);

        assertThat(brief.getActionCount()).isEqualTo(1);
        assertThat(brief.getPriorityB()).isEqualTo(1);
        assertThat(brief.getStaleSubmitted()).isEqualTo(1);
        assertThat(brief.getSummary()).isNotBlank();
    }

    @Test
    void briefSummarizesUnknownDeadlines() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationService service = newService(applicationRepository);
        Long userId = 7L;
        when(applicationRepository.findByUserIdAndStateNot(userId, -1)).thenReturn(List.of(
                base(userId, JobApplicationStatusEnum.PREPARING)
                        .setCompanyName("Epsilon")
                        .setPosition("Algorithm")
        ));

        JobApplicationBriefVo brief = service.brief(userId, 5);

        assertThat(brief.getActionCount()).isEqualTo(1);
        assertThat(brief.getPriorityC()).isEqualTo(1);
        assertThat(brief.getUnknownDeadline()).isEqualTo(1);
        assertThat(brief.getSummary()).contains("截止时间未知");
    }

    @Test
    void briefSummarizesExpiredDeadlines() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationService service = newService(applicationRepository);
        Long userId = 7L;
        when(applicationRepository.findByUserIdAndStateNot(userId, -1)).thenReturn(List.of(
                base(userId, JobApplicationStatusEnum.PREPARING)
                        .setCompanyName("Zeta")
                        .setPosition("Data")
                        .setDeadline(LocalDate.now().minusDays(1).toString())
        ));

        JobApplicationBriefVo brief = service.brief(userId, 5);

        assertThat(brief.getActionCount()).isEqualTo(1);
        assertThat(brief.getPriorityB()).isEqualTo(1);
        assertThat(brief.getExpiredDeadline()).isEqualTo(1);
        assertThat(brief.getSummary()).contains("截止时间已过");
    }

    @Test
    void briefSummarizesProcessFollowUpGaps() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationService service = newService(applicationRepository);
        Long userId = 7L;
        when(applicationRepository.findByUserIdAndStateNot(userId, -1)).thenReturn(List.of(
                base(userId, JobApplicationStatusEnum.INTERVIEW_1)
                        .setCompanyName("Sigma")
                        .setPosition("Platform")
        ));

        JobApplicationBriefVo brief = service.brief(userId, 5);

        assertThat(brief.getActionCount()).isEqualTo(1);
        assertThat(brief.getPriorityB()).isEqualTo(1);
        assertThat(brief.getProcessNeedsFollowUp()).isEqualTo(1);
        assertThat(brief.getSummary()).contains("流程已推进但未设置跟进");
    }

    @Test
    void briefSummarizesTodayImportantEvents() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationEventRepository eventRepository = mock(JobApplicationEventRepository.class);
        JobApplicationService service = newService(applicationRepository, eventRepository);
        Long userId = 7L;
        when(applicationRepository.findByUserIdAndStateNot(userId, -1)).thenReturn(List.of(
                base(userId, JobApplicationStatusEnum.INTERVIEW_1)
                        .setId(101L)
                        .setCompanyName("Gamma")
                        .setPosition("Server engineer")
        ));
        when(applicationRepository.findByUserIdAndIdInAndStateNot(any(), any(), any())).thenReturn(List.of(
                base(userId, JobApplicationStatusEnum.INTERVIEW_1)
                        .setId(101L)
                        .setCompanyName("Gamma")
                        .setPosition("Server engineer"),
                base(userId, JobApplicationStatusEnum.OFFER)
                        .setId(102L)
                        .setCompanyName("Sigma")
                        .setPosition("Platform engineer")
        ));
        Date todayEventTime = Date.from(LocalDate.now().atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant());
        Date futureEventTime = Date.from(LocalDate.now().plusDays(3).atTime(15, 0).atZone(ZoneId.systemDefault()).toInstant());
        when(eventRepository.findByUserIdAndEventTypeInAndEventTimeBetweenOrderByEventTimeAsc(any(), any(), any(), any()))
                .thenReturn(List.of(
                        new JobApplicationEventEntity()
                                .setId(11L)
                                .setApplicationId(101L)
                                .setEventType("INTERVIEW")
                                .setEventTitle("technical interview")
                                .setEventTime(todayEventTime),
                        new JobApplicationEventEntity()
                                .setId(12L)
                                .setApplicationId(102L)
                                .setEventType("OFFER")
                                .setEventTitle("offer discussion")
                                .setEventTime(futureEventTime)
                ));

        JobApplicationBriefVo brief = service.brief(userId, 5);

        assertThat(brief.getTodayEvents()).isEqualTo(1);
        assertThat(brief.getNext7DayEvents()).isEqualTo(2);
        assertThat(brief.getUpcomingEvents()).hasSize(2);
        assertThat(brief.getUpcomingEvents().getFirst().getEventType()).isEqualTo("INTERVIEW");
        assertThat(brief.getUpcomingEvents().getFirst().getEventTitle()).isEqualTo("technical interview");
        assertThat(brief.getUpcomingEvents().getFirst().getCompanyName()).isEqualTo("Gamma");
        assertThat(brief.getUpcomingEvents().getFirst().getPosition()).isEqualTo("Server engineer");
        assertThat(brief.getUpcomingEvents().getFirst().getEventUrgency()).isEqualTo("TODAY");
        assertThat(brief.getUpcomingEvents().getFirst().getHoursUntilEvent()).isNotNull();
        assertThat(brief.getUpcomingEvents().getFirst().getSuggestedPreparation()).contains("面试时间");
        assertThat(brief.getSummary()).contains("今天有 1 个笔面试或 Offer 安排");
    }

    @Test
    void actionItemsCanBeFilteredBySmartScope() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationService service = newService(applicationRepository);
        Long userId = 7L;
        when(applicationRepository.findByUserIdAndStateNotAndCurrentStatusNotIn(userId, -1, JobApplicationRepository.TERMINAL_STATUS_CODES))
                .thenReturn(List.of(
                        base(userId, JobApplicationStatusEnum.PREPARING)
                                .setCompanyName("Alpha")
                                .setPosition("Java backend")
                                .setDeadline(LocalDate.now().toString()),
                        base(userId, JobApplicationStatusEnum.SUBMITTED)
                                .setCompanyName("Beta")
                                .setPosition("Platform")
                                .setSubmittedAt(Date.from(LocalDate.now().minusDays(8).atStartOfDay(ZoneId.systemDefault()).toInstant())),
                        base(userId, JobApplicationStatusEnum.PREPARING)
                                .setCompanyName("Gamma")
                                .setPosition("Frontend")
                                .setDeadline(LocalDate.now().plusDays(5).toString()),
                        base(userId, JobApplicationStatusEnum.INTERVIEW_1)
                                .setCompanyName("Sigma")
                                .setPosition("Platform")
                                .setDeadline(LocalDate.now().plusDays(30).toString()),
                        base(userId, JobApplicationStatusEnum.PREPARING)
                                .setCompanyName("Delta")
                                .setPosition("Data"),
                        base(userId, JobApplicationStatusEnum.PREPARING)
                                .setCompanyName("Zeta")
                                .setPosition("Data")
                                .setDeadline(LocalDate.now().minusDays(1).toString())
                ));

        List<JobApplicationVo> dueToday = service.actionItems(userId, 20, "DUE_TODAY");
        List<JobApplicationVo> thisWeek = service.actionItems(userId, 20, "THIS_WEEK");
        List<JobApplicationVo> expiredDeadline = service.actionItems(userId, 20, "EXPIRED_DEADLINE");
        List<JobApplicationVo> unknownDeadline = service.actionItems(userId, 20, "UNKNOWN_DEADLINE");
        List<JobApplicationVo> staleSubmitted = service.actionItems(userId, 20, "STALE_SUBMITTED");
        List<JobApplicationVo> processNeedsFollowUp = service.actionItems(userId, 20, "PROCESS_NEEDS_FOLLOW_UP");
        List<JobApplicationVo> priorityA = service.actionItems(userId, 20, "A");

        assertThat(dueToday).extracting(JobApplicationVo::getCompanyName).containsExactly("Alpha");
        assertThat(thisWeek).extracting(JobApplicationVo::getCompanyName).containsExactly("Gamma");
        assertThat(expiredDeadline).extracting(JobApplicationVo::getCompanyName).containsExactly("Zeta");
        assertThat(unknownDeadline).extracting(JobApplicationVo::getCompanyName).containsExactly("Beta", "Delta");
        assertThat(staleSubmitted).extracting(JobApplicationVo::getCompanyName).containsExactly("Beta");
        assertThat(processNeedsFollowUp).extracting(JobApplicationVo::getCompanyName).containsExactly("Sigma");
        assertThat(priorityA).extracting(JobApplicationVo::getCompanyName).containsExactly("Alpha");
    }

    @Test
    void reviewSummarizesWeeklyApplicationProgress() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationService service = newService(applicationRepository);
        Long userId = 7L;
        Date thisWeek = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date older = Date.from(LocalDate.now().minusDays(14).atStartOfDay(ZoneId.systemDefault()).toInstant());
        when(applicationRepository.findByUserIdAndStateNot(userId, -1)).thenReturn(List.of(
                base(userId, JobApplicationStatusEnum.PREPARING)
                        .setCompanyName("Alpha")
                        .setCreateTime(thisWeek)
                        .setUpdateTime(thisWeek),
                base(userId, JobApplicationStatusEnum.SUBMITTED)
                        .setCompanyName("Beta")
                        .setCreateTime(older)
                        .setUpdateTime(thisWeek)
                        .setSubmittedAt(older),
                base(userId, JobApplicationStatusEnum.INTERVIEW_1)
                        .setCompanyName("Gamma")
                        .setCreateTime(older)
                        .setUpdateTime(thisWeek),
                base(userId, JobApplicationStatusEnum.OFFER)
                        .setCompanyName("Sigma")
                        .setCreateTime(older)
                        .setUpdateTime(thisWeek),
                base(userId, JobApplicationStatusEnum.SUBMITTED)
                        .setCompanyName("Delta")
                        .setCreateTime(older)
                        .setUpdateTime(older)
                        .setSubmittedAt(older)
        ));

        JobApplicationReviewVo review = service.review(userId);

        assertThat(review.getTotal()).isEqualTo(5);
        assertThat(review.getCreatedThisWeek()).isEqualTo(1);
        assertThat(review.getSubmittedAndLaterThisWeek()).isEqualTo(3);
        assertThat(review.getInterviewThisWeek()).isEqualTo(1);
        assertThat(review.getOfferThisWeek()).isEqualTo(1);
        assertThat(review.getStaleSubmitted()).isEqualTo(2);
        assertThat(review.getProcessNeedsFollowUp()).isEqualTo(2);
        assertThat(review.getExpiredDeadline()).isZero();
        assertThat(review.getUnknownDeadline()).isEqualTo(5);
        assertThat(review.getWeekStart()).isNotNull();
        assertThat(review.getWeekEnd()).isNotNull();
        assertThat(review.getSummary()).contains("投递超过 7 天未跟进");
    }

    @Test
    void reviewSummarizesDeadlineHygieneRisks() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationService service = newService(applicationRepository);
        Long userId = 7L;
        when(applicationRepository.findByUserIdAndStateNot(userId, -1)).thenReturn(List.of(
                base(userId, JobApplicationStatusEnum.PREPARING)
                        .setCompanyName("Zeta")
                        .setPosition("Data")
                        .setDeadline(LocalDate.now().minusDays(1).toString()),
                base(userId, JobApplicationStatusEnum.PREPARING)
                        .setCompanyName("Eta")
                        .setPosition("Backend")
        ));

        JobApplicationReviewVo review = service.review(userId);

        assertThat(review.getExpiredDeadline()).isEqualTo(1);
        assertThat(review.getUnknownDeadline()).isEqualTo(1);
        assertThat(review.getSummary()).contains("已过截止时间");
    }

    @Test
    void detailExposesNextKeyEvent() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationEventRepository eventRepository = mock(JobApplicationEventRepository.class);
        JobApplicationService service = newService(applicationRepository, eventRepository);
        Long userId = 7L;
        JobApplicationEntity entity = base(userId, JobApplicationStatusEnum.INTERVIEW_1)
                .setId(201L)
                .setCompanyName("Gamma")
                .setPosition("Server engineer");
        when(applicationRepository.findById(201L)).thenReturn(Optional.of(entity));
        when(applicationRepository.findByUserIdAndIdInAndStateNot(any(), any(), any())).thenReturn(List.of(entity));
        when(eventRepository.findByApplicationIdAndUserIdOrderByEventTimeAsc(201L, userId)).thenReturn(List.of(
                new JobApplicationEventEntity()
                        .setId(21L)
                        .setApplicationId(201L)
                        .setUserId(userId)
                        .setEventType("INTERVIEW")
                        .setEventTitle("past interview")
                        .setEventTime(plusDays(new Date(), -1)),
                new JobApplicationEventEntity()
                        .setId(22L)
                        .setApplicationId(201L)
                        .setUserId(userId)
                        .setEventType("INTERVIEW")
                        .setEventTitle("next interview")
                        .setEventTime(plusDays(new Date(), 2)),
                new JobApplicationEventEntity()
                        .setId(23L)
                        .setApplicationId(201L)
                        .setUserId(userId)
                        .setEventType("OFFER")
                        .setEventTitle("offer discussion")
                        .setEventTime(plusDays(new Date(), 4))
        ));

        JobApplicationVo detail = service.detail(userId, 201L);

        assertThat(detail.getEvents()).hasSize(3);
        assertThat(detail.getNextKeyEvent()).isNotNull();
        assertThat(detail.getNextKeyEvent().getEventTitle()).isEqualTo("next interview");
        assertThat(detail.getNextKeyEvent().getCompanyName()).isEqualTo("Gamma");
        assertThat(detail.getNextKeyEvent().getSuggestedPreparation()).contains("面试时间");
    }

    @Test
    void saveSubmittedApplicationCreatesDefaultFollowUpReminder() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationService service = newService(applicationRepository);
        Date submittedAt = atStart("2026-07-01");
        when(applicationRepository.saveAndFlush(any(JobApplicationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplicationSaveReq req = new JobApplicationSaveReq();
        req.setCompanyName("Alpha");
        req.setPosition("Java backend");
        req.setCurrentStatus(JobApplicationStatusEnum.SUBMITTED.getCode());
        req.setSubmittedAt(submittedAt);

        service.save(7L, req);

        ArgumentCaptor<JobApplicationEntity> captor = ArgumentCaptor.forClass(JobApplicationEntity.class);
        verify(applicationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSubmittedAt()).isEqualTo(submittedAt);
        assertThat(captor.getValue().getNextFollowUpAt()).isEqualTo(atStart("2026-07-08"));
    }

    @Test
    void changeStatusToSubmittedCreatesDefaultFollowUpReminder() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationService service = newService(applicationRepository);
        JobApplicationEntity entity = base(7L, JobApplicationStatusEnum.PREPARING)
                .setId(99L)
                .setSubmittedAt(atStart("2026-07-01"));
        when(applicationRepository.findById(99L)).thenReturn(Optional.of(entity));
        when(applicationRepository.saveAndFlush(any(JobApplicationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplicationStatusUpdateReq req = new JobApplicationStatusUpdateReq();
        req.setId(99L);
        req.setTargetStatus(JobApplicationStatusEnum.SUBMITTED.getCode());

        service.changeStatus(7L, req);

        ArgumentCaptor<JobApplicationEntity> captor = ArgumentCaptor.forClass(JobApplicationEntity.class);
        verify(applicationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCurrentStatus()).isEqualTo(JobApplicationStatusEnum.SUBMITTED.getCode());
        assertThat(captor.getValue().getNextFollowUpAt()).isEqualTo(atStart("2026-07-08"));
    }

    @Test
    void changeStatusToInterviewCreatesShortDefaultFollowUpReminder() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationService service = newService(applicationRepository);
        JobApplicationEntity entity = base(7L, JobApplicationStatusEnum.SUBMITTED)
                .setId(102L)
                .setSubmittedAt(atStart("2026-07-01"));
        when(applicationRepository.findById(102L)).thenReturn(Optional.of(entity));
        when(applicationRepository.saveAndFlush(any(JobApplicationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplicationStatusUpdateReq req = new JobApplicationStatusUpdateReq();
        req.setId(102L);
        req.setTargetStatus(JobApplicationStatusEnum.INTERVIEW_1.getCode());
        Date before = new Date();

        service.changeStatus(7L, req);

        Date after = new Date();
        ArgumentCaptor<JobApplicationEntity> captor = ArgumentCaptor.forClass(JobApplicationEntity.class);
        verify(applicationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCurrentStatus()).isEqualTo(JobApplicationStatusEnum.INTERVIEW_1.getCode());
        assertThat(captor.getValue().getNextFollowUpAt())
                .isBetween(plusDays(before, 3), plusDaysWithGrace(after, 3));
    }

    @Test
    void changeStatusToOfferCreatesShorterDefaultFollowUpReminder() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationService service = newService(applicationRepository);
        JobApplicationEntity entity = base(7L, JobApplicationStatusEnum.HR_INTERVIEW)
                .setId(103L)
                .setSubmittedAt(atStart("2026-07-01"));
        when(applicationRepository.findById(103L)).thenReturn(Optional.of(entity));
        when(applicationRepository.saveAndFlush(any(JobApplicationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplicationStatusUpdateReq req = new JobApplicationStatusUpdateReq();
        req.setId(103L);
        req.setTargetStatus(JobApplicationStatusEnum.OFFER.getCode());
        Date before = new Date();

        service.changeStatus(7L, req);

        Date after = new Date();
        ArgumentCaptor<JobApplicationEntity> captor = ArgumentCaptor.forClass(JobApplicationEntity.class);
        verify(applicationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCurrentStatus()).isEqualTo(JobApplicationStatusEnum.OFFER.getCode());
        assertThat(captor.getValue().getNextFollowUpAt())
                .isBetween(plusDays(before, 2), plusDaysWithGrace(after, 2));
    }

    @Test
    void changeStatusToInterviewKeepsExistingFollowUpReminder() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationService service = newService(applicationRepository);
        Date explicitFollowUp = plusDays(new Date(), 10);
        JobApplicationEntity entity = base(7L, JobApplicationStatusEnum.SUBMITTED)
                .setId(104L)
                .setSubmittedAt(atStart("2026-07-01"))
                .setNextFollowUpAt(explicitFollowUp);
        when(applicationRepository.findById(104L)).thenReturn(Optional.of(entity));
        when(applicationRepository.saveAndFlush(any(JobApplicationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplicationStatusUpdateReq req = new JobApplicationStatusUpdateReq();
        req.setId(104L);
        req.setTargetStatus(JobApplicationStatusEnum.INTERVIEW_1.getCode());

        service.changeStatus(7L, req);

        ArgumentCaptor<JobApplicationEntity> captor = ArgumentCaptor.forClass(JobApplicationEntity.class);
        verify(applicationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNextFollowUpAt()).isEqualTo(explicitFollowUp);
    }

    @Test
    void changeStatusToInterviewRefreshesExpiredFollowUpReminder() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationService service = newService(applicationRepository);
        JobApplicationEntity entity = base(7L, JobApplicationStatusEnum.SUBMITTED)
                .setId(105L)
                .setSubmittedAt(atStart("2026-07-01"))
                .setNextFollowUpAt(atStart("2020-01-01"));
        when(applicationRepository.findById(105L)).thenReturn(Optional.of(entity));
        when(applicationRepository.saveAndFlush(any(JobApplicationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplicationStatusUpdateReq req = new JobApplicationStatusUpdateReq();
        req.setId(105L);
        req.setTargetStatus(JobApplicationStatusEnum.INTERVIEW_1.getCode());
        Date before = new Date();

        service.changeStatus(7L, req);

        Date after = new Date();
        ArgumentCaptor<JobApplicationEntity> captor = ArgumentCaptor.forClass(JobApplicationEntity.class);
        verify(applicationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNextFollowUpAt())
                .isBetween(plusDays(before, 3), plusDaysWithGrace(after, 3));
    }

    @Test
    void addInterviewEventCreatesFollowUpReminderAfterEvent() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationEventRepository eventRepository = mock(JobApplicationEventRepository.class);
        JobApplicationService service = newService(applicationRepository, eventRepository);
        JobApplicationEntity entity = base(7L, JobApplicationStatusEnum.INTERVIEW_1)
                .setId(106L)
                .setSubmittedAt(atStart("2026-07-01"));
        when(applicationRepository.findById(106L)).thenReturn(Optional.of(entity));
        when(applicationRepository.saveAndFlush(any(JobApplicationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.saveAndFlush(any(JobApplicationEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Date eventTime = plusDays(new Date(), 5);

        JobApplicationEventSaveReq req = new JobApplicationEventSaveReq();
        req.setApplicationId(106L);
        req.setEventType("INTERVIEW");
        req.setEventTitle("interview scheduled");
        req.setEventTime(eventTime);

        service.addEvent(7L, req);

        ArgumentCaptor<JobApplicationEntity> captor = ArgumentCaptor.forClass(JobApplicationEntity.class);
        verify(applicationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNextFollowUpAt()).isEqualTo(plusDays(eventTime, 1));
    }

    @Test
    void addInterviewEventAdvancesSubmittedApplicationToFirstInterview() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationEventRepository eventRepository = mock(JobApplicationEventRepository.class);
        JobApplicationStatusLogRepository statusLogRepository = mock(JobApplicationStatusLogRepository.class);
        JobApplicationService service = new JobApplicationService(
                applicationRepository,
                statusLogRepository,
                eventRepository,
                mock(OcRepository.class)
        );
        JobApplicationEntity entity = base(7L, JobApplicationStatusEnum.SUBMITTED)
                .setId(108L)
                .setSubmittedAt(atStart("2026-07-01"));
        when(applicationRepository.findById(108L)).thenReturn(Optional.of(entity));
        when(applicationRepository.saveAndFlush(any(JobApplicationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.saveAndFlush(any(JobApplicationEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplicationEventSaveReq req = new JobApplicationEventSaveReq();
        req.setApplicationId(108L);
        req.setEventType("INTERVIEW");
        req.setEventTitle("interview scheduled");
        req.setEventTime(plusDays(new Date(), 5));

        service.addEvent(7L, req);

        ArgumentCaptor<JobApplicationEntity> applicationCaptor = ArgumentCaptor.forClass(JobApplicationEntity.class);
        verify(applicationRepository).saveAndFlush(applicationCaptor.capture());
        assertThat(applicationCaptor.getValue().getCurrentStatus()).isEqualTo(JobApplicationStatusEnum.INTERVIEW_1.getCode());

        ArgumentCaptor<JobApplicationStatusLogEntity> logCaptor = ArgumentCaptor.forClass(JobApplicationStatusLogEntity.class);
        verify(statusLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getFromStatus()).isEqualTo(JobApplicationStatusEnum.SUBMITTED.getCode());
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo(JobApplicationStatusEnum.INTERVIEW_1.getCode());
    }

    @Test
    void addEventKeepsExistingFutureFollowUpReminder() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationEventRepository eventRepository = mock(JobApplicationEventRepository.class);
        JobApplicationService service = newService(applicationRepository, eventRepository);
        Date explicitFollowUp = plusDays(new Date(), 10);
        JobApplicationEntity entity = base(7L, JobApplicationStatusEnum.INTERVIEW_1)
                .setId(107L)
                .setSubmittedAt(atStart("2026-07-01"))
                .setNextFollowUpAt(explicitFollowUp);
        when(applicationRepository.findById(107L)).thenReturn(Optional.of(entity));
        when(eventRepository.saveAndFlush(any(JobApplicationEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplicationEventSaveReq req = new JobApplicationEventSaveReq();
        req.setApplicationId(107L);
        req.setEventType("INTERVIEW");
        req.setEventTitle("second interview scheduled");
        req.setEventTime(plusDays(new Date(), 5));

        service.addEvent(7L, req);

        verify(applicationRepository, never()).saveAndFlush(any(JobApplicationEntity.class));
        assertThat(entity.getNextFollowUpAt()).isEqualTo(explicitFollowUp);
    }

    @Test
    void completeFollowUpDefaultsNextReminderWhenMissing() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationEventRepository eventRepository = mock(JobApplicationEventRepository.class);
        JobApplicationService service = newService(applicationRepository, eventRepository);
        JobApplicationEntity entity = base(7L, JobApplicationStatusEnum.SUBMITTED)
                .setId(100L)
                .setSubmittedAt(atStart("2026-07-01"))
                .setNextFollowUpAt(atStart("2026-07-07"));
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(entity));
        when(applicationRepository.saveAndFlush(any(JobApplicationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplicationFollowUpReq req = new JobApplicationFollowUpReq();
        req.setId(100L);
        req.setNote("followed up by email");
        Date before = new Date();

        service.completeFollowUp(7L, req);

        Date after = new Date();
        ArgumentCaptor<JobApplicationEntity> captor = ArgumentCaptor.forClass(JobApplicationEntity.class);
        verify(applicationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNextFollowUpAt())
                .isBetween(plusDays(before, 7), plusDaysWithGrace(after, 7));

        ArgumentCaptor<JobApplicationEventEntity> eventCaptor = ArgumentCaptor.forClass(JobApplicationEventEntity.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getApplicationId()).isEqualTo(100L);
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo(7L);
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("FOLLOW_UP");
        assertThat(eventCaptor.getValue().getEventResult()).isEqualTo("DONE");
        assertThat(eventCaptor.getValue().getNote()).isEqualTo("followed up by email");
    }

    @Test
    void completeFollowUpKeepsExplicitNextReminder() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationService service = newService(applicationRepository);
        JobApplicationEntity entity = base(7L, JobApplicationStatusEnum.SUBMITTED)
                .setId(101L)
                .setSubmittedAt(atStart("2026-07-01"))
                .setNextFollowUpAt(atStart("2026-07-07"));
        when(applicationRepository.findById(101L)).thenReturn(Optional.of(entity));
        when(applicationRepository.saveAndFlush(any(JobApplicationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplicationFollowUpReq req = new JobApplicationFollowUpReq();
        req.setId(101L);
        req.setNextFollowUpAt(atStart("2026-07-20"));

        service.completeFollowUp(7L, req);

        ArgumentCaptor<JobApplicationEntity> captor = ArgumentCaptor.forClass(JobApplicationEntity.class);
        verify(applicationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNextFollowUpAt()).isEqualTo(atStart("2026-07-20"));
    }

    @Test
    void completeFollowUpRejectsTerminalApplications() {
        JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
        JobApplicationEventRepository eventRepository = mock(JobApplicationEventRepository.class);
        JobApplicationService service = newService(applicationRepository, eventRepository);
        JobApplicationEntity entity = base(7L, JobApplicationStatusEnum.REJECTED)
                .setId(109L)
                .setSubmittedAt(atStart("2026-07-01"));
        when(applicationRepository.findById(109L)).thenReturn(Optional.of(entity));

        JobApplicationFollowUpReq req = new JobApplicationFollowUpReq();
        req.setId(109L);

        assertThatThrownBy(() -> service.completeFollowUp(7L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Terminal application cannot be followed up");
        verify(applicationRepository, never()).saveAndFlush(any(JobApplicationEntity.class));
        verify(eventRepository, never()).save(any(JobApplicationEventEntity.class));
    }

    private static JobApplicationService newService(JobApplicationRepository applicationRepository) {
        return newService(applicationRepository, mock(JobApplicationEventRepository.class));
    }

    private static JobApplicationService newService(JobApplicationRepository applicationRepository,
                                                    JobApplicationEventRepository eventRepository) {
        return new JobApplicationService(
                applicationRepository,
                mock(JobApplicationStatusLogRepository.class),
                eventRepository,
                mock(OcRepository.class)
        );
    }

    private static JobApplicationEntity base(Long userId, JobApplicationStatusEnum status) {
        return new JobApplicationEntity()
                .setUserId(userId)
                .setCurrentStatus(status.getCode())
                .setState(1)
                .setPriority(0)
                .setCreateTime(new Date())
                .setUpdateTime(new Date());
    }

    private static Date atStart(String date) {
        return Date.from(LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Date plusDays(Date date, int days) {
        return Date.from(date.toInstant().plus(days, ChronoUnit.DAYS));
    }

    private static Date plusDaysWithGrace(Date date, int days) {
        return new Date(plusDays(date, days).getTime() + 1000);
    }
}
