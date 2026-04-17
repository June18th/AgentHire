package com.git.hui.jobclaw.core.router.intent;

import com.git.hui.jobclaw.core.agent.BizAgent;

/**
 * 意图类型枚举
 * AIDEV-NOTE: 定义系统支持的所有意图类型，扩展时添加新枚举值
 *
 * @author YiHui
 * @date 2026/4/17
 */
public enum PresetAgentIntro implements BizAgent.AgentIntro {
    // ===== 业务意图 =====

    /**
     * COLLECT: 投递简历、收集岗位信息
     * 触发场景：用户想投递岗位、想录入岗位信息
     */
    COLLECT("collect", "岗位信息收集", ""),

    /**
     * RECOMMEND: 推荐岗位
     * 触发场景：用户想要推荐、想让系统帮忙找岗位
     */
    RECOMMEND("recommend", "岗位推荐", ""),

    /**
     * SUBSCRIBE: 订阅推送
     * 触发场景：用户想订阅某个岗位的推送通知
     */
    SUBSCRIBE("subscribe", "订阅推送", ""),

    /**
     * QUERY: 信息查询
     * 触发场景：用户想查询某个信息（岗位状态、投递记录等）
     */
    QUERY("query", "信息查询", ""),

    /**
     * PROFILE: 用户画像管理
     * 触发场景：用户想修改个人信息、偏好设置
     */
    PROFILE("profile", "用户画像管理", ""),


    CHAT("chat", "通用聊天对话Agent", ""),

    // ===== 系统意图 =====

    /**
     * HELP: 帮助
     * 触发场景：用户请求帮助、使用帮助命令
     */
    HELP("help", "帮助", "/help"),

    /**
     * LIST_AGENTS: 列出可用Agent
     * 触发场景：用户通过/agents命令查询可用的Agent列表
     */
    LIST_AGENTS("list_agents", "列出可用Agent", "/agents"),

    /**
     * SWITCH_AGENT: 切换Agent
     * 触发场景：用户通过命令切换Agent
     */
    SWITCH_AGENT("switch_agent", "切换Agent", "/agent"),

    /**
     * RESET: 重置会话
     * 触发场景：用户想重置当前会话状态
     */
    RESET("reset", "重置会话", "/reset"),

    /**
     * CURRENT_AGENT: 查看当前Agent
     * 触发场景：用户通过/current命令查询当前会话绑定的Agent
     */
    CURRENT_AGENT("current_agent", "查看当前Agent", "/current"),

    /**
     * UNKNOWN: 未知意图
     * 触发场景：无法识别用户意图
     */
    UNKNOWN("unknown", "未知意图", ""),

    /**
     * DEFAULT: 默认意图
     * 触发场景：无法识别用户意图
     */
    DEFAULT("default", "默认兜底的agent，可以用于处理所有的问答场景", "");;

    private final String agentId;
    private final String description;

    private final String command;

    PresetAgentIntro(String agentId, String description, String command) {
        this.agentId = agentId;
        this.description = description;
        this.command = command;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getDescription() {
        return description;
    }

    public String getCommand() {
        return command;
    }

    /**
     * 根据agentId获取枚举值
     */
    public static PresetAgentIntro fromAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return UNKNOWN;
        }
        for (PresetAgentIntro type : values()) {
            if (type.agentId.equalsIgnoreCase(agentId)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}