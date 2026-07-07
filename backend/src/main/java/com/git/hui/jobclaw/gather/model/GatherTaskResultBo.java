package com.git.hui.jobclaw.gather.model;

import java.util.Collections;
import java.util.List;

/**
 * @author YiHui
 * @date 2025/7/18
 */
public record GatherTaskResultBo(
        String msg,
        List<Long> insertDraftIds,
        List<Long> updateDraftIds,
        List<Long> unchangedDraftIds,
        List<Long> skipDraftIds,
        List<String> failedItems
) {
    public static final String SUCCESS = "success";

    public GatherTaskResultBo {
        insertDraftIds = safeLongList(insertDraftIds);
        updateDraftIds = safeLongList(updateDraftIds);
        unchangedDraftIds = safeLongList(unchangedDraftIds);
        skipDraftIds = safeLongList(skipDraftIds);
        failedItems = safeStringList(failedItems);
    }

    public GatherTaskResultBo(String msg, List<Long> insertDraftIds, List<Long> updateDraftIds) {
        this(msg, insertDraftIds, updateDraftIds, List.of(), List.of(), List.of());
    }

    public boolean isSuccess() {
        return SUCCESS.equals(msg);
    }

    private static List<Long> safeLongList(List<Long> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private static List<String> safeStringList(List<String> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
