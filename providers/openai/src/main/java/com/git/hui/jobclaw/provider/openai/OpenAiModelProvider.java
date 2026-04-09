package com.git.hui.jobclaw.provider.openai;

import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProvider;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.model.Model;
import org.springframework.ai.model.SimpleApiKey;
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
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

/**
 *
 * @author YiHui
 * @date 2026/4/9
 */
public class OpenAiModelProvider implements ModelProvider {
    private ObjectProvider<RestClient.Builder> restClientBuilderProvider;
    private ObjectProvider<WebClient.Builder> webClientBuilderProvider;
    private ObjectProvider<ResponseErrorHandler> responseErrorHandler;

    public OpenAiModelProvider(ObjectProvider<RestClient.Builder> restClientBuilderProvider, ObjectProvider<WebClient.Builder> webClientBuilderProvider, ObjectProvider<ResponseErrorHandler> responseErrorHandler) {
        this.restClientBuilderProvider = restClientBuilderProvider;
        this.webClientBuilderProvider = webClientBuilderProvider;
        this.responseErrorHandler = responseErrorHandler;
    }

    @Override
    public String apiStyle() {
        return "openai";
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
            default -> throw new IllegalArgumentException("unsupported model type: " + info.getType());
        };
    }

    private Model buildChatModel(ModelConfig.ModelInfo info) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(info.getBaseUrl())
                .completionsPath(info.getPath())
                .apiKey(info.getApiKey())
                .restClientBuilder((RestClient.Builder) restClientBuilderProvider.getIfAvailable(RestClient::builder)).webClientBuilder((WebClient.Builder) webClientBuilderProvider.getIfAvailable(WebClient::builder)).responseErrorHandler((ResponseErrorHandler) responseErrorHandler.getIfAvailable(() -> {
                    return RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;
                })).build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(info.getName()).maxTokens(info.getMaxTokens()).build())
                .build();
        return chatModel;
    }

    private OpenAiEmbeddingModel buildEmbeddingModel(ModelConfig.ModelInfo info) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(info.getApiKey())
                .baseUrl(info.getBaseUrl())
                .embeddingsPath(info.getPath())
                .restClientBuilder((RestClient.Builder) restClientBuilderProvider.getIfAvailable(RestClient::builder)).webClientBuilder((WebClient.Builder) webClientBuilderProvider.getIfAvailable(WebClient::builder)).responseErrorHandler((ResponseErrorHandler) responseErrorHandler.getIfAvailable(() -> {
                    return RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;
                })).build();
        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(openAiApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(info.getName()).build());
        return embeddingModel;
    }

    private OpenAiImageModel buildImageModel(ModelConfig.ModelInfo info) {
        OpenAiImageApi openAiImageApi = OpenAiImageApi.builder().baseUrl(info.getBaseUrl())
                .apiKey(new SimpleApiKey(info.getApiKey()))
                .imagesPath(info.getPath())
                .restClientBuilder((RestClient.Builder) restClientBuilderProvider.getIfAvailable(RestClient::builder)).responseErrorHandler((ResponseErrorHandler) responseErrorHandler.getIfAvailable(() -> {
                    return RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;
                })).build();


        OpenAiImageModel imageModel = new OpenAiImageModel(openAiImageApi, OpenAiImageOptions.builder().model(info.getName()).build(), RetryUtils.DEFAULT_RETRY_TEMPLATE);
        Objects.requireNonNull(imageModel);
        return imageModel;
    }

}
