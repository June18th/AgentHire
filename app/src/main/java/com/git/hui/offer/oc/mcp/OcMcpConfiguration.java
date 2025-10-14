package com.git.hui.offer.oc.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.git.hui.offer.oc.mcp.server.WebMvcSseServerTransportProvider;
import org.springframework.ai.mcp.server.autoconfigure.McpServerProperties;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * mcp 注册
 *
 * @author YiHui
 * @date 2025/7/28
 */
@Configuration
public class OcMcpConfiguration {
    @Bean
    public ToolCallbackProvider dateProvider(OcMcpService dateService) {
        return MethodToolCallbackProvider.builder().toolObjects(dateService).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public WebMvcSseServerTransportProvider webMvcSseServerTransportProvider(ObjectProvider<ObjectMapper> objectMapperProvider, McpServerProperties serverProperties) {
        ObjectMapper objectMapper = (ObjectMapper) objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new WebMvcSseServerTransportProvider(objectMapper, serverProperties.getBaseUrl(), serverProperties.getSseMessageEndpoint(), serverProperties.getSseEndpoint());
    }

    @Bean
    public RouterFunction<ServerResponse> mvcMcpRouterFunction(WebMvcSseServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }
}
