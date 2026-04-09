package com.git.hui.jobclaw.core.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型配置加载器 - 负责从配置属性构建 ModelConfig
 * @author YiHui
 * @date 2026/4/9
 */
@Slf4j
@Component
public class ModelConfigLoader {
    
    private final AiModelProperties aiModelProperties;
    
    @Autowired
    public ModelConfigLoader(AiModelProperties aiModelProperties) {
        this.aiModelProperties = aiModelProperties;
    }
    
    /**
     * 加载所有提供商的 ModelConfig
     * @return Map<provider, ModelConfig>
     */
    public Map<String, ModelConfig> loadAllProviderConfigs() {
        if (aiModelProperties.getProviders() == null || aiModelProperties.getProviders().isEmpty()) {
            log.warn("No AI model providers configured");
            return new HashMap<>();
        }
        
        Map<String, ModelConfig> configs = new HashMap<>();
        
        for (Map.Entry<String, AiModelProperties.ProviderConfig> entry : 
                aiModelProperties.getProviders().entrySet()) {
            String providerName = entry.getKey();
            AiModelProperties.ProviderConfig providerConfig = entry.getValue();
            
            ModelConfig modelConfig = buildModelConfig(providerName, providerConfig);
            configs.put(providerName, modelConfig);
        }
        
        log.info("Loaded {} model provider configurations", configs.size());
        return configs;
    }
    
    /**
     * 为指定用户加载 ModelConfig（包含用户级别的 API Key 覆盖）
     * @param userId 用户ID
     * @return Map<provider, ModelConfig>
     */
    public Map<String, ModelConfig> loadUserModelConfigs(String userId) {
        // 首先加载基础提供商配置
        Map<String, ModelConfig> baseConfigs = loadAllProviderConfigs();

        // 检查是否有用户级别的配置
        if (aiModelProperties.getModel() == null || aiModelProperties.getModel().isEmpty()) {
            log.debug("No user-specific model config found for user: {}", userId);
            return baseConfigs;
        }

        // 查找对应用户的配置
        AiModelProperties.UserModelPreference userPreference = aiModelProperties.getModel().stream()
                .filter(entry -> userId.equals(entry.getUserId()))
                .map(AiModelProperties.UserModelEntry::getPreference)
                .findFirst()
                .orElse(null);

        if (userPreference == null) {
            log.debug("No preference found for user: {}", userId);
            return baseConfigs;
        }

        if (userPreference.getProviders() == null || userPreference.getProviders().isEmpty()) {
            return baseConfigs;
        }

        // 应用用户级别的 API Key 覆盖
        Map<String, ModelConfig> userConfigs = new HashMap<>(baseConfigs);

        for (Map.Entry<String, AiModelProperties.UserProviderConfig> entry :
                userPreference.getProviders().entrySet()) {
            String providerName = entry.getKey();
            AiModelProperties.UserProviderConfig userProviderConfig = entry.getValue();

            if (userConfigs.containsKey(providerName)) {
                ModelConfig baseConfig = userConfigs.get(providerName);
                ModelConfig userConfig = applyUserOverrides(baseConfig, userProviderConfig);
                userConfigs.put(providerName, userConfig);
            }
        }

        log.debug("Applied user-specific overrides for user: {}", userId);
        return userConfigs;
    }

    /**
     * 获取用户的偏好模型
     * @param userId 用户ID
     * @param modelType 模型类型 (vision 或 text)
     * @return ModelInfo 或 null
     */
    public ModelConfig.ModelInfo getUserPreferredModel(String userId, ModelConfig.ModelType modelType) {
        if (aiModelProperties.getModel() == null || aiModelProperties.getModel().isEmpty()) {
            return null;
        }

        // 查找对应用户的配置
        AiModelProperties.UserModelPreference preference = aiModelProperties.getModel().stream()
                .filter(entry -> userId.equals(entry.getUserId()))
                .map(AiModelProperties.UserModelEntry::getPreference)
                .findFirst()
                .orElse(null);

        if (preference == null) {
            return null;
        }

        String modelRef = null;
        if (ModelConfig.ModelType.VISION == modelType) {
            modelRef = preference.getVision();
        } else if (ModelConfig.ModelType.TEXT == modelType) {
            modelRef = preference.getText();
        }

        if (!StringUtils.hasText(modelRef) || !modelRef.contains("#")) {
            return null;
        }

        String[] parts = modelRef.split("#", 2);
        String provider = parts[0];
        String modelName = parts[1];

        // 从用户配置中查找对应的 ModelConfig
        Map<String, ModelConfig> userConfigs = loadUserModelConfigs(userId);
        ModelConfig config = userConfigs.get(provider);

        if (config == null || config.getModels() == null) {
            return null;
        }

        return config.getModels().stream()
                .filter(m -> modelName.equals(m.getName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 构建 ModelConfig
     */
    private ModelConfig buildModelConfig(String providerName, 
                                         AiModelProperties.ProviderConfig providerConfig) {
        List<ModelConfig.ModelInfo> modelInfos = new ArrayList<>();
        
        if (providerConfig.getModels() != null) {
            for (AiModelProperties.ModelDefinition modelDef : providerConfig.getModels()) {
                ModelConfig.ModelInfo modelInfo = ModelConfig.ModelInfo.builder()
                        .name(modelDef.getName())
                        .type(modelDef.getType())
                        .multimodal(modelDef.getMultimodal())
                        .maxTokens(modelDef.getMaxTokens() != null ? modelDef.getMaxTokens() : 1280)
                        .baseUrl(providerConfig.getBaseUrl())
                        .path(resolvePath(modelDef.getType(), providerConfig))
                        .build();
                modelInfos.add(modelInfo);
            }
        }
        
        return ModelConfig.builder()
                .provider(providerName)
                .apiStyle(providerConfig.getApiStyle())
                .baseUrl(providerConfig.getBaseUrl())
                .completionsPath(providerConfig.getCompletionsPath())
                .embeddingsPath(providerConfig.getEmbeddingsPath())
                .imagesPath(providerConfig.getImagesPath())
                .speechPath(providerConfig.getSpeechPath())
                .transcriptionPath(providerConfig.getTranscriptionPath())
                .models(modelInfos)
                .build();
    }
    
    /**
     * 应用用户级别的覆盖配置
     */
    private ModelConfig applyUserOverrides(ModelConfig baseConfig, 
                                           AiModelProperties.UserProviderConfig userConfig) {
        // 复制基础配置
        List<ModelConfig.ModelInfo> updatedModels = new ArrayList<>();
        
        if (baseConfig.getModels() != null) {
            for (ModelConfig.ModelInfo baseModel : baseConfig.getModels()) {
                ModelConfig.ModelInfo updatedModel = ModelConfig.ModelInfo.builder()
                        .provider(baseConfig.getProvider())
                        .name(baseModel.getName())
                        .type(baseModel.getType())
                        .multimodal(baseModel.getMultimodal())
                        .maxTokens(baseModel.getMaxTokens())
                        .baseUrl(baseModel.getBaseUrl())
                        .path(baseModel.getPath())
                        .apiKey(resolveApiKey(userConfig, baseModel.getName(), userConfig.getApiKey()))
                        .build();
                updatedModels.add(updatedModel);
            }
        }
        
        return ModelConfig.builder()
                .provider(baseConfig.getProvider())
                .apiStyle(baseConfig.getApiStyle())
                .baseUrl(baseConfig.getBaseUrl())
                .completionsPath(baseConfig.getCompletionsPath())
                .embeddingsPath(baseConfig.getEmbeddingsPath())
                .imagesPath(baseConfig.getImagesPath())
                .speechPath(baseConfig.getSpeechPath())
                .transcriptionPath(baseConfig.getTranscriptionPath())
                .models(updatedModels)
                .build();
    }
    
    /**
     * 解析 API Key（支持模型级别的覆盖）
     */
    private String resolveApiKey(AiModelProperties.UserProviderConfig userConfig, 
                                 String modelName, String defaultApiKey) {
        if (userConfig.getModels() != null) {
            for (Map<String, String> modelEntry : userConfig.getModels()) {
                if (modelEntry.containsKey(modelName)) {
                    String modelApiKey = modelEntry.get(modelName);
                    if (StringUtils.hasText(modelApiKey)) {
                        return modelApiKey;
                    }
                }
            }
        }
        return defaultApiKey;
    }
    
    /**
     * 根据模型类型解析路径
     */
    private String resolvePath(ModelConfig.ModelType type, 
                               AiModelProperties.ProviderConfig providerConfig) {
        return switch (type) {
            case TEXT, VISION -> providerConfig.getCompletionsPath();
            case EMBEDDING -> providerConfig.getEmbeddingsPath();
            case IMAGE -> providerConfig.getImagesPath();
            case ASR -> providerConfig.getTranscriptionPath();
            case TTS -> providerConfig.getSpeechPath();
            default -> providerConfig.getCompletionsPath();
        };
    }
}
