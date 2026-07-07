package com.git.hui.jobclaw.components.mq;

import com.git.hui.jobclaw.configs.MqProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlatformEventPublisher {
    private final MqProperties mqProperties;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PlatformEventPublisher(MqProperties mqProperties, KafkaTemplate<String, Object> kafkaTemplate) {
        this.mqProperties = mqProperties;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishDomainEvent(String key, Object payload) {
        publish(mqProperties.getTopics().getDomainEvent(), key, payload);
    }

    public void publishLlmAuditEvent(String key, Object payload) {
        publish(mqProperties.getTopics().getLlmAudit(), key, payload);
    }

    private void publish(String topic, String key, Object payload) {
        if (!mqProperties.isEnabled()) {
            log.debug("MQ disabled, skip event topic={}, key={}", topic, key);
            return;
        }
        if (!"kafka".equalsIgnoreCase(mqProperties.getProvider())) {
            log.warn("Unsupported MQ provider={}, skip event topic={}, key={}", mqProperties.getProvider(), topic, key);
            return;
        }
        kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("Kafka publish failed, topic={}, key={}", topic, key, ex);
            }
        });
    }
}
