# 求职派 OpenClaw 式多 Agent 架构总览

> 当前版本说明：求职派已经从早期“岗位信息采集展示应用”，演进为一个类似 OpenClaw 的多 Agent 实战项目。下面这张图可以作为当前架构的代表性视图：它不从某一个单点功能出发，而是按 IM 入口、消息网关、对话管理、业务 Agent、工具、模型、任务调度和消息推送来理解整个系统。

![求职派当前多 Agent 架构](../../imgs/jobclaw-current-agent-architecture.png)

## 一、整体分层

这张图可以拆成八层：

| 层级 | 作用 | 当前代码映射 |
| --- | --- | --- |
| IM 渠道层 | 接入微信、飞书、钉钉和其他 Webhook/API | `channels/` |
| 消息网关适配器 | 统一消息格式、签名校验、用户 ID 映射 | `Channel`、`AbsChannel`、`ChannelReceiveMessage` |
| 对话管理 Agent | 意图识别、会话状态维护、路由到具体 Agent | `MsgRouter`、`IntentClassifier`、`SessionAgentBinder`、`AgentRouter` |
| 业务 Agent | 承载具体求职业务能力 | `agents/` 和 `core/agent/impl/` |
| 工具调用 Agent | MCP 客户端、搜索/抓取、文件 IO、浏览器等工具 | `plugins/`、`AutoDiscoveredTool`、Spring AI Tool Calling |
| 模型路由 Agent | 多模型切换、用户模型偏好、Token 统计入口 | `ModelProviders`、`ModelProvider`、`LlmCaller` |
| 任务调度 Agent | 定时任务、周期任务、心跳、推送触发 | `TaskManager`、JobRunr、Spring `@Scheduled` |
| 消息推送 | 把 Agent 结果送回 IM API | `ChannelEventPublisher`、`MessageResponseEvent`、`Channel.responseToUser()` |

图中的 Redis/Quartz 表示典型架构位点：对话状态可以替换成 Redis，任务调度可以替换成 Quartz。当前仓库里，会话绑定主要由 `FileSystemSessionAgentBinder` 实现，任务调度主要由 JobRunr 和 Spring `@Scheduled` 承担；这些实现不影响整体分层。

## 二、核心链路

```text
IM 渠道层
  -> 消息网关适配器
  -> 对话管理 Agent
     -> 校招采集 Agent
     -> 推荐 Agent
     -> 用户画像 Agent
     -> 校招信息管理 Agent
  -> 工具调用 Agent / 模型路由 Agent / 任务调度 Agent
  -> 消息推送
```

在代码中，对应的主链路是：

```text
AbsChannel.processMessage()
  -> ChannelEventPublisher.publishMessageReceived()
  -> MsgRouter.onMessageReceived()
     1. IIdentityAgent.triggerToCollectIdentity()
     2. SystemCommandDispatcher
     3. SessionAgentBinder.needsIntentRecognition()
     4. IntentClassifier.classify()
     5. AgentRouter.route()
     6. SessionAgentBinder.bind()
     7. BizAgent.process() / BizAgent.stream()
  -> ChannelEventPublisher.publishMessageResponse()
  -> Channel.responseToUser()
```

`MsgRouter` 是这条链路里最核心的“对话管理 Agent”实现：它并不直接处理所有业务，而是负责把用户消息送到最合适的业务 Agent。

## 三、业务 Agent

当前架构里的业务 Agent 可以按职责理解：

| 图中 Agent | 当前实现 | 说明 |
| --- | --- | --- |
| 校招采集 Agent | `JobFetchAgent` | 文件解析、图片 OCR、URL 抓取、文本提取、岗位草稿生成 |
| 推荐 Agent | `JobRecommendAgent` | 基于用户画像和职位库做实时召回、排序和推荐 |
| 用户画像 Agent | `identity-collector-agent` | 偏好存储、行为记录、画像信息采集；向量检索是画像能力的扩展方向 |
| 校招信息管理 Agent | `app/oc`、`app/gather`、`app/agents` | 职位 CRUD、去重/更新、过期清理、草稿清洗与发布 |
| 偏好设置 Agent | `PreferenceSettingBizAgent` | 通过自然语言管理模型、API Key 和渠道偏好 |
| 任务提醒 Agent | `TaskBizAgent` | 调度一次性或周期性任务 |
| 通用聊天 Agent | `CustomChatBizAgent`、`SimpleDefaultBizAgent` | 兜底问答和通用对话 |

业务 Agent 统一实现 `BizAgent`，通常继承 `AbsBizAgent`。新增 Agent 时，优先保持这个扩展方式：

```text
新增模块
  -> 实现 BizAgent
  -> 声明 AgentIntro 和 supported intents
  -> 挂载工具 getTools()
  -> 交给 Spring Bean 自动注册
  -> 在 PresetAgentIntro / AgentRegistry 中补充路由关系
```

## 四、工具与模型

工具调用层解决的是“模型自己做不了，但系统可以做”的事：

- 搜索/抓取：职位页面、网页内容、外部信息源。
- 文件 IO：PDF、Word、Excel、CSV、图片等输入处理。
- MCP 客户端：接入外部工具生态。
- 浏览器能力：通过 Playwright 处理 JS 渲染页面。

模型路由层解决的是“不同用户、不同任务使用哪个模型”的问题：

- 文本模型、视觉模型、Embedding、ASR、TTS 等按 `ModelConfig.ModelType` 区分。
- 用户偏好格式为 `provider#ModelName`，例如 `zhipufree#GLM-4-Flash`。
- Provider 接入放在 `providers/`，业务 Agent 不直接绑定具体模型厂商。

## 五、模块边界

```text
JobClaw/
├── channels/     # IM 渠道层和消息适配
├── core/         # 对话管理、Agent 抽象、路由、模型、记忆、任务
├── agents/       # 独立业务 Agent
├── providers/    # 模型 Provider
├── plugins/      # 工具能力
├── app/          # Web/Admin/职位数据/用户/支付/MCP Server
└── ui-react/     # 前端
```

新增能力时按下表选落点：

| 需求 | 推荐落点 |
| --- | --- |
| 新接一个 IM 或 Webhook 入口 | `channels/{new-channel}` |
| 新增一个可对话业务角色 | `agents/{new-agent}` |
| 新接一个模型厂商 | `providers/{new-provider}` |
| 新增一个可被模型调用的工具 | `plugins/{new-plugin}` |
| 新增后台职位数据流程 | `app/src/main/java/com/git/hui/jobclaw/` |

这样才能保持这张图表达的主线：入口归入口，网关归网关，对话管理归核心内核，业务 Agent 只处理业务，工具和模型能力独立扩展。
