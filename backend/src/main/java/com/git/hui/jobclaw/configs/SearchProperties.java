package com.git.hui.jobclaw.configs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jobclaw.search.elasticsearch")
public class SearchProperties {
    private boolean enabled;
    private String endpoint = "http://localhost:9200";
    private String indexName = "jobclaw_oc_job";
    private int connectTimeoutMs = 2000;
    private int socketTimeoutMs = 5000;
}
