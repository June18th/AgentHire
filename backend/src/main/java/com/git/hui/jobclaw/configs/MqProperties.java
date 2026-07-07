package com.git.hui.jobclaw.configs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jobclaw.mq")
public class MqProperties {
    private boolean enabled;
    private String provider = "kafka";
    private Topics topics = new Topics();

    @Data
    public static class Topics {
        private String domainEvent = "jobclaw.domain-event";
        private String llmAudit = "jobclaw.llm-audit";
    }
}
