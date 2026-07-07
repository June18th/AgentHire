package com.git.hui.jobclaw.web.hook.filter;

import com.git.hui.jobclaw.configs.RateLimitProperties;
import com.git.hui.jobclaw.configs.RedisProperties;
import com.git.hui.jobclaw.core.apis.ResVo;
import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import com.git.hui.jobclaw.util.IpUtil;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private final RateLimitProperties rateLimitProperties;
    private final RedisProperties redisProperties;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Map<String, LocalCounter> localCounters = new ConcurrentHashMap<>();

    public ApiRateLimitFilter(RateLimitProperties rateLimitProperties,
                              RedisProperties redisProperties,
                              ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.rateLimitProperties = rateLimitProperties;
        this.redisProperties = redisProperties;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!rateLimitProperties.isEnabled() || !uri.startsWith("/api/")) {
            return true;
        }
        return rateLimitProperties.getExcludedPaths().stream().anyMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        LimitSubject subject = subject(request);
        int limit = limitFor(subject);
        long count = increment(subject.key());

        if (count > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(rateLimitProperties.getWindowSeconds()));
            response.getWriter().println(JsonUtil.toStr(ResVo.fail(
                    StatusEnum.RATE_LIMITED.getCode(),
                    "请求过于频繁，请稍后再试")));
            response.getWriter().flush();
            return;
        }

        filterChain.doFilter(request, response);
    }

    private LimitSubject subject(HttpServletRequest request) {
        ReqInfoContext.ReqInfo reqInfo = ReqInfoContext.getReqInfo();
        long window = Instant.now().getEpochSecond() / Math.max(1, rateLimitProperties.getWindowSeconds());
        if (reqInfo != null && reqInfo.getUserId() != null) {
            return new LimitSubject("user:" + reqInfo.getUserId() + ":" + window, reqInfo.getUser() == null ? null : reqInfo.getUser().role());
        }

        String deviceId = reqInfo == null ? null : reqInfo.getDeviceId();
        if (StringUtils.isNotBlank(deviceId)) {
            return new LimitSubject("device:" + deviceId + ":" + window, null);
        }
        return new LimitSubject("ip:" + IpUtil.getClientIp(request) + ":" + window, null);
    }

    private int limitFor(LimitSubject subject) {
        if (subject.role() == UserRoleEnum.ADMIN) {
            return rateLimitProperties.getAdminLimit();
        }
        if (subject.role() == UserRoleEnum.NORMAL || subject.role() == UserRoleEnum.VIP) {
            return rateLimitProperties.getUserLimit();
        }
        return rateLimitProperties.getAnonymousLimit();
    }

    private long increment(String suffix) {
        if (redisProperties.isEnabled()) {
            try {
                StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
                if (redisTemplate == null) {
                    return incrementLocal(suffix);
                }
                String key = redisProperties.getKeyPrefix() + ":rate-limit:" + suffix;
                Long count = redisTemplate.opsForValue().increment(key);
                if (Objects.equals(count, 1L)) {
                    redisTemplate.expire(key, Duration.ofSeconds(Math.max(1, rateLimitProperties.getWindowSeconds())));
                }
                return count == null ? incrementLocal(suffix) : count;
            } catch (Exception e) {
                log.warn("Redis API rate limit failed, fallback to local memory", e);
            }
        }
        return incrementLocal(suffix);
    }

    private long incrementLocal(String suffix) {
        long now = Instant.now().getEpochSecond();
        long windowSeconds = Math.max(1, rateLimitProperties.getWindowSeconds());
        localCounters.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > windowSeconds * 2);
        return localCounters.computeIfAbsent(suffix, ignored -> new LocalCounter(now))
                .count()
                .incrementAndGet();
    }

    private record LimitSubject(String key, UserRoleEnum role) {
    }

    private record LocalCounter(long createdAt, AtomicInteger count) {
        LocalCounter(long createdAt) {
            this(createdAt, new AtomicInteger());
        }
    }
}
