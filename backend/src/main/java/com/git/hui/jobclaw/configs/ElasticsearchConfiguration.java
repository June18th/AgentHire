package com.git.hui.jobclaw.configs;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "jobclaw.search.elasticsearch", name = "enabled", havingValue = "true")
    public RestClient elasticsearchRestClient(SearchProperties properties) {
        return RestClient.builder(HttpHost.create(properties.getEndpoint()))
                .setRequestConfigCallback(config -> config
                        .setConnectTimeout(properties.getConnectTimeoutMs())
                        .setSocketTimeout(properties.getSocketTimeoutMs()))
                .build();
    }
}
