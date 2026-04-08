package com.git.hui.jobclaw.gather.service.ai.impl.zhipu;

import com.git.hui.jobclaw.constants.gather.GatherModelEnum;
import com.git.hui.jobclaw.gather.service.ai.impl.AbsOcChatModelApi;
import io.modelcontextprotocol.client.McpAsyncClient;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * @author YiHui
 * @date 2025/7/30
 */
@Component
public class ZhiPuOcChatModel extends AbsOcChatModelApi {
    // 默认的智谱的图片模型
    private final ZhiPuAiChatModel zhiPuAiChatModel;

    private final ChatClient chatClient;
    private ChatClient imgClient;

    @Value("${spring.ai.zhipuai.multi-mode:GLM-4V-Flash}")
    private String imgSigModel;

    public ZhiPuOcChatModel(ZhiPuAiChatModel zhiPuAiChatModel, List<McpAsyncClient> mcpClients) {
        this.zhiPuAiChatModel = zhiPuAiChatModel;

        chatClient = ChatClient.builder(zhiPuAiChatModel)
                .defaultSystem(GATHER_SYSTEM_PROMPT)
                .defaultOptions(ChatOptions.builder().stopSequences(Collections.emptyList()).build()) // 取消默认停止符
                .defaultAdvisors(new SimpleLoggerAdvisor())
                // 将MCP Client 注册为工具
                .defaultToolCallbacks(new AsyncMcpToolCallbackProvider(mcpClients))
                .build();
    }

    /**
     * 初始化多模态的图片理解模型
     */
    @PostConstruct
    public void postInitImgClint() {
        // 图片理解
        imgClient = ChatClient.builder(zhiPuAiChatModel)
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
        return zhiPuAiChatModel;
    }

    @Override
    public String chatModelName() {
        return zhiPuAiChatModel.getDefaultOptions().getModel();
    }

    @Override
    public String imgModelName() {
        return imgSigModel;
    }
}
