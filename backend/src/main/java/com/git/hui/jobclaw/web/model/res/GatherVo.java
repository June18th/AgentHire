package com.git.hui.jobclaw.web.model.res;

import com.git.hui.jobclaw.oc.dao.entity.OcDraftEntity;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;

/**
 * @author YiHui
 * @date 2025/7/18
 */
@Data
@Accessors(chain = true)
public class GatherVo {
    private List<OcDraftEntity> insertList = Collections.emptyList();
    private List<OcDraftEntity> updateList = Collections.emptyList();
    private List<OcDraftEntity> unchangedList = Collections.emptyList();
    private List<OcDraftEntity> skipList = Collections.emptyList();
    private List<String> failedItems = Collections.emptyList();
}
