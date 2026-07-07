package com.git.hui.jobclaw.web.model.res;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class JobApplicationEventVo {
    private Long id;
    private Long applicationId;
    private String eventType;
    private String eventTitle;
    private Long eventTime;
    private String eventResult;
    private String note;
    private Long createTime;
}
