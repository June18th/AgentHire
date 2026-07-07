package com.git.hui.jobclaw.application.service;

import com.git.hui.jobclaw.application.dao.entity.JobApplicationEntity;
import com.git.hui.jobclaw.constants.application.JobApplicationStatusEnum;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JobApplicationActionAdvisor {
    private static final Pattern FULL_DATE_PATTERN = Pattern.compile("(\\d{4})[^0-9]?(\\d{1,2})[^0-9]?(\\d{1,2})");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*(?:月|/|-|\\.)(\\d{1,2})\\s*(?:日|号)?");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private JobApplicationActionAdvisor() {
    }

    public static ActionSignal evaluate(JobApplicationEntity entity) {
        return evaluate(entity, new Date());
    }

    public static ActionSignal evaluate(JobApplicationEntity entity, Date now) {
        if (entity == null) {
            return ActionSignal.empty();
        }
        JobApplicationStatusEnum status = JobApplicationStatusEnum.of(entity.getCurrentStatus());
        boolean terminal = status != null && status.isTerminal();
        LocalDate today = toLocalDate(now);
        LocalDate deadlineDate = parseDeadline(entity.getDeadline(), today);
        Integer daysUntilDeadline = deadlineDate == null ? null : Math.toIntExact(ChronoUnit.DAYS.between(today, deadlineDate));
        String deadlineRisk = deadlineRisk(daysUntilDeadline, terminal);
        boolean followUpOverdue = !terminal && entity.getNextFollowUpAt() != null && !entity.getNextFollowUpAt().after(now);
        Integer daysSinceSubmitted = daysSinceSubmitted(status, entity.getSubmittedAt(), today);
        boolean staleSubmitted = !terminal && status == JobApplicationStatusEnum.SUBMITTED
                && entity.getNextFollowUpAt() == null
                && daysSinceSubmitted != null
                && daysSinceSubmitted >= 7;

        String priority = actionPriority(status, deadlineRisk, followUpOverdue, staleSubmitted);
        String nextAction = nextAction(status, deadlineRisk, followUpOverdue, staleSubmitted);
        String reason = actionReason(deadlineRisk, followUpOverdue, daysUntilDeadline, status, staleSubmitted, daysSinceSubmitted);
        Long deadlineAt = deadlineDate == null ? null : deadlineDate.atStartOfDay(ZONE).toInstant().toEpochMilli();

        return new ActionSignal(deadlineAt, daysUntilDeadline, deadlineRisk, followUpOverdue, priority, nextAction, reason);
    }

    private static LocalDate parseDeadline(String deadline, LocalDate today) {
        if (!StringUtils.hasText(deadline)) {
            return null;
        }
        String value = deadline.trim();
        Matcher matcher = FULL_DATE_PATTERN.matcher(value);
        try {
            if (matcher.find()) {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                return LocalDate.of(year, month, day);
            }
            matcher = MONTH_DAY_PATTERN.matcher(value);
            if (matcher.find()) {
                int month = Integer.parseInt(matcher.group(1));
                int day = Integer.parseInt(matcher.group(2));
                LocalDate inferred = LocalDate.of(today.getYear(), month, day);
                return inferred.isBefore(today.minusDays(7)) ? inferred.plusYears(1) : inferred;
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static LocalDate toLocalDate(Date date) {
        return Instant.ofEpochMilli(date.getTime()).atZone(ZONE).toLocalDate();
    }

    private static Integer daysSinceSubmitted(JobApplicationStatusEnum status, Date submittedAt, LocalDate today) {
        if (status != JobApplicationStatusEnum.SUBMITTED || submittedAt == null) {
            return null;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(toLocalDate(submittedAt), today));
    }

    private static String deadlineRisk(Integer daysUntilDeadline, boolean terminal) {
        if (terminal) {
            return "NONE";
        }
        if (daysUntilDeadline == null) {
            return "UNKNOWN";
        }
        if (daysUntilDeadline < 0) {
            return "EXPIRED";
        }
        if (daysUntilDeadline == 0) {
            return "DUE_TODAY";
        }
        if (daysUntilDeadline <= 3) {
            return "DUE_SOON";
        }
        if (daysUntilDeadline <= 7) {
            return "THIS_WEEK";
        }
        return "NORMAL";
    }

    private static String actionPriority(JobApplicationStatusEnum status, String deadlineRisk, boolean followUpOverdue,
                                         boolean staleSubmitted) {
        if (status != null && status.isTerminal()) {
            return "NONE";
        }
        if (followUpOverdue || "DUE_TODAY".equals(deadlineRisk) || "DUE_SOON".equals(deadlineRisk)) {
            return "A";
        }
        if (staleSubmitted || "EXPIRED".equals(deadlineRisk) || "THIS_WEEK".equals(deadlineRisk)) {
            return "B";
        }
        if (status == JobApplicationStatusEnum.INTERESTED || status == JobApplicationStatusEnum.PREPARING
                || status == JobApplicationStatusEnum.SUBMITTED) {
            return "C";
        }
        return "NONE";
    }

    private static String nextAction(JobApplicationStatusEnum status, String deadlineRisk, boolean followUpOverdue,
                                     boolean staleSubmitted) {
        if (status != null && status.isTerminal()) {
            return "已结束，无需继续跟进。";
        }
        if (followUpOverdue) {
            return "先完成本次跟进，记录沟通结果，并设置下一次提醒。";
        }
        if (staleSubmitted) {
            return "投递已超过 7 天未跟进，检查通知渠道，并设置下一次提醒。";
        }
        if ("EXPIRED".equals(deadlineRisk)) {
            return "确认岗位是否仍开放；若已结束，将状态标记为已截止或放弃。";
        }
        if ("DUE_TODAY".equals(deadlineRisk)) {
            return "今天截止，先确认简历、附件和内推码，完成投递。";
        }
        if ("DUE_SOON".equals(deadlineRisk)) {
            return "临近截止，今天安排材料准备和投递。";
        }
        if (status == JobApplicationStatusEnum.INTERESTED) {
            return "评估岗位匹配度，决定是否进入准备。";
        }
        if (status == JobApplicationStatusEnum.PREPARING) {
            return "补齐定制简历和材料，准备提交。";
        }
        if (status == JobApplicationStatusEnum.SUBMITTED) {
            return "检查通知渠道，设置下一次跟进提醒。";
        }
        if (status == JobApplicationStatusEnum.WRITTEN_TEST || status == JobApplicationStatusEnum.INTERVIEW_1
                || status == JobApplicationStatusEnum.INTERVIEW_2 || status == JobApplicationStatusEnum.HR_INTERVIEW) {
            return "准备笔面试，并记录安排、题目和复盘结果。";
        }
        if (status == JobApplicationStatusEnum.OFFER) {
            return "核对薪资、地点和入职时间，记录选择理由。";
        }
        return "检查当前状态，补充下一步跟进计划。";
    }

    private static String actionReason(String deadlineRisk, boolean followUpOverdue, Integer daysUntilDeadline,
                                       JobApplicationStatusEnum status, boolean staleSubmitted,
                                       Integer daysSinceSubmitted) {
        if (status != null && status.isTerminal()) {
            return "投递流程已结束。";
        }
        if (followUpOverdue) {
            return "下一次跟进时间已到期。";
        }
        if (staleSubmitted && daysSinceSubmitted != null) {
            return "已投递 " + daysSinceSubmitted + " 天，尚未设置下一次跟进。";
        }
        if ("UNKNOWN".equals(deadlineRisk)) {
            return "截止时间尚未规范化。";
        }
        if (daysUntilDeadline != null) {
            if (daysUntilDeadline < 0) {
                return "投递截止时间已过去 " + Math.abs(daysUntilDeadline) + " 天。";
            }
            if (daysUntilDeadline == 0) {
                return "投递今天截止。";
            }
            return "距离投递截止还有 " + daysUntilDeadline + " 天。";
        }
        return "当前状态需要复核。";
    }

    public record ActionSignal(Long deadlineAt,
                               Integer daysUntilDeadline,
                               String deadlineRisk,
                               Boolean followUpOverdue,
                               String actionPriority,
                               String suggestedNextAction,
                               String actionReason) {
        static ActionSignal empty() {
            return new ActionSignal(null, null, "UNKNOWN", false, "NONE", "", "");
        }
    }
}
