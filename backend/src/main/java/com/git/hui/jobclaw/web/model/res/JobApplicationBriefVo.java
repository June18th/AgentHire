package com.git.hui.jobclaw.web.model.res;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class JobApplicationBriefVo {
    private Integer total;
    private Integer active;
    private Integer actionCount;
    private Integer priorityA;
    private Integer priorityB;
    private Integer priorityC;
    private Integer overdueFollowUps;
    private Integer dueToday;
    private Integer dueSoon;
    private Integer thisWeek;
    private Integer submittedAndLater;
    private Integer staleSubmitted;
    private Integer interview;
    private Integer offer;
    private Integer todayEvents;
    private Integer next7DayEvents;
    private String summary;
    private List<JobApplicationEventVo> upcomingEvents;
    private List<JobApplicationVo> topActions;
}
