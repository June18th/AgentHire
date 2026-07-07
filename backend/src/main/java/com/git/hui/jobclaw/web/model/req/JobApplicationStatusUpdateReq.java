package com.git.hui.jobclaw.web.model.req;

import lombok.Data;

@Data
public class JobApplicationStatusUpdateReq {
    private Long id;
    private String targetStatus;
    private String reason;
}
