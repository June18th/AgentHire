package com.git.hui.jobclaw.agents.jobfetch.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.core.security.PublicUrlSafety;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ZhiPu Web Search API adapter. Provider credentials are reused from agent.ai.providers.zhipu.
 * AI-GENERATED
 */
@Slf4j
@Component
public class ZhiPuWebSearchProvider implements JobSearchProvider {

    private static final String PROVIDER = "zhipu";
    private static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4";

    private final ModelProviders modelProviders;
    private final JobSearchProperties properties;
    private final ObjectMapper objectMapper;

    public ZhiPuWebSearchProvider(ModelProviders modelProviders,
                                  JobSearchProperties properties,
                                  ObjectMapper objectMapper) {
        this.modelProviders = modelProviders;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean isAvailable() {
        if (!properties.isEnabled() || !PROVIDER.equalsIgnoreCase(properties.getProvider())) {
            return false;
        }
        return providerConfig().map(config -> hasText(config.getApiKey())).orElse(false);
    }

    @Override
    public List<JobSearchCandidate> search(String query, int limit) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            throw new IllegalArgumentException("岗位搜索条件不能为空");
        }
        if (normalizedQuery.length() > 70) {
            throw new IllegalArgumentException("岗位搜索条件不能超过 70 个字符");
        }

        AiUserPreferenceProperties.ProviderConfig config = providerConfig()
                .filter(value -> hasText(value.getApiKey()))
                .orElseThrow(() -> new IllegalStateException("智谱供应商 API Key 未配置，请先在后台 LLM 供应商中配置"));
        int resultLimit = Math.max(1, Math.min(Math.min(limit, properties.getMaxResults()), 50));

        try {
            WebSearchRequest payload = new WebSearchRequest(
                    normalizedQuery,
                    properties.getEngine(),
                    false,
                    resultLimit,
                    "medium"
            );
            HttpRequest request = HttpRequest.newBuilder(searchEndpoint(config))
                    .timeout(Duration.ofMillis(Math.max(1_000, properties.getReadTimeoutMs())))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("智谱搜索请求失败，HTTP " + response.statusCode());
            }
            WebSearchResponse body = objectMapper.readValue(response.body(), WebSearchResponse.class);
            return sanitizeCandidates(body.searchResult(), resultLimit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("智谱搜索请求被中断", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("智谱搜索请求失败: " + e.getMessage(), e);
        }
    }

    HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1_000, properties.getConnectTimeoutMs())))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    // AIDEV-NOTE: 搜索结果必须先过公网校验再进入抓取链路
    List<JobSearchCandidate> sanitizeCandidates(List<WebSearchItem> items, int limit) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<String, JobSearchCandidate> unique = new LinkedHashMap<>();
        for (WebSearchItem item : items) {
            if (item == null || !hasText(item.link())) {
                continue;
            }
            PublicUrlSafety.CheckResult safety = PublicUrlSafety.check(item.link());
            if (!safety.canAccess()) {
                log.warn("丢弃非公网搜索结果: host={}, reason={}", safety.host(), safety.reason());
                continue;
            }
            String normalizedUrl = normalizeUrl(item.link());
            unique.putIfAbsent(normalizedUrl, new JobSearchCandidate(
                    item.title(), normalizedUrl, item.content(), item.media(), item.publishDate()));
            if (unique.size() >= limit) {
                break;
            }
        }
        return List.copyOf(unique.values());
    }

    static String normalizeUrl(String url) {
        URI uri = URI.create(url.trim()).normalize();
        String scheme = uri.getScheme().toLowerCase();
        String host = uri.getHost().toLowerCase();
        int port = uri.getPort();
        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            port = -1;
        }
        String path = uri.getRawPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        try {
            return new URI(scheme, null, host, port, path, uri.getRawQuery(), null).toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("无效搜索结果 URL", e);
        }
    }

    private java.util.Optional<AiUserPreferenceProperties.ProviderConfig> providerConfig() {
        return modelProviders.getGlobalProviderConfig(PROVIDER);
    }

    private URI searchEndpoint(AiUserPreferenceProperties.ProviderConfig config) {
        String baseUrl = hasText(config.getBaseUrl()) ? config.getBaseUrl().trim() : DEFAULT_BASE_URL;
        return URI.create(baseUrl.replaceAll("/+$", "") + "/web_search");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record WebSearchRequest(
            @JsonProperty("search_query") String searchQuery,
            @JsonProperty("search_engine") String searchEngine,
            @JsonProperty("search_intent") boolean searchIntent,
            int count,
            @JsonProperty("content_size") String contentSize
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WebSearchResponse(@JsonProperty("search_result") List<WebSearchItem> searchResult) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WebSearchItem(String title,
                         String content,
                         String link,
                         String media,
                         @JsonProperty("publish_date") String publishDate) {
    }
}
