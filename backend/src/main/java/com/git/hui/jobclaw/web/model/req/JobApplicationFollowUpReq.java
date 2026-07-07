package com.git.hui.jobclaw.web.model.req;

import lombok.Data;

import java.util.Date;

@Data
public class JobApplicationFollowUpReq {
    private Long id;
    private Date nextFollowUpAt;
    private String note;
}
