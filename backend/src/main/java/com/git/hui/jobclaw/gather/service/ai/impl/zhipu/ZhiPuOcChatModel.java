package com.git.hui.jobclaw.gather.service.ai.impl.zhipu;

import com.git.hui.jobclaw.constants.gather.GatherModelEnum;
import com.git.hui.jobclaw.gather.service.ai.impl.AbsOcChatModelApi;
import io.micrometer.observation.ObservationRegistry;
import io.modelcontextprotocol.client.McpAsyncClient;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;

/**
 * ZhiPu legacy gather model adapter.
 *
 * @author YiHui
 * @date 2025/7/30
 */
@Component
@ConditionalOnProperty(prefix = "jobclaw.gather.ai.legacy.zhipu", name = "enabled", havingValue = "true")
public class ZhiPuOcChatModel extends AbsOcChatModelApi {
    private static final String ZHIPU_OPENAI_BASE_URL = "https://open.bigmodel.cn/api/paas/v4";
    private static final String ZHIPU_CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final OpenAiChatModel zhiPuChatModel;
    private final ChatClient chatClient;
    private ChatClient imgClient;

    @Value("${spring.ai.zhipuai.multi-mode:GLM-4V-Flash}")
    private String imgSigModel;

    public ZhiPuOcChatModel(@Value("${spring.ai.zhipuai.api-key:${ZHIPUAI_API_KEY:}}") String apiKey,
                            @Value("${spring.ai.zhipuai.chat.options.model:GLM-4-Flash}") String chatModelName,
                            ObjectProvider<RestClient.Builder> restClientBuilderProvider,
                            ObjectProvider<WebClient.Builder> webClientBuilderProvider,
                            ObjectProvider<ResponseErrorHandler> responseErrorHandler,
                            ObjectProvider<RetryTemplate> retryTemplate,
                            ObjectProvider<ObservationRegistry> observationRegistry,
                            ObjectProvider<ToolCallingManager> toolCallingManager,
                            ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate,
                            List<McpAsyncClient> mcpClients) {
        this.zhiPuChatModel = buildChatModel(apiKey,
                chatModelName,
                restClientBuilderProvider,
                webClientBuilderProvider,
                responseErrorHandler,
                retryTemplate,
                observationRegistry,
                toolCallingManager,
                toolExecutionEligibilityPredicate);

        this.chatClient = ChatClient.builder(zhiPuChatModel)
                .defaultSystem(GATHER_SYSTEM_PROMPT)
                .defaultOptions(ChatOptions.builder().stopSequences(Collections.emptyList()).build())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultToolCallbacks(AsyncMcpToolCallbackProvider.builder().mcpClients(mcpClients).build())
                .build();
    }

    private OpenAiChatModel buildChatModel(String apiKey,
                                           String chatModelName,
                                           ObjectProvider<RestClient.Builder> restClientBuilderProvider,
                                           ObjectProvider<WebClient.Builder> webClientBuilderProvider,
                                           ObjectProvider<ResponseErrorHandler> responseErrorHandler,
                                           ObjectProvider<RetryTemplate> retryTemplate,
                                           ObjectProvider<ObservationRegistry> observationRegistry,
                                           ObjectProvider<ToolCallingManager> toolCallingManager,
                                           ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(ZHIPU_OPENAI_BASE_URL)
                .completionsPath(ZHIPU_CHAT_COMPLETIONS_PATH)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
                .responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(chatModelName).build())
                .toolCallingManager(toolCallingManager.getIfAvailable())
                .toolExecutionEligibilityPredicate(toolExecutionEligibilityPredicate.getIfAvailable())
                .retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .build();
    }

    @PostConstruct
    public void postInitImgClint() {
        imgClient = ChatClient.builder(zhiPuChatModel)
                .defaultSystem(GATHER_SYSTEM_PROMPT)
                .defaultOptions(ChatOptions.builder()
                        .model(imgSigModel)
                        .stopSequences(Collections.emptyList()).build())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    @Override
    public GatherModelEnum modelEnum() {
        return GatherModelEnum.ZHIPU;
    }

    @Override
    public ChatClient chatClient() {
        return chatClient;
    }

    @Override
    public ChatClient imgClient() {
        return imgClient;
    }

    @Override
    public ChatModel chatModel() {
        return zhiPuChatModel;
    }

    @Override
    public String chatModelName() {
        return zhiPuChatModel.getDefaultOptions().getModel();
    }

    @Override
    public String imgModelName() {
        return imgSigModel;
    }
}
