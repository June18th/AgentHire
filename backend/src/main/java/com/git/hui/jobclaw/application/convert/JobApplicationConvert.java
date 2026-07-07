package com.git.hui.jobclaw.application.convert;

import com.git.hui.jobclaw.application.dao.entity.JobApplicationEntity;
import com.git.hui.jobclaw.application.dao.entity.JobApplicationEventEntity;
import com.git.hui.jobclaw.application.dao.entity.JobApplicationStatusLogEntity;
import com.git.hui.jobclaw.application.service.JobApplicationActionAdvisor;
import com.git.hui.jobclaw.constants.application.JobApplicationStatusEnum;
import com.git.hui.jobclaw.web.model.res.JobApplicationEventVo;
import com.git.hui.jobclaw.web.model.res.JobApplicationStatusLogVo;
import com.git.hui.jobclaw.web.model.res.JobApplicationVo;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class JobApplicationConvert {
    private JobApplicationConvert() {
    }

    public static JobApplicationVo toVo(JobApplicationEntity entity) {
        if (entity == null) {
            return null;
        }
        JobApplicationStatusEnum status = JobApplicationStatusEnum.of(entity.getCurrentStatus());
        JobApplicationActionAdvisor.ActionSignal actionSignal = JobApplicationActionAdvisor.evaluate(entity);
        return new JobApplicationVo()
                .setId(entity.getId())
                .setUserId(entity.getUserId())
                .setJobId(entity.getJobId())
                .setCompanyName(entity.getCompanyName())
                .setPosition(entity.getPosition())
                .setApplyUrl(entity.getApplyUrl())
                .setCompanyType(entity.getCompanyType())
                .setCurrentStatus(entity.getCurrentStatus())
                .setCurrentStatusDesc(status == null ? "" : status.getDesc())
                .setTerminal(status != null && status.isTerminal())
                .setSource(entity.getSource())
                .setPriority(entity.getPriority())
                .setDeadline(entity.getDeadline())
                .setDeadlineAt(actionSignal.deadlineAt())
                .setDaysUntilDeadline(actionSignal.daysUntilDeadline())
                .setDeadlineRisk(actionSignal.deadlineRisk())
                .setFollowUpOverdue(actionSignal.followUpOverdue())
                .setActionPriority(actionSignal.actionPriority())
                .setSuggestedNextAction(actionSignal.suggestedNextAction())
                .setActionReason(actionSignal.actionReason())
                .setSubmittedAt(toTime(entity.getSubmittedAt()))
                .setNextFollowUpAt(toTime(entity.getNextFollowUpAt()))
                .setRemark(entity.getRemark())
                .setState(entity.getState())
                .setCreateTime(toTime(entity.getCreateTime()))
                .setUpdateTime(toTime(entity.getUpdateTime()));
    }

    public static List<JobApplicationVo> toVoList(List<JobApplicationEntity> list) {
        return Optional.ofNullable(list).orElse(Collections.emptyList()).stream().map(JobApplicationConvert::toVo).toList();
    }

    public static JobApplicationStatusLogVo toVo(JobApplicationStatusLogEntity entity) {
        if (entity == null) {
            return null;
        }
        return new JobApplicationStatusLogVo()
                .setId(entity.getId())
                .setApplicationId(entity.getApplicationId())
                .setFromStatus(entity.getFromStatus())
                .setToStatus(entity.getToStatus())
                .setOperatorType(entity.getOperatorType())
                .setOperatorId(entity.getOperatorId())
                .setReason(entity.getReason())
                .setEventTime(toTime(entity.getEventTime()));
    }

    public static List<JobApplicationStatusLogVo> toStatusLogVoList(List<JobApplicationStatusLogEntity> list) {
        return Optional.ofNullable(list).orElse(Collections.emptyList()).stream().map(JobApplicationConvert::toVo).toList();
    }

    public static JobApplicationEventVo toVo(JobApplicationEventEntity entity) {
        return toVo(entity, null);
    }

    public static JobApplicationEventVo toVo(JobApplicationEventEntity entity, JobApplicationEntity application) {
        if (entity == null) {
            return null;
        }
        JobApplicationStatusEnum status = application == null ? null : JobApplicationStatusEnum.of(application.getCurrentStatus());
        JobApplicationEventVo vo = new JobApplicationEventVo()
                .setId(entity.getId())
                .setApplicationId(entity.getApplicationId())
                .setEventType(entity.getEventType())
                .setEventTitle(entity.getEventTitle())
                .setEventTime(toTime(entity.getEventTime()))
                .setHoursUntilEvent(hoursUntil(entity.getEventTime()))
                .setEventUrgency(eventUrgency(entity.getEventTime()))
                .setSuggestedPreparation(suggestedPreparation(entity.getEventType(), entity.getEventTime()))
                .setEventResult(entity.getEventResult())
                .setNote(entity.getNote())
                .setCreateTime(toTime(entity.getCreateTime()));
        if (application != null) {
            vo.setCompanyName(application.getCompanyName())
                    .setPosition(application.getPosition())
                    .setCurrentStatus(application.getCurrentStatus())
                    .setCurrentStatusDesc(status == null ? "" : status.getDesc());
        }
        return vo;
    }

    public static List<JobApplicationEventVo> toEventVoList(List<JobApplicationEventEntity> list) {
        return Optional.ofNullable(list).orElse(Collections.emptyList()).stream().map(JobApplicationConvert::toVo).toList();
    }

    private static Long toTime(Date date) {
        return date == null ? null : date.getTime();
    }

    private static Integer hoursUntil(Date date) {
        if (date == null) {
            return null;
        }
        long hours = ChronoUnit.HOURS.between(new Date().toInstant(), date.toInstant());
        if (hours > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (hours < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) hours;
    }

    private static String eventUrgency(Date date) {
        if (date == null) {
            return "UNKNOWN";
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate eventDate = date.toInstant().atZone(zone).toLocalDate();
        LocalDate today = LocalDate.now(zone);
        if (eventDate.isBefore(today)) {
            return "PAST";
        }
        if (eventDate.isEqual(today)) {
            return "TODAY";
        }
        if (eventDate.isEqual(today.plusDays(1))) {
            return "TOMORROW";
        }
        if (eventDate.isBefore(today.plusDays(8))) {
            return "THIS_WEEK";
        }
        return "LATER";
    }

    private static String suggestedPreparation(String eventType, Date eventTime) {
        if (eventTime != null && eventTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isBefore(LocalDate.now())) {
            return "补录事件结果、复盘关键问题，并确认下一步跟进时间。";
        }
        if ("WRITTEN_TEST".equals(eventType)) {
            return "确认笔试平台、时间、设备网络，复习岗位相关题型并预留提前登录时间。";
        }
        if ("INTERVIEW".equals(eventType)) {
            return "确认面试时间和会议链接，准备项目经历、岗位匹配点、反问问题和简历版本。";
        }
        if ("HR".equals(eventType)) {
            return "准备薪资期望、到岗时间、城市偏好、Offer 对比项和待确认问题。";
        }
        if ("OFFER".equals(eventType)) {
            return "核对薪资结构、试用期、签约截止时间、三方/入职材料和取舍理由。";
        }
        return "确认时间、地点或链接、所需材料，并记录下一步计划。";
    }
}
