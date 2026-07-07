package com.git.hui.jobclaw.web.model.req;

import lombok.Data;

import java.util.Date;

@Data
public class JobApplicationEventSaveReq {
    private Long applicationId;
    private String eventType;
    private String eventTitle;
    private Date eventTime;
    private String eventResult;
    private String note;
}
