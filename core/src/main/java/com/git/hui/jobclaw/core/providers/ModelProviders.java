package com.git.hui.jobclaw.core.providers;

import org.springframework.ai.model.Model;
import org.springframework.beans.factory.annotation.Autowired;
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
@Component
public class ModelProviders {

    /**
     * 这里用于维护不同模型厂商的默认配置信息
     */
    public Map<String, ModelConfig> providerConfigs;

    /**
     * 模型缓存
     */
    public Map<ModelConfig.ModelInfo, Model> modelCache;


    private Map<String, ModelProvider> providerMap;

    private final ModelConfigLoader modelConfigLoader;

    @Autowired
    public ModelProviders(List<ModelProvider> list, ModelConfigLoader modelConfigLoader) {
        this.providerMap = list.stream().collect(Collectors.toMap(ModelProvider::apiStyle, it -> it));
        this.modelCache = new ConcurrentHashMap<>();
        this.modelConfigLoader = modelConfigLoader;
        // 从配置加载模型配置信息
        this.providerConfigs = modelConfigLoader.loadAllProviderConfigs();
    }

    /**
     * 获取指定用户的模型配置
     * @param userId 用户ID
     * @return 用户的模型配置
     */
    public Map<String, ModelConfig> getUserModelConfigs(String userId) {
        return modelConfigLoader.loadUserModelConfigs(userId);
    }

    /**
     * 获取用户偏好的模型
     * @param userId 用户ID
     * @param modelType 模型类型 (vision 或 text)
     * @return ModelInfo
     */
    public ModelConfig.ModelInfo getUserPreferredModel(String userId, ModelConfig.ModelType modelType) {
        return modelConfigLoader.getUserPreferredModel(userId, modelType);
    }

    public Model getModel(String provider, String modelName, String apiKey) {
        var config = providerConfigs.get(provider);
        if (config == null) {
            throw new RuntimeException("未找到对应的模型厂商配置");
        }

        var defaultProviderModelInfo = config.getModels().stream()
                .filter(it -> it.getName().equals(modelName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到模型: " + modelName + " 在提供商: " + provider));

        ModelConfig.ModelInfo personModelInfo = ModelConfig.ModelInfo.builder()
                .provider(provider)
                .name(modelName)
                .apiKey(apiKey)
                .baseUrl(defaultProviderModelInfo.getBaseUrl())
                .path(defaultProviderModelInfo.getPath())
                .type(defaultProviderModelInfo.getType())
                .multimodal(defaultProviderModelInfo.getMultimodal())
                .maxTokens(defaultProviderModelInfo.getMaxTokens())
                .build();

        // 检查缓存
        if (modelCache.containsKey(personModelInfo)) {
            return modelCache.get(personModelInfo);
        }

        // 创建新模型实例
        var modelProvider = providerMap.get(config.getApiStyle());
        if (modelProvider == null) {
            throw new RuntimeException("未找到模型提供者: " + config.getApiStyle());
        }

        var model = modelProvider.model(personModelInfo);
        modelCache.put(personModelInfo, model);
        return model;
    }

    private void autoMergeConfig(ModelConfig.ModelInfo subConfig, ModelConfig.ModelInfo baseConfig) {

    }
}
