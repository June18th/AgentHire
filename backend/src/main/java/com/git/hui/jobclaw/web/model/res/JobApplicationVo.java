package com.git.hui.jobclaw.web.model.res;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class JobApplicationVo {
    private Long id;
    private Long userId;
    private Long jobId;
    private String companyName;
    private String position;
    private String applyUrl;
    private String companyType;
    private String currentStatus;
    private String currentStatusDesc;
    private Boolean terminal;
    private String source;
    private Integer priority;
    private String deadline;
    private Long deadlineAt;
    private Integer daysUntilDeadline;
    private String deadlineRisk;
    private Boolean followUpOverdue;
    private String actionPriority;
    private String suggestedNextAction;
    private String actionReason;
    private Long submittedAt;
    private Long nextFollowUpAt;
    private String remark;
    private Integer state;
    private Long createTime;
    private Long updateTime;
    private JobApplicationEventVo nextKeyEvent;
    private List<JobApplicationStatusLogVo> statusLogs;
    private List<JobApplicationEventVo> events;
}
