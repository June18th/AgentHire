package com.git.hui.jobclaw.web.model.res;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class GatherSourceVo {
    private Long id;
    private Integer type;
    private String title;
    private String content;
    private Integer version;
    private String ownerType;
    private String runnerType;
    private String lastModel;
    private String status;
    private Long lastTaskId;
    private String lastResultSummary;
    private Long lastRunTime;
    private Long createTime;
    private Long updateTime;
}
