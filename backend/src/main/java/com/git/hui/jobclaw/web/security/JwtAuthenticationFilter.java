package com.git.hui.jobclaw.web.security;

import com.git.hui.jobclaw.constants.user.LoginConstants;
import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.context.UserBo;
import com.git.hui.jobclaw.user.helper.SessionHelper;
import com.git.hui.jobclaw.user.service.RbacService;
import com.git.hui.jobclaw.user.service.UserService;
import com.git.hui.jobclaw.util.SessionUtil;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final SessionHelper sessionHelper;
    private final UserService userService;
    private final RbacService rbacService;

    public JwtAuthenticationFilter(SessionHelper sessionHelper, UserService userService, RbacService rbacService) {
        this.sessionHelper = sessionHelper;
        this.userService = userService;
        this.rbacService = rbacService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.isNotBlank(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            Long userId = sessionHelper.getUserIdBySession(token);
            if (userId != null) {
                UserBo user = userService.getUserBo(userId);
                if (user != null) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user, token, rbacService.buildAuthorities(user));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    fillReqInfo(token, user);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader(LoginConstants.TOKEN_KEY);
        if (StringUtils.isNotBlank(token)) {
            return token;
        }

        String auth = request.getHeader("Authorization");
        if (StringUtils.isNotBlank(auth) && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }

        Cookie cookie = SessionUtil.findCookieByName(request, LoginConstants.SESSION_KEY);
        return cookie == null ? null : cookie.getValue();
    }

    private void fillReqInfo(String token, UserBo user) {
        ReqInfoContext.ReqInfo reqInfo = ReqInfoContext.getReqInfo();
        if (reqInfo == null) {
            return;
        }
        reqInfo.setSession(token);
        reqInfo.setUserId(user.userId());
        reqInfo.setUser(user);
    }
}
