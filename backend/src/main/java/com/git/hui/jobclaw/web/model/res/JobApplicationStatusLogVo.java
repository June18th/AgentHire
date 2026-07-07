package com.git.hui.jobclaw.web.model.res;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class JobApplicationStatusLogVo {
    private Long id;
    private Long applicationId;
    private String fromStatus;
    private String toStatus;
    private String operatorType;
    private Long operatorId;
    private String reason;
    private Long eventTime;
}
