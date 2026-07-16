package com.git.hui.jobclaw.agents.jobfetch;

import com.git.hui.jobclaw.agents.jobfetch.search.JobSearchProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author YiHui
 * @date 2026/4/17
 */
@Configuration
@EnableConfigurationProperties(JobSearchProperties.class)
public class JobFetchConfiguration {
}
