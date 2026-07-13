package com.git.hui.jobclaw.core.tools;

import io.modelcontextprotocol.client.McpAsyncClient;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * MCP Client 工具回调持有者，仅在启用 MCP Client 时装配。
 * AI-GENERATED
 */
@Component
@ConditionalOnProperty(name = "spring.ai.mcp.client.enabled", havingValue = "true")
public class AgentMcpToolCallbacksHolder {

    private final ToolCallback[] toolCallbacks;

    public AgentMcpToolCallbacksHolder(List<McpAsyncClient> mcpClients) {
        if (CollectionUtils.isEmpty(mcpClients)) {
            this.toolCallbacks = new ToolCallback[0];
            return;
        }
        this.toolCallbacks = AsyncMcpToolCallbackProvider.builder()
                .mcpClients(mcpClients)
                .build()
                .getToolCallbacks();
    }

    public ToolCallback[] getToolCallbacks() {
        return toolCallbacks;
    }
}
