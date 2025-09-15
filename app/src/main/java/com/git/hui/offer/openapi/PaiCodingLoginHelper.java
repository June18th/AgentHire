package com.git.hui.offer.openapi;

import com.git.hui.offer.components.context.ReqInfoContext;
import com.git.hui.offer.components.context.UserBo;
import com.git.hui.offer.constants.user.LoginConstants;
import com.git.hui.offer.openapi.client.OpenApiSilentLoginClient;
import com.git.hui.offer.openapi.model.OpenApiUserDTO;
import com.git.hui.offer.user.helper.SessionHelper;
import com.git.hui.offer.user.service.UserService;
import com.git.hui.offer.util.SessionUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 通过技术派登录服务
 *
 * @author YiHui
 * @date 2025/9/15
 */
@Component
public class PaiCodingLoginHelper {
    private static final String PAI_CODING_TOKEN_NAME = "f-session";

    @Autowired
    private OpenApiSilentLoginClient openApiSilentLoginClient;

    @Autowired
    private SessionHelper sessionHelper;

    @Autowired
    private UserService userService;


    /**
     * 从cookie或者请求头中，获取技术派的登录信息
     *
     * @param request
     * @return
     */
    public UserBo loginByPaiCoding(HttpServletRequest request, HttpServletResponse response) {
        if (request.getRequestURI().contains("logout")) {
            return null;
        }

        // 从cookie获取登录信息，适用于一级域名相同的场景
        Cookie ck = SessionUtil.findCookieByName(request, PAI_CODING_TOKEN_NAME);
        if (ck != null) {
            return this.loginByPaiCoding(ck.getValue(), response);
        }


        // 从请求头中获取，适用于iframe嵌入请求方式、域名不同的场景
        String session = request.getHeader(PAI_CODING_TOKEN_NAME);
        if (StringUtils.isNotBlank(session)) {
            return loginByPaiCoding(session, response);
        }

        // 从请求参数中获取，适用于iframe嵌入请求方式、域名不同、外部授权的场景
        session = request.getParameter(PAI_CODING_TOKEN_NAME);
        if (StringUtils.isNotBlank(session)) {
            return loginByPaiCoding(session, response);
        }

        // 未登录
        return null;
    }

    public UserBo loginByPaiCoding(String session, HttpServletResponse response) {
        if (StringUtils.isBlank(session)) {
            return null;
        }
        OpenApiUserDTO openUser = openApiSilentLoginClient.loginBySession(session);
        if (openUser == null) {
            return null;
        }

        UserBo user;
        // 如果技术派的用户，有wxid，则基于wxid来登录
        if (StringUtils.isNotBlank(openUser.getWxId())) {
            user = userService.autoRegisterWxUserInfo(openUser.getWxId());
        } else if (StringUtils.isNotBlank(openUser.getZsxqId()) && openUser.getLoginName().startsWith("zsxq_")) {
            // 基于知识星球用户的登录，同样使用用户名方式进行匹配
            // fixme 对于星球登录，需要判断，是否需要同步会员时间
            user = userService.autoRegisterPaiCodingUserInfo(openUser);
        } else {
            // 普通的用户名密码登录方式，统一给ai-oc的用户添加前缀 pai_
            openUser.setLoginName("pai_" + openUser.getLoginName());
            user = userService.autoRegisterPaiCodingUserInfo(openUser);
        }

        // 自动生成 ai-oc 的 session，写入用户登录信息，用于身份识别
        String ocSession = sessionHelper.genSession(user);
        response.addCookie(SessionUtil.newCookie(LoginConstants.SESSION_KEY, ocSession));
        ReqInfoContext.getReqInfo().setSession(ocSession);
        return user;
    }
}
