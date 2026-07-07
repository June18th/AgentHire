package com.git.hui.jobclaw.web.hook.interceptor;

import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.context.UserBo;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCheckInterceptorTest {
    private final PermissionCheckInterceptor interceptor = new PermissionCheckInterceptor();

    @AfterEach
    void tearDown() {
        ReqInfoContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsProtectedApiWhenUserIsNotLoggedIn() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/admin/user/list"),
                response,
                handler("adminApi"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("未登录");
    }

    @Test
    void allowsRbacPlatformAdminForAdminPermission() throws Exception {
        loginAs(new UserBo(1L, "admin", "", UserRoleEnum.NORMAL), "ROLE_PLATFORM_ADMIN");

        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/admin/user/list"),
                new MockHttpServletResponse(),
                handler("adminApi"));

        assertThat(allowed).isTrue();
    }

    @Test
    void rejectsNormalUserForAdminPermission() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        loginAs(new UserBo(2L, "normal", "", UserRoleEnum.NORMAL), "ROLE_JOB_SEEKER");

        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/admin/user/list"),
                response,
                handler("adminApi"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("管理员账号");
    }

    @Test
    void allowsRbacVipUserForVipPermission() throws Exception {
        loginAs(new UserBo(3L, "vip", "", UserRoleEnum.NORMAL), "ROLE_VIP_USER");

        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/vip/demo"),
                new MockHttpServletResponse(),
                handler("vipApi"));

        assertThat(allowed).isTrue();
    }

    @Test
    void allowsAdminForVipPermission() throws Exception {
        loginAs(new UserBo(4L, "admin", "", UserRoleEnum.ADMIN), "ROLE_PLATFORM_ADMIN");

        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/vip/demo"),
                new MockHttpServletResponse(),
                handler("vipApi"));

        assertThat(allowed).isTrue();
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = PermissionController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(new PermissionController(), method);
    }

    private static void loginAs(UserBo user, String... authorities) {
        ReqInfoContext.ReqInfo reqInfo = new ReqInfoContext.ReqInfo();
        reqInfo.setUserId(user.userId());
        reqInfo.setUser(user);
        ReqInfoContext.addReqInfo(reqInfo);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user,
                "test-token",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    private static class PermissionController {
        @Permission(role = UserRoleEnum.ADMIN)
        void adminApi() {
        }

        @Permission(role = UserRoleEnum.VIP)
        void vipApi() {
        }
    }
}
