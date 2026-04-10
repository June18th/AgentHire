package com.git.hui.jobclaw.provider.zhipu;

import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProvider;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.model.Model;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingModel;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingOptions;
import org.springframework.ai.zhipuai.ZhiPuAiImageModel;
import org.springframework.ai.zhipuai.ZhiPuAiImageOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.ai.zhipuai.api.ZhiPuAiImageApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

/**
 *
 * @author YiHui
 * @date 2026/4/10
 */
public class ZhiPuModelProvider implements ModelProvider {
    private final ObjectProvider<RestClient.Builder> restClientBuilderProvider;
    private final ObjectProvider<WebClient.Builder> webClientBuilderProvider;
    private final ObjectProvider<ResponseErrorHandler> responseErrorHandler;
    private final ObjectProvider<RetryTemplate> retryTemplate;
    private final ObjectProvider<ObservationRegistry> observationRegistry;
    private final ToolCallingManager toolCallingManager;
    private final ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate;

    public ZhiPuModelProvider(ObjectProvider<RestClient.Builder> restClientBuilderProvider, ObjectProvider<WebClient.Builder> webClientBuilderProvider, ObjectProvider<ResponseErrorHandler> responseErrorHandler, ObjectProvider<RetryTemplate> retryTemplate, ObjectProvider<ObservationRegistry> observationRegistry, ToolCallingManager toolCallingManager, ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate) {
        this.restClientBuilderProvider = restClientBuilderProvider;
        this.webClientBuilderProvider = webClientBuilderProvider;
        this.responseErrorHandler = responseErrorHandler;
        this.retryTemplate = retryTemplate;
        this.observationRegistry = observationRegistry;
        this.toolCallingManager = toolCallingManager;
        this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
    }

    @Override
    public String apiStyle() {
        return "zhipu";
    }

    @Override
    public Model model(ModelConfig.ModelInfo info) {
        return switch (info.getType()) {
            case TEXT -> buildChatModel(info);
            case VISION -> buildChatModel(info);
            case IMAGE -> buildImageModel(info);
            case EMBEDDING -> buildEmbeddingModel(info);
            default -> throw new IllegalArgumentException("unsupported model type: " + info.getType());
        };
    }

    private ChatModel buildChatModel(ModelConfig.ModelInfo info) {
        var zhiPuAiApi = ZhiPuAiApi.builder().baseUrl(info.getBaseUrl())
                .apiKey(new SimpleApiKey(info.getApiKey()))
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
                .responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
                .build();


        var chatModel = new ZhiPuAiChatModel(zhiPuAiApi,
                ZhiPuAiChatOptions.builder().model(info.getModelName()).temperature(0.7).build(),
                toolCallingManager,
                retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE),
                observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
                toolExecutionEligibilityPredicate.getIfUnique(
                        DefaultToolExecutionEligibilityPredicate::new));
        Objects.requireNonNull(chatModel);
        if (SpringUtil.getBeanOrNull(ChatModelObservationConvention.class) != null) {
            chatModel.setObservationConvention(SpringUtil.getBean(ChatModelObservationConvention.class));
        }
        return chatModel;
    }

    private ImageModel buildImageModel(ModelConfig.ModelInfo info) {
        ZhiPuAiImageApi zhiPuAiImageApi = new ZhiPuAiImageApi(info.getBaseUrl(),
                info.getApiKey(),
                restClientBuilderProvider.getIfAvailable(RestClient::builder),
                responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER));
        ZhiPuAiImageModel imageModel = new ZhiPuAiImageModel(zhiPuAiImageApi,
                ZhiPuAiImageOptions.builder().model(info.getModelName()).build(),
                retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE));
        return imageModel;
    }

    private EmbeddingModel buildEmbeddingModel(ModelConfig.ModelInfo info) {
        var zhiPuAiApi = ZhiPuAiApi.builder().baseUrl(info.getBaseUrl())
                .apiKey(new SimpleApiKey(info.getApiKey()))
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
                .responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
                .build();

        ZhiPuAiEmbeddingModel embeddingModel = new ZhiPuAiEmbeddingModel(zhiPuAiApi,
                MetadataMode.EMBED,
                ZhiPuAiEmbeddingOptions.builder().model(info.getModelName()).build(),
                retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE),
                observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));
        Objects.requireNonNull(embeddingModel);
        if (SpringUtil.getBeanOrNull(EmbeddingModelObservationConvention.class) != null) {
            embeddingModel.setObservationConvention(SpringUtil.getBean(EmbeddingModelObservationConvention.class));
        }
        return embeddingModel;
    }
}
