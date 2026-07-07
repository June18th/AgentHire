package com.git.hui.jobclaw.configs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "jobclaw.rate-limit")
public class RateLimitProperties {
    /**
     * AIDEV-NOTE: API限流总开关，默认关闭以免影响本地调试。
     */
    private boolean enabled;
    private int windowSeconds = 60;
    private int anonymousLimit = 60;
    private int userLimit = 300;
    private int adminLimit = 1000;
    private List<String> excludedPaths = List.of(
            "/api/common/",
            "/api/wx/",
            "/api/oc/list"
    );
}
