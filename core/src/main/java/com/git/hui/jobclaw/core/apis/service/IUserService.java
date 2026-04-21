package com.git.hui.jobclaw.core.apis.service;

import com.git.hui.jobclaw.core.apis.context.UserBo;

/**
 *
 * @author YiHui
 * @date 2026/4/21
 */
public interface IUserService {
    UserBo getUser(String userId);
}
