package com.git.hui.jobclaw.core.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局环境配置处理器
 * 从数据库(global_env_config表)加载配置,并以最高优先级注入到Spring Environment
 * 支持配置的动态刷新
 *
 * @author YiHui
 * @date 2026/4/9
 */
@Slf4j
@Component
public class GlobalEnvConfigProcessor implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "GlobalEnvConfig";

    // 敏感关键词列表,包含这些关键词的key会被脱敏
    private static final String[] SENSITIVE_KEYWORDS = {
            "SECRET", "KEY", "PASSWORD", "TOKEN", "API_KEY", "PRIVATE",
            "CERT", "SERIAL", "MERCHANT"
    };

    // 缓存已加载的配置,用于动态刷新时对比
    private final Map<String, Object> cachedConfig = new ConcurrentHashMap<>();

    // 保存ApplicationContext引用,用于动态刷新
    private ConfigurableApplicationContext applicationContext;
    @Autowired(required = false)
    private EnvConfigRepository configRepository;
    private DynamicConfigBinder dynamicBinder;

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        // 保存ApplicationContext引用
        this.applicationContext = applicationContext;

        if (configRepository == null) {
            log.warn("[GlobalEnvConfig] GlobalEnvConfigRepository not available, skip loading database config");
            return;
        }

        ConfigurableEnvironment environment = applicationContext.getEnvironment();

        try {
            Map<String, Object> properties = loadConfigFromDatabase();

            if (properties.isEmpty()) {
                log.info("[GlobalEnvConfig] No configuration found in database");
                return;
            }

            // 缓存配置
            cachedConfig.clear();
            cachedConfig.putAll(properties);

            // 添加到Environment,使用最高优先级
            if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
                environment.getPropertySources().replace(PROPERTY_SOURCE_NAME,
                        new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
                log.info("[GlobalEnvConfig] Replaced existing property source with {} configurations",
                        properties.size());
            } else {
                environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
                log.info("[GlobalEnvConfig] Added property source with {} configurations", properties.size());
            }

            // 打印加载的配置(脱敏)
            if (log.isInfoEnabled()) {
                properties.forEach((key, value) ->
                        log.info("[GlobalEnvConfig] Loaded: {} = {}",
                                key,
                                maskSensitiveValue(key, String.valueOf(value)))
                );
            }
        } catch (Exception e) {
            log.error("[GlobalEnvConfig] Failed to load configuration from database", e);
        }
    }

    @Override
    public int getOrder() {
        // 设置为最高优先级,确保在DotenvEnvironmentPostProcessor之后执行
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }


    public void autoRefreshConfig(boolean force) {
        if (configRepository == null) {
            return;
        }

        try {
            Map<String, Object> newConfig = loadConfigFromDatabase();

            // 检测配置变更
            boolean hasChanged = detectConfigChanges(newConfig);

            if (force || hasChanged) {
                log.info("[GlobalEnvConfig] Scheduled configuration check found changes, refreshing...");
                applyConfigToEnvironment(newConfig);
                cachedConfig.clear();
                cachedConfig.putAll(newConfig);
                log.info("[GlobalEnvConfig] Configuration refreshed successfully");
            }
        } catch (Exception e) {
            log.error("[GlobalEnvConfig] Failed to refresh configuration", e);
        }
    }

    /**
     * 从数据库加载配置
     *
     * @return 配置Map
     */
    private Map<String, Object> loadConfigFromDatabase() {
        var configs = configRepository.findByEnabledOrderByCreateTimeAsc(1);

        Map<String, Object> properties = new LinkedHashMap<>();
        for (var temp : configs) {
            var config = ((EnvConfigRepository.EnvConfig) temp);
            String key = config.getConfigKey();
            String value = config.getConfigValue();

            if (key != null && !key.trim().isEmpty() && value != null) {
                properties.put(key.trim(), convertValue(value, config.getConfigType()));
            }
        }
        return properties;
    }

    /**
     * 检测配置变更
     *
     * @param newConfig 新配置
     * @return 是否有变更
     */
    private boolean detectConfigChanges(Map<String, Object> newConfig) {
        // 检查新增或修改的配置
        for (Map.Entry<String, Object> entry : newConfig.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            Object oldValue = cachedConfig.get(key);

            if (oldValue == null || !oldValue.equals(newValue)) {
                log.info("[GlobalEnvConfig] Config changed: {} = {} -> {}",
                        key, maskSensitiveValue(key, String.valueOf(oldValue)),
                        maskSensitiveValue(key, String.valueOf(newValue)));
                return true;
            }
        }

        // 检查删除的配置
        for (String key : cachedConfig.keySet()) {
            if (!newConfig.containsKey(key)) {
                log.info("[GlobalEnvConfig] Config removed: {}", key);
                return true;
            }
        }

        return false;
    }

    /**
     * 将新配置应用到Environment
     *
     * @param newConfig 新配置
     */
    private void applyConfigToEnvironment(Map<String, Object> newConfig) {
        if (applicationContext == null) {
            log.warn("[GlobalEnvConfig] ApplicationContext not available, cannot refresh config");
            return;
        }

        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        environment.getPropertySources().replace(PROPERTY_SOURCE_NAME,
                new MapPropertySource(PROPERTY_SOURCE_NAME, newConfig));

        applicationContext.getBeansWithAnnotation(ConfigurationProperties.class).values().forEach(bean -> {
            Bindable<?> target = Bindable.ofInstance(bean).withAnnotations(AnnotationUtils.findAnnotation(bean.getClass(),
                    ConfigurationProperties.class));
            if (this.dynamicBinder == null) {
                this.dynamicBinder = new DynamicConfigBinder(this.applicationContext, environment.getPropertySources());
            }
            this.dynamicBinder.bind(target);
        });
    }

    /**
     * 转换配置值类型
     *
     * @param value 字符串值
     * @param type  类型
     * @return 转换后的值
     */
    private Object convertValue(String value, String type) {
        if (type == null || type.trim().isEmpty()) {
            return value;
        }

        switch (type.toLowerCase().trim()) {
            case "int":
            case "integer":
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    log.warn("[GlobalEnvConfig] Failed to parse integer value: {}", value);
                    return value;
                }
            case "long":
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    log.warn("[GlobalEnvConfig] Failed to parse long value: {}", value);
                    return value;
                }
            case "boolean":
                return Boolean.parseBoolean(value);
            case "double":
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    log.warn("[GlobalEnvConfig] Failed to parse double value: {}", value);
                    return value;
                }
            case "json":
                // JSON类型保持字符串形式,由使用者自行解析
                return value;
            default:
                return value;
        }
    }

    /**
     * 判断是否为敏感信息并脱敏
     */
    private String maskSensitiveValue(String key, String value) {
        if (value == null) {
            return "null";
        }

        String upperKey = key.toUpperCase();
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (upperKey.contains(keyword)) {
                return maskValue(value);
            }
        }
        return value;
    }

    /**
     * 脱敏值:只显示前4个和后4个字符,中间用***替代
     */
    private String maskValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= 8) {
            return "***";
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }
}
