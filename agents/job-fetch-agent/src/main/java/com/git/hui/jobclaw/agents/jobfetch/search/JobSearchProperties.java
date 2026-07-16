package com.git.hui.jobclaw.agents.jobfetch.search;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Vendor-neutral controls for job-page discovery.
 * AI-GENERATED
 */
@Data
@ConfigurationProperties(prefix = "agent.job-search")
public class JobSearchProperties {

    private boolean enabled = true;
    private String provider = "zhipu";
    private String engine = "search_std";
    private int maxResults = 8;
    private int maxPages = 5;
    private int connectTimeoutMs = 3_000;
    private int readTimeoutMs = 15_000;
}
