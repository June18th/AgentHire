package com.git.hui.jobclaw.core.providers;

import com.git.hui.jobclaw.core.configuration.event.PropertiesRefreshedEvent;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 模型提供者
 * @author YiHui
 * @date 2026/4/9
 */
@Slf4j
@Component
public class ModelProviders {
    private final static String DEFAULT_PREFERENCE = "total";
    /**
     * 模型缓存
     */
    public Map<ModelConfig.ModelInfo, Model> modelCache;


    /**
     * key = 接口风格，modelProvider.apiStyle();
     */
    private Map<String, ModelProvider> providerMap;


    private final AiUserPreferenceProperties aiUserPreferenceProperties;

    @Async
    @EventListener
    public void registerUserPreferenceChangeCallback(PropertiesRefreshedEvent event) {
        // 这里用于注册用户偏好配置变更之后的回调逻辑，比如当用户重置userCacheClient缓存
        if (AiUserPreferenceProperties.class.equals(event.getPropertiesClz())) {
            modelCache.clear();
            log.info("[ModelProviders] User preference changed, clear user cache");
        }
    }


    @Autowired
    public ModelProviders(List<ModelProvider> list, AiUserPreferenceProperties aiUserPreferenceProperties) {
        this.providerMap = list.stream().collect(Collectors.toMap(ModelProvider::apiStyle, it -> it));
        this.aiUserPreferenceProperties = aiUserPreferenceProperties;
        this.modelCache = new ConcurrentHashMap<>();
    }

    /**
     * 获取用户偏好的模型
     * @param userId 用户ID
     * @param modelType 模型类型 (vision 或 text)
     * @return ModelInfo
     */
    public Model getModel(String userId, ModelConfig.ModelType modelType) {
        var preference = aiUserPreferenceProperties.getUserPreference(userId);
        if (preference == null) {
            log.info("[ModelProviders] User preference not found, use default preference");
            preference = aiUserPreferenceProperties.getUserPreference(DEFAULT_PREFERENCE);
        }
        if (preference == null) {
            throw new RuntimeException("未找到用户偏好配置: " + userId);
        }

        // 首先找到用户的偏好模型
        var preferModel = switch (modelType) {
            case TEXT -> preference.getModels().getText();
            case VISION -> preference.getModels().getVision();
            case IMAGE -> preference.getModels().getImage();
            case VIDEO -> preference.getModels().getVideo();
            case EMBEDDING -> preference.getModels().getEmbedding();
            case ASR -> preference.getModels().getAsr();
            case TTS -> preference.getModels().getTts();
        };

        if (StringUtils.isBlank(preferModel) && !DEFAULT_PREFERENCE.equals(userId)) {
            // 用户没有配置API的场景下，使用默认的全局配置替代
            log.info("[ModelProviders] User API Key not configured, use default preference");
            return getModel(DEFAULT_PREFERENCE, modelType);
        }

        // 解析获取 provider + modelName
        var cell = preferModel.split("#");
        String provider = cell[0];
        String modelName = cell[1];

        // 根据provider来构建 ModelInfo的基础配置
        var providerInfo = preference.getProviders() == null ? null : preference.getProviders().get(provider);
        if (providerInfo == null) {
            // 从全局兜底的厂商配置中进行获取
            providerInfo = aiUserPreferenceProperties.getProviders().get(provider);
            if (providerInfo == null) {
                throw new RuntimeException("未找到厂商配置: " + provider);
            }
        }
        var modelInfoOpt = providerInfo.getModels().stream().filter(s -> s.getName().equals(modelName)).findFirst();
        if (modelInfoOpt.isEmpty()) {
            throw new RuntimeException("未找到模型配置: " + modelName);
        }
        var modelInfo = modelInfoOpt.get();


        ModelConfig.ModelInfo personModelInfo = ModelConfig.ModelInfo.builder()
                .provider(provider)
                .modelName(modelName)
                .apiKey(providerInfo.getApiKey())
                .baseUrl(providerInfo.getBaseUrl())
                .path(providerInfo.getCompletionsPath())
                .type(modelInfo.getType())
                .multimodal(modelInfo.getMultimodal())
                .build();


        // 检查缓存
        if (modelCache.containsKey(personModelInfo)) {
            return modelCache.get(personModelInfo);
        }

        // 创建新模型实例
        var modelProvider = providerMap.get(providerInfo.getApiStyle());
        if (modelProvider == null) {
            throw new RuntimeException("未找到模型提供者: " + providerInfo.getApiStyle());
        }

        var model = modelProvider.model(personModelInfo);
        modelCache.put(personModelInfo, model);
        return model;
    }
}
