package com.git.hui.jobclaw.agents.jobfetch.search;

import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZhiPuWebSearchProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/paas/v4/web_search", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "id": "search-1",
                      "search_result": [
                        {
                          "title": "Java 实习招聘",
                          "content": "北京 2026 校招岗位",
                          "link": "https://8.8.8.8/jobs/java#details",
                          "media": "示例招聘网",
                          "publish_date": "2026-07-16"
                        },
                        {
                          "title": "重复结果",
                          "content": "同一个职位页面",
                          "link": "https://8.8.8.8:443/jobs/java",
                          "media": "示例招聘网"
                        },
                        {
                          "title": "内网页面",
                          "content": "不应进入抓取链路",
                          "link": "http://127.0.0.1/admin",
                          "media": "非法来源"
                        }
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsOfficialRequestAndMapsOnlyUniquePublicResults() throws Exception {
        ZhiPuWebSearchProvider provider = provider("secret-key");

        var candidates = provider.search("北京 Java 实习", 5);

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.title()).isEqualTo("Java 实习招聘");
            assertThat(candidate.url()).isEqualTo("https://8.8.8.8/jobs/java");
            assertThat(candidate.source()).isEqualTo("示例招聘网");
            assertThat(candidate.publishDate()).isEqualTo("2026-07-16");
        });
        assertThat(authorization.get()).isEqualTo("Bearer secret-key");
        JsonNode request = objectMapper.readTree(requestBody.get());
        assertThat(request.get("search_query").asText()).isEqualTo("北京 Java 实习");
        assertThat(request.get("search_engine").asText()).isEqualTo("search_std");
        assertThat(request.get("search_intent").asBoolean()).isFalse();
        assertThat(request.get("count").asInt()).isEqualTo(5);
        assertThat(request.get("content_size").asText()).isEqualTo("medium");
    }

    @Test
    void reportsUnavailableAndRejectsSearchWhenApiKeyIsBlank() {
        ZhiPuWebSearchProvider provider = provider(" ");

        assertThat(provider.isAvailable()).isFalse();
        assertThatThrownBy(() -> provider.search("北京 Java 实习", 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API Key 未配置");
    }

    private ZhiPuWebSearchProvider provider(String apiKey) {
        JobSearchProperties properties = new JobSearchProperties();
        properties.setConnectTimeoutMs(2_000);
        properties.setReadTimeoutMs(2_000);

        AiUserPreferenceProperties.ProviderConfig config = new AiUserPreferenceProperties.ProviderConfig();
        config.setApiKey(apiKey);
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api/paas/v4");
        ModelProviders modelProviders = mock(ModelProviders.class);
        when(modelProviders.getGlobalProviderConfig("zhipu")).thenReturn(Optional.of(config));
        return new ZhiPuWebSearchProvider(modelProviders, properties, objectMapper);
    }
}
