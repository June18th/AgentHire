package com.git.hui.jobclaw.provider.zhipu;

import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProvider;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import io.micrometer.common.util.StringUtils;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.model.Model;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.retry.RetryUtils;
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
    private final static String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4";
    private final static String DEFAULT_COMPLETIONS_PATH = "/chat/completions";
    private final static String DEFAULT_EMBEDDINGS_PATH = "/embeddings";
    private final static String DEFAULT_IMAGES_PATH = "/images/generations";
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
            case VIDEO -> throw new IllegalArgumentException("unsupported model type: " + info.getType());
            case ASR -> throw new IllegalArgumentException("unsupported model type: " + info.getType());
            case TTS -> throw new IllegalArgumentException("unsupported model type: " + info.getType());
        };
    }

    private ChatModel buildChatModel(ModelConfig.ModelInfo info) {
        OpenAiApi openAiApi = openAiApiBuilder(info)
                .completionsPath(StringUtils.isNotBlank(info.getPath()) ? info.getPath() : DEFAULT_COMPLETIONS_PATH)
                .build();

        var chatModel = new OpenAiChatModel(openAiApi,
                OpenAiChatOptions.builder().model(info.getModelName()).maxTokens(info.getMaxTokens()).temperature(0.7).build(),
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

    private OpenAiApi.Builder openAiApiBuilder(ModelConfig.ModelInfo info) {
        return OpenAiApi.builder()
                .baseUrl(StringUtils.isNotBlank(info.getBaseUrl()) ? info.getBaseUrl() : DEFAULT_BASE_URL)
                .apiKey(info.getApiKey())
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
                .responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER));
    }

    private ImageModel buildImageModel(ModelConfig.ModelInfo info) {
        var builder = OpenAiImageApi.builder()
                .apiKey(info.getApiKey())
                .baseUrl(StringUtils.isNotBlank(info.getBaseUrl()) ? info.getBaseUrl() : DEFAULT_BASE_URL)
                .imagesPath(StringUtils.isNotBlank(info.getPath()) ? info.getPath() : DEFAULT_IMAGES_PATH)
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER));
        OpenAiImageModel imageModel = new OpenAiImageModel(builder.build(),
                OpenAiImageOptions.builder().model(info.getModelName()).build(),
                retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE));
        if (SpringUtil.getBeanOrNull(ImageModelObservationConvention.class) != null) {
            imageModel.setObservationConvention(SpringUtil.getBean(ImageModelObservationConvention.class));
        }
        return imageModel;
    }

    private EmbeddingModel buildEmbeddingModel(ModelConfig.ModelInfo info) {
        OpenAiApi openAiApi = openAiApiBuilder(info)
                .embeddingsPath(StringUtils.isNotBlank(info.getPath()) ? info.getPath() : DEFAULT_EMBEDDINGS_PATH)
                .build();

        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(openAiApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(info.getModelName()).build());
        Objects.requireNonNull(embeddingModel);
        if (SpringUtil.getBeanOrNull(EmbeddingModelObservationConvention.class) != null) {
            embeddingModel.setObservationConvention(SpringUtil.getBean(EmbeddingModelObservationConvention.class));
        }
        return embeddingModel;
    }
}
