package com.git.hui.jobclaw.application.service;

import com.git.hui.jobclaw.application.dao.entity.JobApplicationEntity;
import com.git.hui.jobclaw.constants.application.JobApplicationStatusEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JobApplicationActionAdvisorTest {

    @Test
    void marksDueTodayAsTopPriority() {
        JobApplicationEntity entity = base(JobApplicationStatusEnum.PREPARING)
                .setDeadline("2026-07-07");

        JobApplicationActionAdvisor.ActionSignal signal = JobApplicationActionAdvisor.evaluate(entity, atStart("2026-07-07"));

        assertThat(signal.deadlineRisk()).isEqualTo("DUE_TODAY");
        assertThat(signal.daysUntilDeadline()).isZero();
        assertThat(signal.actionPriority()).isEqualTo("A");
        assertThat(signal.suggestedNextAction()).contains("今天截止");
        assertThat(signal.actionReason()).contains("今天截止");
    }

    @Test
    void parsesChineseDeadlineText() {
        JobApplicationEntity entity = base(JobApplicationStatusEnum.INTERESTED)
                .setDeadline("2026年07月10日 23:59 截止");

        JobApplicationActionAdvisor.ActionSignal signal = JobApplicationActionAdvisor.evaluate(entity, atStart("2026-07-07"));

        assertThat(signal.deadlineRisk()).isEqualTo("DUE_SOON");
        assertThat(signal.daysUntilDeadline()).isEqualTo(3);
        assertThat(signal.actionPriority()).isEqualTo("A");
    }

    @Test
    void infersCurrentYearForMonthDayDeadlineText() {
        JobApplicationEntity entity = base(JobApplicationStatusEnum.INTERESTED)
                .setDeadline("7月10日 23:59 截止");

        JobApplicationActionAdvisor.ActionSignal signal = JobApplicationActionAdvisor.evaluate(entity, atStart("2026-07-07"));

        assertThat(signal.deadlineRisk()).isEqualTo("DUE_SOON");
        assertThat(signal.daysUntilDeadline()).isEqualTo(3);
        assertThat(signal.deadlineAt()).isEqualTo(atStart("2026-07-10").getTime());
    }

    @Test
    void rollsMonthDayDeadlineIntoNextYearWhenItClearlyPassed() {
        JobApplicationEntity entity = base(JobApplicationStatusEnum.INTERESTED)
                .setDeadline("1/5 23:59");

        JobApplicationActionAdvisor.ActionSignal signal = JobApplicationActionAdvisor.evaluate(entity, atStart("2026-12-30"));

        assertThat(signal.deadlineRisk()).isEqualTo("THIS_WEEK");
        assertThat(signal.daysUntilDeadline()).isEqualTo(6);
        assertThat(signal.deadlineAt()).isEqualTo(atStart("2027-01-05").getTime());
    }

    @Test
    void followUpOverdueWinsOverNormalDeadline() {
        JobApplicationEntity entity = base(JobApplicationStatusEnum.SUBMITTED)
                .setDeadline("2026-08-01")
                .setNextFollowUpAt(atStart("2026-07-06"));

        JobApplicationActionAdvisor.ActionSignal signal = JobApplicationActionAdvisor.evaluate(entity, atStart("2026-07-07"));

        assertThat(signal.followUpOverdue()).isTrue();
        assertThat(signal.actionPriority()).isEqualTo("A");
        assertThat(signal.suggestedNextAction()).contains("设置下一次提醒");
        assertThat(signal.actionReason()).contains("跟进时间已到期");
    }

    @Test
    void submittedWithoutFollowUpForAWeekBecomesMediumPriority() {
        JobApplicationEntity entity = base(JobApplicationStatusEnum.SUBMITTED)
                .setSubmittedAt(atStart("2026-06-30"));

        JobApplicationActionAdvisor.ActionSignal signal = JobApplicationActionAdvisor.evaluate(entity, atStart("2026-07-07"));

        assertThat(signal.deadlineRisk()).isEqualTo("UNKNOWN");
        assertThat(signal.actionPriority()).isEqualTo("B");
        assertThat(signal.suggestedNextAction()).contains("超过 7 天未跟进");
        assertThat(signal.actionReason()).contains("已投递 7 天");
    }

    @Test
    void terminalApplicationNeedsNoAction() {
        JobApplicationEntity entity = base(JobApplicationStatusEnum.REJECTED)
                .setDeadline("2026-07-07")
                .setNextFollowUpAt(atStart("2026-07-01"));

        JobApplicationActionAdvisor.ActionSignal signal = JobApplicationActionAdvisor.evaluate(entity, atStart("2026-07-07"));

        assertThat(signal.deadlineRisk()).isEqualTo("NONE");
        assertThat(signal.actionPriority()).isEqualTo("NONE");
        assertThat(signal.followUpOverdue()).isFalse();
        assertThat(signal.suggestedNextAction()).contains("无需继续跟进");
    }

    private static JobApplicationEntity base(JobApplicationStatusEnum status) {
        return new JobApplicationEntity().setCurrentStatus(status.getCode());
    }

    private static Date atStart(String date) {
        return Date.from(LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
