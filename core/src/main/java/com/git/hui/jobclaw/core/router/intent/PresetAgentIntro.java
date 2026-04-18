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
    COLLECT("collect", "岗位信息收集", "收集和管理岗位信息", ""),

    /**
     * RECOMMEND: 推荐岗位
     * 触发场景：用户想要推荐、想让系统帮忙找岗位
     */
    RECOMMEND("recommend", "岗位推荐", "智能推荐匹配的岗位", ""),

    /**
     * SUBSCRIBE: 订阅推送
     * 触发场景：用户想订阅某个岗位的推送通知
     */
    SUBSCRIBE("subscribe", "订阅推送", "订阅岗位推送通知", ""),

    /**
     * QUERY: 信息查询
     * 触发场景：用户想查询某个信息（岗位状态、投递记录等）
     */
    QUERY("query", "信息查询", "查询各类信息", ""),

    /**
     * PROFILE: 用户画像管理
     * 触发场景：用户想修改个人信息、偏好设置
     */
    PROFILE("profile", "用户画像管理", "管理用户个人画像", ""),

    /**
     * CHAT: 通用聊天对话
     * 触发场景：用户想进行通用聊天对话
     */
    CHAT("chat", "通用聊天对话Agent", "通用对话聊天", ""),

    /**
     * PREFERENCE_SETTING: 用户偏好设置
     * 触发场景：用户想修改偏好设置
     */
    PREFERENCE_SETTING("preferenceAgent",
            """
                    preferenceAgent是 JobClaw 系统中专门负责用户个人偏好配置管理的业务 Agent。它通过自然语言交互方式,让用户可以便捷地查询和修改自己的个性化设置,包括默认模型选择、API Key 管理等核心配置。

                    🎯 核心能力
                    1. 默认模型管理
                    - 支持为不同类型的任务设置专属的 AI 模型
                    - 支持的模型类型:
                      - TXT - 文本对话
                      - VISION - 视觉理解
                      - IMAGE - 图片生成
                      - VIDEO - 视频处理
                      - EMBEDDING - 向量嵌入
                      - ASR - 语音识别
                      - TTS - 语音合成

                    2. API Key 管理
                        - 为用户级别的模型提供商添加/更新 API Key
                        - 支持的提供商: zhipu(智谱)、silicon(硅基流动)、ali(阿里)、openai、doubao(豆包) 等

                    3. 偏好查询
                        - 查看当前用户的完整偏好配置
                        - 自动对敏感信息(API Key)进行脱敏处理
                        
                    💬 使用示例
                                
                    场景 1: 修改默认文本模型：
                                
                    `帮我把默认的文本模型改成智谱的 GLM-5`
                                
                    场景 2: 添加新的 API Key
                                
                    `我要添加一个硅基流动的 API Key: sk-abc123def456`
                                
                    场景 3: 查询当前偏好设置
                                
                    `显示我的偏好设置`
                                
                    场景 4: 切换视觉模型
                                
                    `把视觉模型换成阿里的通义千问 qwen-vl-max`
                    """,
            "负责用户个人偏好配置管理的业务 Agent。它通过自然语言交互方式,让用户可以便捷地查询和修改自己的个性化设置,包括默认模型选择、API Key 管理等核心配置。",
            ""),

    // ===== 系统意图 =====

    /**
     * HELP: 帮助
     * 触发场景：用户请求帮助、使用帮助命令
     */
    HELP("help", "帮助", "查看帮助信息", "/help"),

    /**
     * LIST_AGENTS: 列出可用Agent
     * 触发场景：用户通过/agents命令查询可用的Agent列表
     */
    LIST_AGENTS("list_agents", "列出可用Agent", "查看所有可用Agent", "/agents"),

    /**
     * SWITCH_AGENT: 切换Agent
     * 触发场景：用户通过命令切换Agent
     */
    SWITCH_AGENT("switch_agent", "切换Agent", "切换到指定Agent", "/agent"),

    /**
     * RESET: 重置会话
     * 触发场景：用户想重置当前会话状态
     */
    RESET("reset", "重置会话", "重置当前会话", "/reset"),

    /**
     * CURRENT_AGENT: 查看当前Agent
     * 触发场景：用户通过/current命令查询当前会话绑定的Agent
     */
    CURRENT_AGENT("current_agent", "查看当前Agent", "查看当前Agent", "/current"),

    /**
     * UNKNOWN: 未知意图
     * 触发场景：无法识别用户意图
     */
    UNKNOWN("unknown", "未知意图", "未知意图", ""),

    /**
     * DEFAULT: 默认意图
     * 触发场景：无法识别用户意图
     */
    DEFAULT("default", "默认兜底的agent，可以用于处理所有的问答场景", "默认问答处理", "");;

    private final String agentId;
    private final String description;
    private final String intro;
    private final String command;

    PresetAgentIntro(String agentId, String description, String intro, String command) {
        this.agentId = agentId;
        this.description = description;
        this.intro = intro;
        this.command = command;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getDescription() {
        return description;
    }

    public String getIntro() {
        return intro;
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