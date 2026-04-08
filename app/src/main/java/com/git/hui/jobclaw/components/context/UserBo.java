package com.git.hui.jobclaw.components.context;

import com.git.hui.jobclaw.constants.user.permission.UserRoleEnum;

/**
 * 用户业务对象
 *
 * @author YiHui
 * @date 2025/7/15
 */
public record UserBo(Long userId, String nickName, String avatar, UserRoleEnum role) {
}
