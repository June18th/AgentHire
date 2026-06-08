# JobClaw Agent 能力升级方案

> 参照 [AgentScope Java](https://github.com/agentscope-ai/agentscope-java) 核心实现，结合 JobClaw 当前架构制定的渐进式改进计划。

---

## 一、现状分析

### 1.1 JobClaw 当前 Agent 架构

```
IM Message → AbsChannel → ChannelEventPublisher → MsgRouter
  → IIdentityAgent (身份采集)
  → SystemCommandDispatcher (/help, /agent, /reset)
  → SessionAgentBinder (会话绑定)
  → IntentClassifier (意图分类)
  → AgentRouter (路由)
  → BizAgent.process() / stream()
  → ChannelEventPublisher → Channel.responseToUser()
```

**核心抽象：**
- `BizAgent` 接口：定义 `process()` / `stream()` / `getAgentIntro()`
- `AbsBizAgent`：持有 `ModelProviders`、`ChatMemory`、`BizAgentLlmCaller`
- `SmartWindowChatMemory`：基于 token 计数 + 消息数量 + 短消息过滤的窗口管理
- `MsgRouter`：消息路由中枢，串联身份采集 → 命令分发 → 意图识别 → Agent 路由

**当前不足：**
1. Agent 内部没有推理-行动循环，工具调用为一次性 Fire-and-Forget
2. 记忆管理仅有滑动窗口，缺少摘要压缩和长期记忆
3. Agent 生命周期无拦截机制，无法注入横切关注点（日志、审计、限流）
4. 无结构化任务分解能力，复杂任务只能由单一 Agent 一口气完成
5. Agent 能力无法沉淀复用，每次对话结束后经验丢失
6. 缺少 Human-in-the-Loop 控制，无法对敏感操作进行人工确认

### 1.2 AgentScope Java 核心设计提炼

| 模块 | 核心思想 | JobClaw 差距 |
|------|---------|-------------|
| **ReAct Loop** | reasoning → acting → reasoning 迭代循环，直到任务完成或达到上限 | 无循环，单次 LLM 调用 |
| **Middleware** | 洋葱模型拦截 Agent/Reasoning/Acting/ModelCall 四个阶段 | 无拦截机制 |
| **AgentState** | 统一的 `AgentState` 对象持有 context、toolContext、permissionContext，通过 Session 持久化 | 状态分散在 ChatMemory + SessionAgentBinder |
| **Memory 分层** | 短期 Memory (InMemory) + 长期 LongTermMemory (语义检索) + 记忆压缩 | 仅 SmartWindow 滑动窗口 |
| **PlanNotebook** | 结构化任务分解为 SubTask，状态追踪 todo/in_progress/done | 无任务分解 |
| **SkillBox** | Markdown 技能文件 + 动态加载/卸载 + 工具组联动 | 无能力沉淀 |
| **Permission & HITL** | PermissionEngine + ConfirmResult 机制实现工具级权限控制和人工确认 | 无确认机制 |
| **Structured Output** | 自纠正输出解析，失败时自动重试并引导模型 | 无结构化输出 |
| **Graceful Shutdown** | 中断保护 + 状态保存 + 恢复执行 | 无中断保护 |

---

## 二、升级方案总览

### 阶段路线图

```
Phase 1: ReAct 推理循环 (核心引擎升级)
  ↓
Phase 2: Middleware 生命周期拦截 (可观测性 + 横切关注点)
  ↓
Phase 3: 记忆压缩与分层管理 (上下文增强)
  ↓
Phase 4: PlanNotebook 任务分解 (复杂任务编排)
  ↓
Phase 5: Skill 能力沉淀 (经验复用)
  ↓
Phase 6: HITL 人工确认 + 权限控制 (生产安全)
```

---

## 三、Phase 1：ReAct 推理循环

### 3.1 设计目标

将 JobClaw 当前"单次 LLM 调用 + 工具调用"模式升级为 ReAct（Reasoning-Acting）迭代循环。Agent 在每轮中：
1. **Reasoning**：基于上下文和工具 schema 调用 LLM，产出思考和工具调用计划
2. **Acting**：执行工具调用，收集结果
3. **判断**：是否完成（无工具调用）、达到最大迭代次数、或需要继续推理

### 3.2 核心设计

#### 3.2.1 ReActAgent 抽象

参照 AgentScope 的 `ReActAgent`，在 `core/agent/impl/` 下新增 `ReActBizAgent`：

```java
// core/agent/impl/ReActBizAgent.java
public abstract class ReActBizAgent extends AbsBizAgent {

    private final int maxIters;           // 最大推理轮数，默认 10
    private final ReactConfig reactConfig; // ReAct 配置

    /**
     * ReAct 核心循环：
     * reasoning(iter) → acting(iter) → reasoning(iter+1) → ... → 完成/超限
     */
    protected Mono<String> reactLoop(List<Message> messages) {
        return executeIteration(messages, 0);
    }

    private Mono<String> executeIteration(List<Message> messages, int iter) {
        if (iter >= maxIters) {
            return summarizing(messages); // 超限总结
        }
        return reasoning(messages, iter)
            .flatMap(reasoningResult -> {
                if (reasoningResult.hasToolCalls()) {
                    return acting(reasoningResult.getToolCalls())
                        .flatMap(toolResults -> {
                            messages.add(reasoningResult.toMessage());
                            messages.addAll(toolResults);
                            return executeIteration(messages, iter + 1);
                        });
                }
                return Mono.just(reasoningResult.getText()); // 无工具调用 = 完成
            });
    }
}
```

**关键设计点（参照 AgentScope `coreAgent()` + `executeIteration()`）：**

- `reasoning()` 调用 LLM 并解析返回中的 tool_calls
- `acting()` 执行工具并将结果写入上下文
- `summarizing()` 超限时生成总结性回复
- 每轮判断中断信号（`checkInterruptedAsync`）
- 支持 `gotoReasoning` 回退（结构化输出失败时重新推理）

#### 3.2.2 与现有 BizAgent 的兼容

```
BizAgent (接口，保持不变)
  └── AbsBizAgent (基类，保持不变)
        ├── 现有 Agent (IdentityCollectorAgent, JobFetchAgent...) — 保持单次调用
        └── ReActBizAgent (新增)
              └── 新的业务 Agent 继承此类
```

**改造策略：** 新增 `ReActBizAgent` 作为 `AbsBizAgent` 的子类，现有 Agent 无需改动，新业务 Agent 选择继承。

#### 3.2.3 BizAgentLlmCaller 改造

当前的 `BizAgentLlmCaller` 需要支持"带工具 schema 的 LLM 调用"：

```java
// 新增方法
public LlmResponse callWithTools(List<Message> messages, List<ToolSchema> tools);
public Flux<LlmResponseChunk> streamWithTools(List<Message> messages, List<ToolSchema> tools);
```

参照 AgentScope 的 `Model.stream(messages, tools, options)` 模式，将工具 schema 注入 LLM 调用。

### 3.3 落地改动清单

| 文件 | 改动 |
|------|-----|
| `core/agent/impl/ReActBizAgent.java` | **新增** - ReAct 循环基类 |
| `core/agent/config/ReactConfig.java` | **新增** - ReAct 配置（maxIters、stopOnReject） |
| `core/agent/llm/BizAgentLlmCaller.java` | **修改** - 增加 `callWithTools` / `streamWithTools` |
| `core/agent/tool/ToolSchema.java` | **新增** - 工具 schema 描述 |
| `core/agent/tool/Toolkit.java` | **新增** - 工具注册与管理 |
| `core/agent/llm/ToolCallParser.java` | **新增** - 解析 LLM 返回中的 tool_calls |

---

## 四、Phase 2：Middleware 生命周期拦截

### 4.1 设计目标

参照 AgentScope 2.0 的 `MiddlewareBase` 接口，为 JobClaw Agent 引入洋葱模型拦截机制，覆盖 Agent 执行全生命周期。

### 4.2 核心设计

#### 4.2.1 Middleware 接口

```java
// core/agent/middleware/AgentMiddleware.java
public interface AgentMiddleware {

    /** 拦截整个 Agent 调用 */
    default Flux<AgentEvent> onAgent(
            BizAgent agent, AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        return next.apply(input);
    }

    /** 拦截推理阶段（LLM 调用） */
    default Flux<AgentEvent> onReasoning(
            BizAgent agent, ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {
        return next.apply(input);
    }

    /** 拦截工具执行阶段 */
    default Flux<AgentEvent> onActing(
            BizAgent agent, ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {
        return next.apply(input);
    }

    /** 转换系统提示词（管道模式） */
    default Mono<String> onSystemPrompt(BizAgent agent, String currentPrompt) {
        return Mono.just(currentPrompt);
    }
}
```

#### 4.2.2 MiddlewareChain 构建

参照 AgentScope 的 `MiddlewareChain.build()`，实现洋葱模型链式调用：

```java
// core/agent/middleware/MiddlewareChain.java
public class MiddlewareChain {
    public static <I, O> Function<I, O> build(
            List<AgentMiddleware> middlewares,
            BizAgent agent,
            MiddlewareInvoker<I, O> invoker,
            Function<I, O> core) {
        Function<I, O> next = core;
        for (int i = middlewares.size() - 1; i >= 0; i--) {
            final var mw = middlewares.get(i);
            final var currentNext = next;
            next = input -> invoker.invoke(mw, agent, input, currentNext);
        }
        return next;
    }
}
```

#### 4.2.3 内置 Middleware

| Middleware | 用途 | 对应 AgentScope |
|-----------|------|----------------|
| `LoggingMiddleware` | 记录每轮推理和工具调用的日志 | - |
| `TokenCountMiddleware` | Token 使用量统计 | - |
| `RateLimitMiddleware` | 推理调用限流 | - |
| `GracefulShutdownMiddleware` | 优雅停机保护 | `GracefulShutdownMiddleware` |
| `TaskReminderMiddleware` | 每轮注入 PlanNotebook 进度提示 | `TaskReminderMiddleware` |
| `PlanHintMiddleware` | 注入计划上下文到系统提示 | `PlanHintMiddleware` |

### 4.3 与 MsgRouter 的集成

在 `MsgRouter` 路由到 Agent 后，Agent 执行过程经过 Middleware 链：

```java
// MsgRouter.routeToAgent() 中
List<AgentMiddleware> middlewares = resolveMiddlewares(agent);
Function<AgentInput, Flux<AgentEvent>> core = input -> agent.stream(input);
Flux<AgentEvent> stream = MiddlewareChain.build(middlewares, agent, AgentMiddleware::onAgent, core)
    .apply(new AgentInput(msgs));
```

### 4.4 落地改动清单

| 文件 | 改动 |
|------|-----|
| `core/agent/middleware/AgentMiddleware.java` | **新增** |
| `core/agent/middleware/MiddlewareChain.java` | **新增** |
| `core/agent/middleware/AgentInput.java` | **新增** |
| `core/agent/middleware/ReasoningInput.java` | **新增** |
| `core/agent/middleware/ActingInput.java` | **新增** |
| `core/agent/middleware/impl/LoggingMiddleware.java` | **新增** |
| `core/agent/middleware/impl/TokenCountMiddleware.java` | **新增** |
| `core/router/MsgRouter.java` | **修改** - 集成 MiddlewareChain |

---

## 五、Phase 3：记忆压缩与分层管理

### 5.1 设计目标

将 JobClaw 当前的 `SmartWindowChatMemory`（纯滑动窗口）升级为三层记忆架构：

```
┌─────────────────────────────────┐
│       Working Memory            │  ← 当前对话窗口（SmartWindow 升级）
│   短期上下文，滑动窗口 + 压缩    │
├─────────────────────────────────┤
│       Episodic Memory           │  ← 会话级记忆（新增）
│   对话摘要、关键决策、工具结果    │
├─────────────────────────────────┤
│       Long-term Memory          │  ← 跨会话持久记忆（新增）
│   用户偏好、技能画像、经验知识    │
└─────────────────────────────────┘
```

### 5.2 核心设计

#### 5.2.1 Working Memory 增强 — 摘要压缩

参照 AgentScope 的 `InMemoryMemory` + 摘要机制，在窗口管理中引入"先压缩后丢弃"策略：

```java
// core/agent/memory/CompressingWindowMemory.java
public class CompressingWindowMemory {

    private final SmartWindowChatMemory windowMemory;   // 现有窗口管理
    private final LlmCaller summarizer;                  // 摘要 LLM

    /**
     * 升级策略：
     * 1. 当消息数超过 softLimit（如 20 条），对最早的 N 条进行摘要压缩
     * 2. 将摘要作为 system message 注入后续对话
     * 3. 只在摘要也放不下时，才执行硬截断
     */
    public List<Message> manage(List<Message> messages) {
        if (messages.size() <= softLimit) {
            return messages;
        }

        // Step 1: 对早期消息进行摘要压缩
        List<Message> earlyMessages = messages.subList(0, messages.size() - keepRecent);
        String summary = summarize(earlyMessages);

        // Step 2: 构建压缩后的消息列表
        List<Message> result = new ArrayList<>();
        result.add(Message.system("[历史对话摘要]\n" + summary));
        result.addAll(messages.subList(messages.size() - keepRecent, messages.size()));
        return result;
    }

    private String summarize(List<Message> messages) {
        String prompt = "请将以下对话内容压缩为简洁的摘要，保留关键信息、决策和结论：\n"
            + formatMessages(messages);
        return summarizer.call(prompt);
    }
}
```

**对比现状：** 当前 `SmartWindowChatMemory` 只做 token 截断和短消息过滤，信息丢失严重。引入摘要压缩后，关键上下文得以保留。

#### 5.2.2 Episodic Memory — 会话级记忆

参照 AgentScope 的 `LongTermMemory.record()` / `retrieve()` 模式：

```java
// core/agent/memory/EpisodicMemory.java
public interface EpisodicMemory {

    /**
     * 记录本次对话的关键信息（对话结束后自动调用）
     * 参照 AgentScope LongTermMemory.record()
     */
    Mono<Void> record(String userId, List<Message> conversation);

    /**
     * 检索与当前查询相关的历史记忆（推理前自动调用）
     * 参照 AgentScope LongTermMemory.retrieve()
     */
    Mono<String> retrieve(String userId, Message query);
}
```

**实现方案：**
- 基于 H2/MySQL 存储，每次对话结束后提取关键信息（意图、结论、工具结果）
- 使用 Embedding 做语义检索，或简单的关键词匹配作为起步
- 集成方式：作为 Middleware 在 `onAgent` 阶段自动 record/retrieve

#### 5.2.3 Long-term Memory — 跨会话持久记忆

这是 JobClaw 已有的 `identity-collector-agent`（soul.md / user.md / info.md）的自然延伸：

```java
// core/agent/memory/LongTermMemoryService.java
public class LongTermMemoryService {

    /**
     * 从对话中自动提取并更新长期记忆
     * - 用户偏好变化
     * - 新的技能/经验
     * - 求职状态更新
     */
    public Mono<Void> updateFromConversation(String userId, List<Message> conversation);

    /**
     * 获取用户完整画像（整合 soul + identity + info + episodic）
     */
    public String getUserProfile(String userId);
}
```

**与现有系统的整合：** JobClaw 的 `workspace/users/{userId}/` 目录已经存储了 soul.md、identity.md、info.md，LongTermMemoryService 在此基础上增加自动更新和检索能力。

### 5.3 记忆管理 Middleware

```java
// core/agent/middleware/impl/MemoryMiddleware.java
public class MemoryMiddleware implements AgentMiddleware {

    @Override
    public Flux<AgentEvent> onAgent(BizAgent agent, AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        String userId = input.userId();

        // 推理前：检索相关记忆，注入到系统提示
        return episodicMemory.retrieve(userId, input.lastMessage())
            .flatMap(memory -> {
                String enhancedPrompt = agent.getSystemPrompt()
                    + "\n\n[相关历史记忆]\n" + memory;
                input = input.withEnhancedSystemPrompt(enhancedPrompt);
                return next.apply(input);
            })
            // 推理后：记录本次对话
            .flatMap(result -> episodicMemory.record(userId, input.messages())
                .thenReturn(result));
    }
}
```

### 5.4 落地改动清单

| 文件 | 改动 |
|------|-----|
| `core/agent/memory/CompressingWindowMemory.java` | **新增** - 摘要压缩窗口管理 |
| `core/agent/memory/EpisodicMemory.java` | **新增** - 会话级记忆接口 |
| `core/agent/memory/JdbcEpisodicMemory.java` | **新增** - JDBC 实现 |
| `core/agent/memory/LongTermMemoryService.java` | **新增** - 长期记忆服务 |
| `core/agent/memory/MemoryConfig.java` | **新增** - 记忆配置 |
| `core/agent/middleware/impl/MemoryMiddleware.java` | **新增** - 记忆拦截器 |
| `core/agent/memory/SmartWindowChatMemory.java` | **修改** - 作为 CompressingWindowMemory 的底层 |

---

## 六、Phase 4：PlanNotebook 任务分解

### 6.1 设计目标

参照 AgentScope 的 `PlanNotebook`，为 JobClaw Agent 引入结构化任务分解能力。当面对复杂任务时，Agent 可以：
1. 创建计划（Plan），将目标分解为有序子任务（SubTask）
2. 追踪子任务状态（todo → in_progress → done / abandoned）
3. 每轮推理自动注入计划进度提示（Hint）

### 6.2 核心设计

#### 6.2.1 PlanNotebook

```java
// core/agent/plan/PlanNotebook.java
public class PlanNotebook {

    private Plan currentPlan;

    /** 创建计划 */
    @Tool(description = "创建一个结构化任务计划")
    public String createPlan(
        @ToolParam(name = "name") String name,
        @ToolParam(name = "subtasks") List<String> subtaskDescriptions) {
        this.currentPlan = Plan.builder()
            .name(name)
            .subtasks(subtaskDescriptions.stream()
                .map(desc -> SubTask.of(desc, SubTaskState.TODO))
                .toList())
            .build();
        return "计划已创建：" + formatPlan(currentPlan);
    }

    /** 更新子任务状态 */
    @Tool(description = "更新当前子任务状态")
    public String updateSubtaskState(
        @ToolParam(name = "index") int index,
        @ToolParam(name = "state") String state) {
        currentPlan.getSubtasks().get(index).setState(SubTaskState.valueOf(state));
        return "子任务 " + index + " 状态已更新为 " + state;
    }

    /** 完成当前子任务 */
    @Tool(description = "标记当前子任务为完成")
    public String finishSubtask(@ToolParam(name = "index") int index) {
        return updateSubtaskState(index, "DONE");
    }

    /** 生成计划进度提示 */
    public String generateHint() {
        if (currentPlan == null) return "";
        return "<system-hint>\n当前计划: " + currentPlan.getName()
            + "\n进度: " + formatProgress(currentPlan)
            + "\n下一步: " + getNextAction(currentPlan)
            + "\n</system-hint>";
    }
}
```

#### 6.2.2 PlanHintMiddleware

参照 AgentScope 的 `PlanHintMiddleware`，在每轮推理前自动注入计划进度：

```java
// core/agent/middleware/impl/PlanHintMiddleware.java
public class PlanHintMiddleware implements AgentMiddleware {

    @Override
    public Mono<String> onSystemPrompt(BizAgent agent, String currentPrompt) {
        String hint = planNotebook.generateHint();
        if (hint.isEmpty()) return Mono.just(currentPrompt);
        return Mono.just(currentPrompt + "\n\n" + hint);
    }
}
```

### 6.3 JobClaw 场景适配

将 PlanNotebook 应用到 JobClaw 的具体业务场景：

| 场景 | Plan 示例 |
|------|----------|
| 岗位数据采集 | Plan: 采集 XX 公司校招 → [确定目标公司, 搜索岗位, 提取详情, 清洗入库] |
| 个性化推荐 | Plan: 为 XX 推荐岗位 → [分析用户画像, 搜索匹配岗位, 排序推荐, 生成推荐语] |
| 公众号发文 | Plan: 发布校招推文 → [汇总最新岗位, 筛选亮点, 撰写推文, 审核发布] |

### 6.4 落地改动清单

| 文件 | 改动 |
|------|-----|
| `core/agent/plan/PlanNotebook.java` | **新增** |
| `core/agent/plan/model/Plan.java` | **新增** |
| `core/agent/plan/model/SubTask.java` | **新增** |
| `core/agent/plan/model/SubTaskState.java` | **新增** |
| `core/agent/middleware/impl/PlanHintMiddleware.java` | **新增** |

---

## 七、Phase 5：Skill 能力沉淀

### 7.1 设计目标

参照 AgentScope 的 `SkillBox`，为 JobClaw 引入技能沉淀机制。Agent 在完成任务后，可以将成功的执行策略沉淀为可复用的技能文件（Markdown），后续遇到类似任务时自动加载。

### 7.2 核心设计

#### 7.2.1 Skill 定义

```java
// core/agent/skill/AgentSkill.java
@Data
@Builder
public class AgentSkill {
    private String skillId;          // 唯一标识
    private String name;             // 技能名称
    private String description;      // 技能描述
    private String content;          // Markdown 内容（执行步骤、提示词模板等）
    private List<String> tags;       // 标签（用于检索）
    private String triggerPattern;   // 触发模式（关键词/意图匹配）
    private LocalDateTime createdAt;
    private int usageCount;          // 使用次数
}
```

#### 7.2.2 SkillBox — 技能管理

```java
// core/agent/skill/SkillBox.java
public class SkillBox {

    private final Map<String, AgentSkill> skills = new ConcurrentHashMap<>();
    private final Path skillDir;  // workspace/skills/

    /** 注册技能 */
    public void registerSkill(AgentSkill skill) {
        skills.put(skill.getSkillId(), skill);
        persistSkill(skill); // 写入 workspace/skills/ 目录
    }

    /** 根据用户消息检索相关技能 */
    public List<AgentSkill> findRelevantSkills(String userMessage) {
        return skills.values().stream()
            .filter(skill -> matchesSkill(skill, userMessage))
            .sorted(Comparator.comparingInt(AgentSkill::getUsageCount).reversed())
            .limit(3)
            .toList();
    }

    /** 获取技能系统提示词（注入到 Agent 的系统提示中） */
    public String getSkillPrompt(List<AgentSkill> relevantSkills) {
        if (relevantSkills.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("你可以参考以下已有技能来辅助完成任务：\n");
        for (AgentSkill skill : relevantSkills) {
            sb.append("## ").append(skill.getName()).append("\n");
            sb.append(skill.getContent()).append("\n\n");
        }
        return sb.toString();
    }
}
```

#### 7.2.3 SkillSedimentMiddleware

```java
// core/agent/middleware/impl/SkillSedimentMiddleware.java
public class SkillSedimentMiddleware implements AgentMiddleware {

    @Override
    public Flux<AgentEvent> onAgent(BizAgent agent, AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        // 推理前：检索并注入相关技能
        List<AgentSkill> skills = skillBox.findRelevantSkills(input.lastMessage().getText());
        if (!skills.isEmpty()) {
            String skillPrompt = skillBox.getSkillPrompt(skills);
            input = input.withEnhancedSystemPrompt(
                input.systemPrompt() + "\n\n" + skillPrompt);
        }
        return next.apply(input);
    }

    @Override
    public Mono<String> onSystemPrompt(BizAgent agent, String currentPrompt) {
        // 管道模式：转换系统提示词
        return Mono.just(currentPrompt);
    }
}
```

#### 7.2.4 能力沉淀工具

为 Agent 提供一个 `@Tool` 方法，允许 Agent 主动沉淀成功经验：

```java
@Tool(description = "将当前成功的执行策略沉淀为可复用技能")
public String saveSkill(
    @ToolParam(name = "name") String skillName,
    @ToolParam(name = "description") String description,
    @ToolParam(name = "steps") String executionSteps) {
    AgentSkill skill = AgentSkill.builder()
        .skillId(generateId())
        .name(skillName)
        .description(description)
        .content(executionSteps)
        .tags(extractTags(description))
        .build();
    skillBox.registerSkill(skill);
    return "技能已沉淀：" + skillName;
}
```

### 7.3 与现有 workspace/skills 整合

JobClaw 已有 `workspace/skills/skill-creator/` 目录，SkillBox 在此基础上：
- 统一技能文件的存储和检索
- 支持技能的动态加载和热更新
- 提供使用频率统计和淘汰机制

### 7.4 落地改动清单

| 文件 | 改动 |
|------|-----|
| `core/agent/skill/AgentSkill.java` | **新增** |
| `core/agent/skill/SkillBox.java` | **新增** |
| `core/agent/skill/SkillToolFactory.java` | **新增** - 将技能注册为 Agent 工具 |
| `core/agent/middleware/impl/SkillSedimentMiddleware.java` | **新增** |

---

## 八、Phase 6：HITL 人工确认与权限控制

### 8.1 设计目标

参照 AgentScope 的 `PermissionEngine` + `ConfirmResult` 机制，为 JobClaw Agent 的工具调用引入人工确认流程。

### 8.2 核心设计

#### 8.2.1 ToolPermission 注解

```java
// core/agent/tool/annotation/RequireConfirm.java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireConfirm {
    String message() default "此操作需要您的确认";
    ConfirmLevel level() default ConfirmLevel.NORMAL;
}

public enum ConfirmLevel {
    NORMAL,    // 普通确认
    SENSITIVE, // 敏感操作（如数据删除、发布）
    CRITICAL   // 关键操作（如支付、对外发送）
}
```

#### 8.2.2 确认流程

参照 AgentScope 的 `ConfirmResult` + `applyConfirmResults()` 模式：

```
Agent 推理产出工具调用
  ↓
PermissionEngine 评估每个工具调用
  ↓
├── ALLOW → 直接执行
├── ASK   → 暂停 Agent，向用户发送确认请求
└── DENY  → 拒绝执行，返回拒绝结果
  ↓
用户确认/拒绝
  ↓
Agent 恢复执行（携带确认结果）
```

#### 8.2.3 IM 渠道集成

利用 JobClaw 现有的 IM 渠道（WeChat/DingDing/FeiShu）发送确认卡片：

```java
// 当 Agent 暂停等待确认时
channel.sendConfirmCard(userId, ConfirmCard.builder()
    .title("操作确认")
    .description("Agent 希望执行以下操作：发布校招信息到公众号")
    .toolName("publishToWechat")
    .parameters(Map.of("title", "XX公司2026校招", "content", "..."))
    .build());
```

### 8.4 落地改动清单

| 文件 | 改动 |
|------|-----|
| `core/agent/tool/annotation/RequireConfirm.java` | **新增** |
| `core/agent/permission/PermissionEngine.java` | **新增** |
| `core/agent/permission/ConfirmResult.java` | **新增** |
| `core/channel/Channel.java` | **修改** - 增加 `sendConfirmCard()` |
| `core/agent/impl/ReActBizAgent.java` | **修改** - 集成确认流程 |

---

## 九、AgentState 统一状态管理

### 9.1 设计目标

参照 AgentScope 的 `AgentState`，将 JobClaw 当前分散的状态（ChatMemory、SessionAgentBinder、用户偏好）统一到单一状态对象中。

### 9.2 核心设计

```java
// core/agent/state/AgentState.java
@Data
@Builder
public class AgentState {
    private String sessionId;
    private String agentId;

    /** 对话上下文（替代 ChatMemory 的内部存储） */
    private List<Message> context;

    /** 工具上下文（已激活的工具组、pending 工具调用） */
    private ToolContext toolContext;

    /** 权限上下文 */
    private PermissionContext permissionContext;

    /** 记忆引用（EpisodicMemory + LongTermMemory 的检索结果缓存） */
    private Map<String, String> memoryCache;

    /** 当前计划引用 */
    private Plan currentPlan;
}
```

### 9.3 持久化策略

参照 AgentScope 的 `Session.save(sessionKey, "agent_state", state)` 模式：

```java
// 每次 Agent 执行结束后自动保存状态
private Mono<Void> saveStateToSession() {
    return Mono.fromRunnable(() ->
        session.save(sessionKey, "agent_state", state));
}
```

**与现有 Session 的整合：** JobClaw 的 `SessionAgentBinder` 已经维护了 session-agent 映射关系，在此基础上将 AgentState 纳入 Session 管理。

### 9.4 落地改动清单

| 文件 | 改动 |
|------|-----|
| `core/agent/state/AgentState.java` | **新增** |
| `core/agent/state/ToolContext.java` | **新增** |
| `core/agent/state/SessionStateStore.java` | **新增** |
| `core/router/SessionAgentBinder.java` | **修改** - 集成 AgentState 持久化 |

---

## 十、Structured Output 结构化输出

### 10.1 设计目标

参照 AgentScope 的 `StructuredOutputCapableAgent` + `StructuredOutputHook`，使 Agent 能够返回类型安全的结构化数据。

### 10.2 核心设计

```java
// core/agent/output/StructuredOutput.java
public interface StructuredOutput<T> {
    /** 期望输出的 Java 类型 */
    Class<T> outputType();

    /** JSON Schema 描述 */
    String jsonSchema();

    /** 解析 LLM 输出为结构化对象，失败时返回 empty */
    Optional<T> parse(String llmOutput);
}

// 使用示例
public class JobRecommendAgent extends ReActBizAgent {
    @Override
    public StructuredOutput<List<JobRecommendation>> structuredOutput() {
        return StructuredOutput.of(JobRecommendation.class);
    }
}
```

### 10.3 自纠正机制

参照 AgentScope 的 `StructuredOutputHook`，当 LLM 输出不符合格式时自动重试：

```java
// core/agent/middleware/impl/StructuredOutputMiddleware.java
public class StructuredOutputMiddleware implements AgentMiddleware {

    @Override
    public Flux<AgentEvent> onReasoning(BizAgent agent, ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {
        return next.apply(input)
            .flatMap(result -> {
                if (structuredOutput.parse(result.getText()).isEmpty()) {
                    // 解析失败 → 注入纠正提示，重新推理
                    input = input.withAdditionalMessage(
                        Message.system("输出格式不正确，请按照以下 JSON Schema 重新输出：\n"
                            + structuredOutput.jsonSchema()));
                    return next.apply(input); // gotoReasoning
                }
                return Flux.just(result);
            });
    }
}
```

---

## 十一、Event 事件系统

### 11.1 设计目标

参照 AgentScope 的 `AgentEvent` 细粒度事件流，为 JobClaw Agent 提供完整的执行可观测性。

### 11.2 事件类型

```java
// core/agent/event/AgentEvent.java (sealed interface)
public sealed interface AgentEvent {
    // Agent 级事件
    record AgentStartEvent(String replyId, String agentName) implements AgentEvent {}
    record AgentEndEvent(String replyId) implements AgentEvent {}

    // 推理级事件
    record ReasoningStartEvent(String replyId) implements AgentEvent {}
    record ReasoningChunkEvent(String replyId, String text) implements AgentEvent {}
    record ReasoningEndEvent(String replyId) implements AgentEvent {}

    // 工具级事件
    record ToolCallStartEvent(String replyId, String toolId, String toolName) implements AgentEvent {}
    record ToolCallChunkEvent(String replyId, String toolId, String chunk) implements AgentEvent {}
    record ToolResultEndEvent(String replyId, String toolId, String result) implements AgentEvent {}

    // 记忆级事件
    record MemoryRetrievedEvent(String userId, String memory) implements AgentEvent {}
    record MemoryRecordedEvent(String userId, int messageCount) implements AgentEvent {}

    // 计划级事件
    record PlanCreatedEvent(String planName, int subtaskCount) implements AgentEvent {}
    record SubtaskCompletedEvent(String planName, int index) implements AgentEvent {}
}
```

### 11.3 用途

- **前端流式展示**：细粒度的事件流可以让前端实时展示 Agent 的思考过程
- **日志审计**：完整记录每轮推理和工具调用
- **调试追踪**：定位 Agent 执行中的问题

---

## 十二、实施优先级与依赖关系

```
                    ┌─────────────────────┐
                    │  Phase 1: ReAct     │ ← 最高优先级，其他 Phase 的基础
                    │  推理循环           │
                    └────────┬────────────┘
                             │
                    ┌────────▼────────────┐
                    │  Phase 2: Middleware │ ← 基础设施，后续 Phase 都依赖
                    │  生命周期拦截        │
                    └────────┬────────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
     ┌────────▼──────┐ ┌────▼───────┐ ┌───▼──────────┐
     │ Phase 3:      │ │ Phase 4:   │ │ Phase 5:     │
     │ 记忆压缩      │ │ 任务分解   │ │ 能力沉淀     │  ← 可并行推进
     │ 与分层管理    │ │ PlanNote   │ │ Skill        │
     └───────────────┘ └────────────┘ └──────────────┘
                             │
                    ┌────────▼────────────┐
                    │  Phase 6: HITL      │ ← 生产部署前必须
                    │  人工确认           │
                    └─────────────────────┘
```

### 推荐实施节奏

| 阶段 | 预估工时 | 依赖 | 核心产出 |
|------|---------|------|---------|
| Phase 1: ReAct | 5-7 天 | 无 | `ReActBizAgent` + 工具调用循环 |
| Phase 2: Middleware | 3-4 天 | Phase 1 | `AgentMiddleware` 接口 + Chain + Logging |
| Phase 3: 记忆 | 4-5 天 | Phase 2 | 摘要压缩 + EpisodicMemory |
| Phase 4: Plan | 3-4 天 | Phase 2 | PlanNotebook + PlanHintMiddleware |
| Phase 5: Skill | 3-4 天 | Phase 2 | SkillBox + SkillSedimentMiddleware |
| Phase 6: HITL | 3-4 天 | Phase 1 | PermissionEngine + 确认卡片 |

**总计：约 21-28 天**

---

## 十三、核心对比：AgentScope vs JobClaw 改进方案

| 维度 | AgentScope Java | JobClaw 当前 | JobClaw 改进后 |
|------|----------------|-------------|---------------|
| 推理模式 | ReAct 迭代循环 | 单次 LLM 调用 | ReAct 迭代循环 |
| 生命周期 | Middleware 洋葱模型 | 无拦截 | Middleware 洋葱模型 |
| 记忆 | InMemory + LongTermMemory + 语义检索 | SmartWindow 滑动窗口 | 三层记忆 + 摘要压缩 |
| 任务分解 | PlanNotebook + SubTask | 无 | PlanNotebook + SubTask |
| 能力沉淀 | SkillBox + Markdown 技能文件 | 无 | SkillBox + workspace/skills |
| 权限控制 | PermissionEngine + ConfirmResult | 无 | @RequireConfirm + 确认卡片 |
| 输出格式 | StructuredOutput + 自纠正 | 自由文本 | StructuredOutput + 自纠正 |
| 状态管理 | AgentState + Session | 分散状态 | 统一 AgentState |
| 事件流 | 细粒度 AgentEvent | 无 | sealed AgentEvent |
| 响应式 | Project Reactor | Spring AI Flux | Project Reactor / Spring AI Flux |

---

## 十四、注意事项

1. **向后兼容**：所有改进以新增为主，现有 `BizAgent` 接口和实现不变，新 Agent 选择继承 `ReActBizAgent`
2. **渐进式迁移**：现有 Agent（IdentityCollectorAgent、JobFetchAgent 等）可逐步迁移到 ReAct 模式
3. **技术栈一致**：继续使用 Spring AI 的 `ChatModel` / `StreamingChatModel` 作为底层模型调用，不引入 AgentScope 的 Model 抽象
4. **Spring 生态融合**：Middleware 和 Agent 均注册为 Spring Bean，支持 `@Autowired` 注入
5. **配置驱动**：所有新增功能通过 `application.yml` 配置开关控制，默认关闭
6. **Java 21 特性**：充分利用 Record、Sealed Class、Pattern Matching、Virtual Thread
