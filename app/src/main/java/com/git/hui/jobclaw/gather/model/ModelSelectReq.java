package com.git.hui.jobclaw.gather.model;

import com.git.hui.jobclaw.constants.gather.GatherModelEnum;
import com.git.hui.jobclaw.constants.gather.GatherModelTypeEnum;

/**
 * 模型选择器
 *
 * @author YiHui
 * @date 2025/7/30
 */
public record ModelSelectReq(GatherModelEnum mode, GatherModelTypeEnum type) {
    public static ModelSelectReq of(GatherModelEnum mode, GatherModelTypeEnum type) {
        return new ModelSelectReq(mode, type);
    }
}
