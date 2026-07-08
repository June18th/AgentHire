package com.git.hui.jobclaw.web.model.res;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class JobApplicationReviewVo {
    private Long weekStart;
    private Long weekEnd;
    private Integer total;
    private Integer createdThisWeek;
    private Integer submittedAndLaterThisWeek;
    private Integer interviewThisWeek;
    private Integer offerThisWeek;
    private Integer overdueFollowUps;
    private Integer staleSubmitted;
    private Integer processNeedsFollowUp;
    private String summary;
}
