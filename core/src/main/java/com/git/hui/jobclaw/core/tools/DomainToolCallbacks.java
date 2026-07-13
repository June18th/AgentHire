package com.git.hui.jobclaw.core.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 域 Agent 工具回调合并工具。
 * AI-GENERATED
 */
public final class DomainToolCallbacks {

    private DomainToolCallbacks() {
    }

    /**
     * 合并业务工具、自动发现工具（如 Playwright）与 MCP Client 工具回调。
     */
    public static ToolCallback[] merge(Object primaryToolBean,
                                       List<AutoDiscoveredTool<?>> autoDiscoveredTools,
                                       ToolCallback[] mcpToolCallbacks) {
        List<ToolCallback> merged = new ArrayList<>();
        if (primaryToolBean != null) {
            merged.addAll(Arrays.asList(ToolCallbacks.from(primaryToolBean)));
        }
        if (autoDiscoveredTools != null) {
            for (AutoDiscoveredTool<?> tool : autoDiscoveredTools) {
                if (tool != null && tool.tool() != null) {
                    merged.addAll(Arrays.asList(ToolCallbacks.from(tool.tool())));
                }
            }
        }
        if (mcpToolCallbacks != null && mcpToolCallbacks.length > 0) {
            merged.addAll(Arrays.asList(mcpToolCallbacks));
        }
        return merged.toArray(new ToolCallback[0]);
    }

    /**
     * 仅保留 Playwright 相关的自动发现工具，避免 JobFetch 误注入职位库/计划本等域外工具。
     */
    public static List<AutoDiscoveredTool<?>> playwrightOnly(List<AutoDiscoveredTool<?>> autoDiscoveredTools) {
        if (autoDiscoveredTools == null || autoDiscoveredTools.isEmpty()) {
            return List.of();
        }
        return autoDiscoveredTools.stream()
                .filter(tool -> tool != null
                        && tool.tool() != null
                        && tool.tool().getClass().getName().contains("playwright"))
                .toList();
    }
}
