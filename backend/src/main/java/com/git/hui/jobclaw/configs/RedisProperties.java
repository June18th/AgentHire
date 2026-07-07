package com.git.hui.jobclaw.configs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jobclaw.redis")
public class RedisProperties {
    private boolean enabled;
    private String keyPrefix = "jobclaw";
}
