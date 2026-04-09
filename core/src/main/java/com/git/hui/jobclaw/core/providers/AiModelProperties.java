package com.git.hui.jobclaw.core.providers;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * AI 模型配置属性
 * @author YiHui
 * @date 2026/4/9
 */
@Data
@ConfigurationProperties(prefix = "agent.ai")
public class AiModelProperties {
    
    /**
     * 用户级别的模型配置（按用户ID分组）
     * 注意：由于用户ID可能包含特殊字符，使用 List 而非 Map
     */
    private List<UserModelEntry> model;
    
    /**
     * 用户模型配置条目
     */
    @Data
    public static class UserModelEntry {
        /**
         * 用户ID
         */
        private String userId;
        
        /**
         * 用户的模型偏好配置
         */
        private UserModelPreference preference;
    }
    
    /**
     * 模型提供商配置
     * key: 提供商名称 (如: zhipu, silicon)
     * value: 提供商的配置信息
     */
    private Map<String, ProviderConfig> providers;
    
    /**
     * 用户模型偏好配置
     */
    @Data
    public static class UserModelPreference {
        /**
         * 视觉模型配置，格式: provider#modelName (如: zhipu#GLM-4V-Flash)
         */
        private String vision;
        
        /**
         * 文本模型配置，格式: provider#modelName (如: zhipu#GLM-4.7-Flash)
         */
        private String text;
        
        /**
         * 用户级别的提供商 API Key 覆盖配置
         */
        private Map<String, UserProviderConfig> providers;
    }
    
    /**
     * 用户级别的提供商配置
     */
    @Data
    public static class UserProviderConfig {
        /**
         * API Key
         */
        private String apiKey;
        
        /**
         * 模型级别的 API Key 覆盖
         * key: 模型名称
         * value: API Key
         */
        private List<Map<String, String>> models;
    }
    
    /**
     * 提供商配置
     */
    @Data
    public static class ProviderConfig {
        /**
         * API 风格 (如: openai)
         */
        private String apiStyle;
        
        /**
         * 基础 URL
         */
        private String baseUrl;
        
        /**
         * 对话完成路径
         */
        private String completionsPath;
        
        /**
         * 嵌入模型路径
         */
        private String embeddingsPath;
        
        /**
         * 图片生成路径
         */
        private String imagesPath;
        
        /**
         * 语音合成路径
         */
        private String speechPath;
        
        /**
         * 语音识别路径
         */
        private String transcriptionPath;
        
        /**
         * 模型列表
         */
        private List<ModelDefinition> models;
    }
    
    /**
     * 模型定义
     */
    @Data
    public static class ModelDefinition {
        /**
         * 模型名称
         */
        private String name;
        
        /**
         * 模型类型 (TEXT, VISION, IMAGE, VIDEO, EMBEDDING, ASR, TTS)
         */
        private ModelConfig.ModelType type;
        
        /**
         * 是否支持多模态
         */
        private Boolean multimodal;
        
        /**
         * 最大 token 数
         */
        private Integer maxTokens;
    }
}
