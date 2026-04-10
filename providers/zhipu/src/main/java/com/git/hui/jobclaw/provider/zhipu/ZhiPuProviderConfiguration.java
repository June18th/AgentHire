package com.git.hui.jobclaw.provider.zhipu;

import com.git.hui.jobclaw.core.providers.ModelProvider;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * openai 模型提供者
 * @author YiHui
 * @date 2026/4/9
 */
@AutoConfiguration
public class ZhiPuProviderConfiguration {

    @Bean
    public ModelProvider zhipuModelProvider(ObjectProvider<RestClient.Builder> restClientBuilderProvider, ObjectProvider<WebClient.Builder> webClientBuilderProvider, ObjectProvider<ResponseErrorHandler> responseErrorHandler, ObjectProvider<RetryTemplate> retryTemplate, ObjectProvider<ObservationRegistry> observationRegistry, ToolCallingManager toolCallingManager, ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate) {
        return new ZhiPuModelProvider(restClientBuilderProvider,
                webClientBuilderProvider,
                responseErrorHandler,
                retryTemplate,
                observationRegistry,
                toolCallingManager,
                toolExecutionEligibilityPredicate);
    }
}
