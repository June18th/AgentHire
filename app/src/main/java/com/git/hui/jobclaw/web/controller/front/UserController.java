package com.git.hui.jobclaw.web.controller.front;

import com.git.hui.jobclaw.components.context.ReqInfoContext;
import com.git.hui.jobclaw.constants.user.LoginConstants;
import com.git.hui.jobclaw.constants.user.permission.Permission;
import com.git.hui.jobclaw.constants.user.permission.UserRoleEnum;
import com.git.hui.jobclaw.oc.service.OcService;
import com.git.hui.jobclaw.openapi.PaiCodingLoginHelper;
import com.git.hui.jobclaw.user.service.UserInterestService;
import com.git.hui.jobclaw.user.service.UserService;
import com.git.hui.jobclaw.util.SessionUtil;
import com.git.hui.jobclaw.web.model.PageListVo;
import com.git.hui.jobclaw.web.model.req.PageReq;
import com.git.hui.jobclaw.web.model.req.UserInterestRecommendReq;
import com.git.hui.jobclaw.web.model.req.UserSaveReq;
import com.git.hui.jobclaw.web.model.res.OcVo;
import com.git.hui.jobclaw.web.model.res.UserVo;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用户管理
 *
 * @author YiHui
 * @date 2025/7/17
 */
@Permission(role = UserRoleEnum.NORMAL)
@RestController
@RequestMapping(path = "/api/user")
public class UserController {
    private final UserService userService;

    private final UserInterestService userInterestService;

    private final OcService ocService;

    public UserController(UserService userService, UserInterestService userInterestService, OcService ocService) {
        this.userService = userService;
        this.userInterestService = userInterestService;
        this.ocService = ocService;
    }


    /**
     * 更新用户信息
     *
     * @param user 用户id
     * @return
     */
    @RequestMapping(path = "update")
    public UserVo updateUserRole(@RequestBody UserSaveReq user) {
        user.setUserId(ReqInfoContext.getReqInfo().getUserId());
        return userService.updateUserInfo(user);
    }

    /**
     * 用户详情
     *
     * @return
     */
    @RequestMapping(path = "detail")
    public UserVo detail() {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        Assert.notNull(userId, "未登录");
        return userService.detail(userId);
    }

    /**
     * 提交自己的喜好
     *
     * @param text
     * @return
     */
    @RequestMapping(path = "interest")
    public UserVo submitInterest(String text) {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        Assert.notNull(userId, "未登录");
        userInterestService.submitInterest(text);
        return userService.detail(userId);
    }

    /**
     * 基于用户订阅偏好的岗位推荐
     *
     * @param req 分页参数
     * @return
     */
    @RequestMapping(path = "recommend")
    public PageListVo<OcVo> recommend(PageReq req) {
        UserInterestRecommendReq recommendReq = userInterestService.buildInterestRecommendReq(req);
        if (recommendReq == null) {
            // 用户还没有设置订阅偏好的场景下，不做任何推荐
            return PageListVo.emptyVo();
        }
        return ocService.recommendForUser(recommendReq);
    }


    /**
     * 账号登出
     *
     * @return
     */
    @RequestMapping(path = "logout")
    public boolean logout(HttpServletResponse response) {
        // 移除cookie
        response.addCookie(SessionUtil.delCookie(LoginConstants.SESSION_KEY));
        response.addCookie(SessionUtil.delCookie(PaiCodingLoginHelper.PAI_CODING_TOKEN_NAME, "paicoding.com", "/"));
        return true;
    }
}
