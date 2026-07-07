package com.git.hui.jobclaw.application.convert;

import com.git.hui.jobclaw.application.dao.entity.JobApplicationEntity;
import com.git.hui.jobclaw.application.dao.entity.JobApplicationEventEntity;
import com.git.hui.jobclaw.application.dao.entity.JobApplicationStatusLogEntity;
import com.git.hui.jobclaw.constants.application.JobApplicationStatusEnum;
import com.git.hui.jobclaw.web.model.res.JobApplicationEventVo;
import com.git.hui.jobclaw.web.model.res.JobApplicationStatusLogVo;
import com.git.hui.jobclaw.web.model.res.JobApplicationVo;

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
        if (entity == null) {
            return null;
        }
        return new JobApplicationEventVo()
                .setId(entity.getId())
                .setApplicationId(entity.getApplicationId())
                .setEventType(entity.getEventType())
                .setEventTitle(entity.getEventTitle())
                .setEventTime(toTime(entity.getEventTime()))
                .setEventResult(entity.getEventResult())
                .setNote(entity.getNote())
                .setCreateTime(toTime(entity.getCreateTime()));
    }

    public static List<JobApplicationEventVo> toEventVoList(List<JobApplicationEventEntity> list) {
        return Optional.ofNullable(list).orElse(Collections.emptyList()).stream().map(JobApplicationConvert::toVo).toList();
    }

    private static Long toTime(Date date) {
        return date == null ? null : date.getTime();
    }
}
