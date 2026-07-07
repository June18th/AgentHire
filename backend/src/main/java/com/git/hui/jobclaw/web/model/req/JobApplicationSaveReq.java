package com.git.hui.jobclaw.web.model.req;

import lombok.Data;

import java.util.Date;

@Data
public class JobApplicationSaveReq {
    private Long id;
    private Long jobId;
    private String companyName;
    private String position;
    private String applyUrl;
    private String companyType;
    private String currentStatus;
    private String source;
    private Integer priority;
    private String deadline;
    private Date submittedAt;
    private Date nextFollowUpAt;
    private String remark;
}
