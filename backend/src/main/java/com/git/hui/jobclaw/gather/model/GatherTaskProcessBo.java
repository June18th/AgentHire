package com.git.hui.jobclaw.gather.model;

import com.git.hui.jobclaw.constants.gather.GatherTargetTypeEnum;

/**
 * @author YiHui
 * @date 2025/7/18
 */
public record GatherTaskProcessBo(
        Long taskId,
        Long sourceId,
        Integer sourceVersion,
        String runnerType,
        GatherTargetTypeEnum type,
        String model,
        String content
) {
}
