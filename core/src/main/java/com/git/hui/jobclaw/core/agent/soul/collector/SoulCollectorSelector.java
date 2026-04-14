package com.git.hui.jobclaw.core.agent.soul.collector;

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
public class SoulCollectorSelector {
    private final Map<SoulCollector.CollectorType, SoulCollector> collectorMap;

    private final AiUserPreferenceProperties aiUserPreferenceProperties;


    public SoulCollectorSelector(List<SoulCollector> collectors,
                                 AiUserPreferenceProperties aiUserPreferenceProperties) {
        this.collectorMap = collectors.stream().collect(Collectors.toMap(SoulCollector::getCollectorType,
                collector -> collector));
        this.aiUserPreferenceProperties = aiUserPreferenceProperties;
    }

    public SoulCollector getCollector(String jobClawUserId) {
        var tmp = aiUserPreferenceProperties.getPreference().stream()
                .filter(entry -> entry.getUserId().equals(jobClawUserId))
                .findFirst();
        if (tmp.isPresent()) {
            var collectorType = tmp.get().getCollector();
            if (collectorType == null) {
                collectorType = SoulCollector.CollectorType.AI_BASED;
            }
            return collectorMap.get(collectorType);
        } else {
            return collectorMap.get(SoulCollector.CollectorType.RULE_BASED);
        }
    }
}
