package com.git.hui.jobclaw.web.hook.filter;

import com.git.hui.jobclaw.configs.RateLimitProperties;
import com.git.hui.jobclaw.configs.RedisProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ApiRateLimitFilterTest {

    @Test
    void skipsWhenRateLimitIsDisabled() throws Exception {
        ApiRateLimitFilter filter = filter(false, 1);
        AtomicInteger chainCalls = new AtomicInteger();

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/api/chat/send", "10.0.0.1"), response, chain(chainCalls));

        assertThat(chainCalls).hasValue(1);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void skipsConfiguredExcludedPaths() throws Exception {
        ApiRateLimitFilter filter = filter(true, 1);
        AtomicInteger chainCalls = new AtomicInteger();

        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request("/api/wx/dev/login", "10.0.0.2"), first, chain(chainCalls));
        filter.doFilter(request("/api/wx/dev/login", "10.0.0.2"), second, chain(chainCalls));

        assertThat(chainCalls).hasValue(2);
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(200);
    }

    @Test
    void blocksAnonymousApiRequestsAfterLimitIsExceeded() throws Exception {
        ApiRateLimitFilter filter = filter(true, 1);
        AtomicInteger chainCalls = new AtomicInteger();

        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request("/api/chat/send", "10.0.0.3"), first, chain(chainCalls));
        filter.doFilter(request("/api/chat/send", "10.0.0.3"), second, chain(chainCalls));

        assertThat(chainCalls).hasValue(1);
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader("Retry-After")).isEqualTo("60");
        assertThat(second.getContentAsString()).contains("请求过于频繁");
    }

    private static ApiRateLimitFilter filter(boolean enabled, int anonymousLimit) {
        RateLimitProperties rateLimitProperties = new RateLimitProperties();
        rateLimitProperties.setEnabled(enabled);
        rateLimitProperties.setAnonymousLimit(anonymousLimit);
        rateLimitProperties.setWindowSeconds(60);

        RedisProperties redisProperties = new RedisProperties();
        redisProperties.setEnabled(false);
        redisProperties.setKeyPrefix("jobclaw-test");

        return new ApiRateLimitFilter(rateLimitProperties, redisProperties, redisTemplateProvider());
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<StringRedisTemplate> redisTemplateProvider() {
        return mock(ObjectProvider.class);
    }

    private static MockHttpServletRequest request(String uri, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private static FilterChain chain(AtomicInteger calls) {
        return (request, response) -> calls.incrementAndGet();
    }
}
