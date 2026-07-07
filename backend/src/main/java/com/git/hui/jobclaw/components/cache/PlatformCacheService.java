package com.git.hui.jobclaw.components.cache;

import com.git.hui.jobclaw.configs.RedisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class PlatformCacheService {
    private final RedisProperties redisProperties;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final AtomicInteger localOnlineUserCnt = new AtomicInteger();

    public PlatformCacheService(RedisProperties redisProperties, ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisProperties = redisProperties;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public int incrOnlineUserCnt(int add) {
        if (!redisProperties.isEnabled()) {
            return localOnlineUserCnt.addAndGet(add);
        }
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null) {
                return localOnlineUserCnt.addAndGet(add);
            }
            Long value = redisTemplate.opsForValue().increment(key("online:user:count"), add);
            return value == null ? localOnlineUserCnt.addAndGet(add) : value.intValue();
        } catch (Exception e) {
            log.warn("Redis online user counter failed, fallback to local memory", e);
            return localOnlineUserCnt.addAndGet(add);
        }
    }

    public int getOnlineUserCnt() {
        if (!redisProperties.isEnabled()) {
            return localOnlineUserCnt.get();
        }
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null) {
                return localOnlineUserCnt.get();
            }
            String value = redisTemplate.opsForValue().get(key("online:user:count"));
            return value == null ? 0 : Integer.parseInt(value);
        } catch (Exception e) {
            log.warn("Redis online user counter read failed, fallback to local memory", e);
            return localOnlineUserCnt.get();
        }
    }

    private String key(String suffix) {
        return redisProperties.getKeyPrefix() + ":" + suffix;
    }
}
