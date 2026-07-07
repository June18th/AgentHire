package com.git.hui.jobclaw.web.model.req;

import com.git.hui.jobclaw.core.apis.PageReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class GatherSourceSearchReq extends PageReq {
    private Long id;
    private Integer type;
    private String ownerType;
    private String runnerType;
    private String status;
    private String keyword;
}
