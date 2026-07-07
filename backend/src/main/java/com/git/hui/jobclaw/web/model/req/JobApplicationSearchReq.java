package com.git.hui.jobclaw.web.model.req;

import com.git.hui.jobclaw.core.apis.PageReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class JobApplicationSearchReq extends PageReq {
    private Long jobId;
    private String currentStatus;
    private String companyName;
    private String position;
    private String companyType;
    private String followUpScope;
    private Integer priority;
}
