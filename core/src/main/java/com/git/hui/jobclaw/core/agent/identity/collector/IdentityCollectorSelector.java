package com.git.hui.jobclaw.core.agent.identity.collector;

import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户画像收集
 * @author YiHui
 * @date 2026/4/14
 */
@Component
public class IdentityCollectorSelector {
    private final Map<IdentityCollector.CollectorType, IdentityCollector> collectorMap;

    private final AiUserPreferenceProperties aiUserPreferenceProperties;


    public IdentityCollectorSelector(List<IdentityCollector> collectors,
                                     AiUserPreferenceProperties aiUserPreferenceProperties) {
        this.collectorMap = collectors.stream().collect(Collectors.toMap(IdentityCollector::getCollectorType,
                collector -> collector));
        this.aiUserPreferenceProperties = aiUserPreferenceProperties;
    }

    public IdentityCollector getCollector(String jobClawUserId) {
        var tmp = aiUserPreferenceProperties.getPreference().stream()
                .filter(entry -> entry.getUserId().equals(jobClawUserId))
                .findFirst();
        if (tmp.isPresent()) {
            var collectorType = tmp.get().getCollector();
            if (collectorType == null) {
                collectorType = IdentityCollector.CollectorType.AI_BASED;
            }
            return collectorMap.get(collectorType);
        } else {
            return collectorMap.get(IdentityCollector.CollectorType.RULE_BASED);
        }
    }
}
