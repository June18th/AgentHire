package com.git.hui.jobclaw.core.configuration;

import com.git.hui.jobclaw.core.configuration.event.GlobalEnvConfigChangedEvent;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Component
public class ConfigurationManager {

    private final GlobalEnvConfigProcessor globalEnvConfigProcessor;
    private final ApplicationEventPublisher eventPublisher;

    private final EnvConfigRepository envConfigRepository;

    public ConfigurationManager(GlobalEnvConfigProcessor globalEnvConfigProcessor, Environment environment, ApplicationEventPublisher eventPublisher, EnvConfigRepository envConfigRepository) {
        this.globalEnvConfigProcessor = globalEnvConfigProcessor;
        this.eventPublisher = eventPublisher;
        this.envConfigRepository = envConfigRepository;
    }

    @Transactional
    public void updateProperties(Map<String, Object> keyValues) {
        // 持久化变更的配置
        keyValues.forEach((k, v) -> {
            if (v instanceof String) {
                envConfigRepository.saveOrUpdateConfig(k, (String) v, "string", "");
            } else if (v instanceof Integer) {
                envConfigRepository.saveOrUpdateConfig(k, String.valueOf(v), "int", "");
            } else if (v instanceof Boolean) {
                envConfigRepository.saveOrUpdateConfig(k, String.valueOf(v), "boolean", "");
            } else if (v instanceof Long) {
                envConfigRepository.saveOrUpdateConfig(k, String.valueOf(v), "long", "");
            } else if (v instanceof Double) {
                envConfigRepository.saveOrUpdateConfig(k, String.valueOf(v), "double", "");
            } else {
                envConfigRepository.saveOrUpdateConfig(k, JsonUtil.toStr(v), "json", "");
            }
        });

        eventPublisher.publishEvent(new GlobalEnvConfigChangedEvent(this, keyValues));
    }


    /**
     * 监听配置变更事件
     */
    @Async
    @EventListener
    public void onConfigChanged(GlobalEnvConfigChangedEvent event) {
        log.info("[GlobalEnvConfig] Received config change event, changed keys: {}",
                event.getChangedConfigs().keySet());
        globalEnvConfigProcessor.autoRefreshConfig(true);
    }

    /**
     * 定时刷新配置(每5分钟执行一次)
     * AIDEV-NOTE: 可根据实际需求调整cron表达式
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void refreshConfig() {
        globalEnvConfigProcessor.autoRefreshConfig(false);
    }
}
