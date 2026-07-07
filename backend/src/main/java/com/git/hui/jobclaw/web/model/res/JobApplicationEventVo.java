package com.git.hui.jobclaw.web.model.res;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class JobApplicationEventVo {
    private Long id;
    private Long applicationId;
    private String companyName;
    private String position;
    private String currentStatus;
    private String currentStatusDesc;
    private String eventType;
    private String eventTitle;
    private Long eventTime;
    private Integer hoursUntilEvent;
    private String eventUrgency;
    private String suggestedPreparation;
    private String eventResult;
    private String note;
    private Long createTime;
}
