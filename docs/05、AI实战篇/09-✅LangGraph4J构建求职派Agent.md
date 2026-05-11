# 一、版本速览
截止到 Release 0.0.2 版本的时候，求职派主要还是借助SpringAI实现的单智能体应用，借助SpringAI 实现目标数据的解析，提取出校招信息并保存到数据库中。


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1755070401858-fb742ce0-bb88-48ec-9fcc-14d5738f2a01.png)


从信息录入到最终数据同步到正式业务表有下面几步：

1. 录入采集任务
2. 任务调度，执行基于SpringAI实现的任务采集Agent
3. 将采集的任务，保存到草稿数据 `draft_oc`
4. 数据处理，将草稿中的数据按照正式业务数据标准进行清洗、转换
5. 发布数据到正式库


现阶段，数据清洗这一阶段的AI能力还较弱，更多依赖于人工处理，这也是影响整个链路自动化执行的瓶颈点；如果这一阶段能打通，那我们就可以完成一个真正自动化的多智能体应用了。


接下来，我们朝这个方向努力一下，来实现一个完全体的求职派智能体。

# 二、求职派智能体构建
## 1.流程拆解
前面的章节已经把业务流程跑通了一遍，接下来就该把这条链路落地成一套可执行的智能体工作流。


整体思路很简单：围绕“校招信息从进到出”的生命周期，把事情拆成四步，交给四个各司其职的 Agent 去干。


我们定义四个智能体，分别执行 任务分类 -> 任务采集 -> 数据清洗 -> 数据发布。


+ task_calssify: 它负责看懂你丢过来的东西到底是图片、Excel、CSV 还是纯文本，把任务分好类，后面才好按套路走。
+ task_gather: 这一步其实就是我们之前基于 Spring AI 做过的单智能体应用，接收请求、调大模型、把解析后的校招信息结构化出来，先落在草稿区。
+ draft_washer: 按照既定规则把草稿数据做一遍清洗和校验，修补缺项、统一字段、去掉脏数据，保证质量别翻车。
+ draft_publish: 把满足发布条件的草稿数据自动推到正式库，形成对外可用的“干净源”。


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1755075770381-47ef1a49-bf99-4d76-b252-1ea68e08b437.png)

由LangGraph4J生成的流程图


## 2.定义AgentState
在 LangGraph 中，AgentState 用于图的状态共享，本质是一个`Map<String, Object>`，在求职派中，我们定义了一个 `OcAgentStat，`在内部持有各Agent的输入输出。

```java
public class OcAgentState extends AgentState {

    /**
     * 求职派智能体的外部输入，对象格式为 GatherTaskEntity
     */
    public static final String INPUT = "input";

    /**
     * TaskCliassifyAgent执行后的输出，对象格式为 GatherTaskProcessBo， 作为后续任务提取Agent的传参
     */
    public static final String TASK = "task";

    /**
     * TaskGatherAgent执行后的输出，对象格式为 GatherVo，内部记录的是提取后保存到草稿表中数据id（区分插入和更新数据）
     */
    public static final String GATHER = "gather";

    /**
     * TaskWasherAgent执行后的输出，对象格式为 DraftWasherAgent.WasherRecords，内部记录的是完成清洗的草稿数据主键
     */
    public static final String WASHER = "washer";

    /**
     * TaskPublishAgent执行后的输出，对象格式为 List<OcInfoEntity>，内部记录的是发布成功数据
     */
    public static final String PUBLISH = "publish";

    public OcAgentState(Map<String, Object> initData) {
        super(initData);
    }

    public GatherTaskEntity getInput() {
        return (GatherTaskEntity) value(INPUT).orElse(new GatherTaskEntity());
    }

    public GatherTaskProcessBo getTask() {
        return (GatherTaskProcessBo) value(TASK).orElse(null);
    }

    public GatherVo getGather() {
        return (GatherVo) value(GATHER).orElse(null);
    }

    public DraftWasherAgent.WasherRecords getWasherRecords() {
        return (DraftWasherAgent.WasherRecords) value(WASHER).orElse(new DraftWasherAgent.WasherRecords(List.of()));
    }

    public List<OcInfoEntity> getPublishRecords() {
        return (List<OcInfoEntity>) value(PUBLISH).orElse(List.of());
    }

    /**
     * 提供序列化方式，默认使用ObjectStreamStateSerializer，无法有效支持Java POJO类的序列化
     *
     * @return An instance of `StateSerializer` for serializing and deserializing `State` objects.
     */
    public static StateSerializer<OcAgentState> serializer() {
        var serializer = new ObjectStreamStateSerializer<>(OcAgentState::new);
        serializer.mapper().register(Message.class, new MessageSerializer());
        serializer.mapper().register(GatherTaskEntity.class, new JsonSerializer<>(GatherTaskEntity.class));
        serializer.mapper().register(GatherTaskProcessBo.class, new JsonSerializer<>(GatherTaskProcessBo.class));
        serializer.mapper().register(GatherVo.class, new JsonSerializer<>(GatherVo.class));
        serializer.mapper().register(DraftWasherAgent.WasherRecords.class, new JsonSerializer<>(DraftWasherAgent.WasherRecords.class));
        serializer.mapper().register(OcInfoEntity.class, new JsonSerializer<>(OcInfoEntity.class));
        return serializer;
    }
}

```

AgentState 这块，核心其实就是把每个智能体的输入输出先想清楚。我们这版走了最省心的路子：每个 agent 各自维护一套输入和一套输出，边界清楚、耦合度低，调试起来不费脑子。要是后面你更偏向“全局状态一盘棋”，也可以换成一个“大对象”在图里流转，所有智能体直接往上读写，只是需要更严格的字段约束和并发约定，不然很容易把状态搅成一锅粥。


还有个容易踩坑的点一定要提：POJO 的序列化。LangGraph4j 自己搞了一套序列化协议，我们要按它的规矩来，不然状态在节点之间传着传着就花了。我的做法很朴素，直接自定义了个 POJO 的序列化器，底层用最常见的 JSON，把复杂对象统一成字符串再传。


优点很实在：可读、可打日志、出问题也好定位；后面如果真要上更高性能的方案（比如 Protobuf），把序列化器替换掉就行，业务层不用动。

```java
public class JsonSerializer<T> implements Serializer<T> {
    private Class<T> type;

    public JsonSerializer(Class<T> type) {
        this.type = type;
    }

    @Override
    public void write(T recommendRes, ObjectOutput objectOutput) throws IOException {
        String text = JsonUtil.toStr(recommendRes);
        objectOutput.writeObject(text);
    }

    @Override
    public T read(ObjectInput objectInput) throws IOException, ClassNotFoundException {
        String json = Objects.toString(objectInput.readObject());
        return JsonUtil.toObj(json, type);
    }
}
```

## 3.定义通用Agent
在正式的Agent开发之前，我们先定义一个通用的 BaseAgent， 来实现一些统一的行为封装（如获取AgentName, 关键日志输出）

```java
public abstract class BaseAgent {
    /**
     * 返回唯一的AgentName
     *
     * @return
     */
    public abstract String agentName();

    /**
     * 统一的日志打印
     *
     * @param template
     * @param input
     */
    public void log(String template, Object... input) {
        if (log.isDebugEnabled()) {
            log.debug("[{}]: " + template, agentName(), input);
        }
    }

    public void printAgentStartLog(Object req) {
        log("in query: {}", req);
    }

    public void printAgentEndLog(Object res) {
        log("out res: {}", res);
    }
}
```

## 4.任务自动分类：TaskClassifyAgent
在单智能体场景中，用户在提交任务的时候，需要指定对应的任务类型，如下。


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1755081156215-c7818bc2-119b-428b-941a-e8ca4508c0ee.png)

我们希望将这里改造成一个输入源，由 `TaskClassifyAgent`根据输入信息来自动进行分类，让求职派表现得更加智能，(后续有更多不同任务提取类型时，无需做前端页面调整） 因此任务分类Agent的实现如下。

```java
@Slf4j
@Service
public class TaskClassifyAgent extends BaseAgent {
    /**
     * 采集分类Agent
     */
    public static final String AGENT_NAME = "task_classify";

    @Autowired
    private GatherTaskService gatherTaskService;

    @Override
    public String agentName() {
        return AGENT_NAME;
    }

    public Map<String, Object> apply(OcAgentState ocAgentState) {
        GatherTaskEntity task = ocAgentState.getInput();
        printAgentStartLog(task);
        if (task.getType() != 0) {
            return buildResponse(task);
        }

        // 未指定传入数据类型的时候，需要根据输入信息进行自动判断
        if (task.getContent().startsWith("/oc")) {
            // 传入附件的场景
            if (task.getContent().endsWith("csv")) {
                task.setType(GatherTargetTypeEnum.CSV_FILE.getValue());
            } else if (task.getContent().endsWith("xls") || task.getContent().endsWith("xlsx")) {
                task.setType(GatherTargetTypeEnum.EXCEL_FILE.getValue());
            } else if (task.getContent().endsWith("png") || task.getContent().endsWith("jpg") || task.getContent().endsWith("jpeg")
                    || task.getContent().endsWith("gif") || task.getContent().endsWith("webp")) {
                task.setType(GatherTargetTypeEnum.IMAGE.getValue());
            }
        } else {
            // 文本类型
            if (task.getContent().contains("<div>") || task.getContent().contains("<td ")) {
                // html格式文本
                task.setType(GatherTargetTypeEnum.HTML_TEXT.getValue());
            } else {
                if (task.getContent().matches("^https?://.*$")) {
                    // 完整的http链接
                    task.setType(GatherTargetTypeEnum.HTTP_URL.getValue());
                } else {
                    // 如果文本中，只有一个http链接地址，则认为文本是http方式
                    int index = task.getContent().indexOf("http");
                    if (index >= 0) {
                        index = task.getContent().indexOf("http", index + 4);
                        if (index < 0) {
                            task.setType(GatherTargetTypeEnum.HTTP_URL.getValue());
                        } else {
                            task.setType(GatherTargetTypeEnum.TEXT.getValue());
                        }
                    } else {
                        task.setType(GatherTargetTypeEnum.TEXT.getValue());
                    }
                }
            }
        }

        return buildResponse(task);
    }

    private Map<String, Object> buildResponse(GatherTaskEntity task) {
        Map<String, Object> map = new HashMap<>();
        map.put(OcAgentState.TASK, gatherTaskService.markTaskProcessing(task));
        printAgentEndLog(map);
        return map;
    }
}
```

## 5.任务采集：TaskGatherAgent
接下来就是核心的任务采集 Agent，也就是我们之前基于SpringAI实现的单智能体，根据用户的输入，与大模型进行交互，从而提取校招信息。


直接复用之前实现的能力 `OfferGatherService`，有兴趣的小伙伴，可以将这里拆成下面几种：

+ 基于文本的任务采集Agent
+ 基于文件的任务采集Agent
+ 基于图片的任务采集Agent

```java
@Slf4j
@Service
public class TaskGatherAgent extends BaseAgent {
    /**
     * 采集分类Agent
     */
    public static final String AGENT_NAME = "task_gather";

    @Autowired
    private OfferGatherService offerGatherService;

    @Override
    public String agentName() {
        return AGENT_NAME;
    }

    public Map<String, Object> apply(OcAgentState ocAgentState) {
        GatherTaskProcessBo task = ocAgentState.getTask();

        // 执行任务采集
        GatherVo vo = offerGatherService.gatherInfo(task);
        printAgentStartLog(vo);

        // 返回采集结果
        Map<String, Object> map = new HashMap<>();
        map.put(OcAgentState.GATHER, vo);
        printAgentEndLog(vo);
        return map;
    }
}
```

## 6.数据清洗：DraftWasherAgent
大模型抽取的结果，底子多半取决于我们喂进去的信息源质量。


源头够干净、字段够标准，准确率自然更稳一点；但就算输入很规整，也别指望模型次次都完全贴合预期，所以后置的数据处理是必须的。


这里我会先把公司名称、招聘类型之类的字段统一映射到当前系统定义的枚举，确保内外口径一致；同时把各类链接做一遍合法性校验，能打开、能跳转、没毒瘤，才允许进库。


数据清洗的具体玩法其实很有料，比如如何借助大模型做“反校对”、自动纠错、低置信度回退人工，这部分后面有机会单独拆一篇细讲。


本文的重心还是搭建智能体，所以现在先把清洗逻辑作为工作流中的一个固定环节放好，跑通链路，等整体站稳再精细化打磨。

```java
@Slf4j
@Service
public class DraftWasherAgent extends BaseAgent {
    public static final String AGENT_NAME = "draft_washer";

    @Override
    public String agentName() {
        return AGENT_NAME;
    }

    public Map<String, Object> apply(OcAgentState ocAgentState) {
        GatherVo vo = ocAgentState.getGather();
        printAgentStartLog(vo);
        // 清洗数据
        // 根据规则，将大模型新增和更新的数据进行清洗，若清洗完毕之后，一个数据已非常符合发布的条件，则将其传递给下一个发布节点；否则不发布这个节点

        Map<String, Object> map = new HashMap<>();
        map.put(OcAgentState.WASHER, new WasherRecords(List.of(1L, 2L)));
        printAgentEndLog(map);
        return map;
    }

    public record WasherRecords(List<Long> ids) {
    }
}
```


## 7.数据发布：DraftPublishAgent
发布任务相对简单一点，直接将清洗后的数据，通过现有的发布服务发布上架；后续可以联动订阅，新增一个职位信息时，扫描感兴趣的人群，推送一条消息过去，实现“增值服务”。

```java
@Slf4j
@Service
public class DraftPublishAgent extends BaseAgent {
    public static final String AGENT_NAME = "draft_publish";
    @Autowired
    private GatherService gatherService;

    @Override
    public String agentName() {
        return AGENT_NAME;
    }

    public Map<String, Object> apply(OcAgentState ocAgentState) {
        DraftWasherAgent.WasherRecords vo = ocAgentState.getWasherRecords();
        printAgentStartLog(vo);

        List<OcInfoEntity> list = gatherService.moveToOc(vo.ids());
        Map<String, Object> map = new HashMap<>();
        map.put(OcAgentState.PUBLISH, list);
        printAgentEndLog(map);
        return map;
    }
}

```

## 8.定义AgentGraph
当上面的Agent实现完毕之后，接下来的就是基于这些Agent来定义LangGraph中的`Node` `Edge`，

+ 将上面的Agent映射为Node
+ 基于业务流程定义Agent之间的流向关系，以此作为`Edge`

对应的实现，主要是定义第一步中的流程图。

```java
@Slf4j
@Service
public class AgentExecutor {
    private final DraftPublishAgent draftPublishAgent;
    private final DraftWasherAgent draftWasherAgent;
    private final TaskClassifyAgent taskClassifyAgent;
    private final TaskGatherAgent taskGatherAgent;

    private final CompiledGraph<OcAgentState> compiledGraph;

    public AgentExecutor(DraftPublishAgent draftPublishAgent, DraftWasherAgent draftWasherAgent, TaskClassifyAgent taskClassifyAgent, TaskGatherAgent taskGatherAgent) throws GraphStateException {
        this.draftPublishAgent = draftPublishAgent;
        this.draftWasherAgent = draftWasherAgent;
        this.taskClassifyAgent = taskClassifyAgent;
        this.taskGatherAgent = taskGatherAgent;
        this.compiledGraph = new GraphBuilder().build().compile();
        this.printPlantUml();
    }

    public OcAgentState invoke(GatherTaskEntity input) {
        return this.compiledGraph
                .invoke(Map.of(OcAgentState.INPUT, input))
                .orElseGet(() -> new OcAgentState(Map.of("Error", "NoDataResponse")));
    }

    public class GraphBuilder {
        public StateGraph<OcAgentState> build() throws GraphStateException {
            return new StateGraph<>(OcAgentState.serializer())
                    .addNode(TaskClassifyAgent.AGENT_NAME, node_async(taskClassifyAgent::apply))
                    .addNode(TaskGatherAgent.AGENT_NAME, node_async(taskGatherAgent::apply))
                    .addNode(DraftWasherAgent.AGENT_NAME, node_async(draftWasherAgent::apply))
                    .addNode(DraftPublishAgent.AGENT_NAME, node_async(draftPublishAgent::apply))
                    .addEdge(START, TaskClassifyAgent.AGENT_NAME)
                    .addConditionalEdges(TaskClassifyAgent.AGENT_NAME,
                            edge_async(state -> state.getTask() == null ? END : "采集"),
                            EdgeMappings.builder().to(TaskGatherAgent.AGENT_NAME, "采集").toEND().build())
                    .addConditionalEdges(TaskGatherAgent.AGENT_NAME,
                            edge_async(state -> {
                                GatherVo vo = state.getGather();
                                if (vo == null || (CollectionUtils.isEmpty(vo.getInsertList()) && CollectionUtils.isEmpty(vo.getUpdateList()))) {
                                    return END;
                                } else {
                                    return "清洗";
                                }
                            }),
                            EdgeMappings.builder().to(DraftWasherAgent.AGENT_NAME, "清洗").toEND().build())
                    .addConditionalEdges(DraftWasherAgent.AGENT_NAME,
                            edge_async(state -> {
                                DraftWasherAgent.WasherRecords records = state.getWasherRecords();
                                if (records == null || CollectionUtils.isEmpty(records.ids())) {
                                    return END;
                                }
                                return "发布";
                            }),
                            Map.of("发布", DraftPublishAgent.AGENT_NAME, END, END))
                    .addEdge(DraftPublishAgent.AGENT_NAME, END);
        }
    }
}
```


这段的关键在 GraphBuilder.build 的实现，它把整个工作流真正“落地成图”。


在这里我们把 Node、Edge、ConditionalEdges 都定义好，但光盯着代码很容易迷糊——节点是谁、边怎么走、分支在哪拐，脑子想的很费劲。


建议直接对照下面那张可视化图一起看：把每个 Node 对上对应的 Agent，把直连的 Edge 当成“顺流而下”的必经路径，再把 ConditionalEdges 看成“分叉路口”的路由规则。


这样一来，代码里的 builder 链式调用就能和图上的流向一一对应，哪个环节先后执行、哪种条件会走哪条支路、循环回路从哪儿回到哪儿，一眼就明白了。


```java
// 直接使用 LangGraph4J 来生成 plantuml
/**
 * 打印 plantUml 格式流程图
 *
 * @return
 */
private String printPlantUml() {
    // 在线 mermaid绘制地址：https://mermaid.live/
    // GraphRepresentation representation = compiledGraph.getGraph(GraphRepresentation.Type.MERMAID, "求职派智能体", true);

    // 在线uml绘制地址： https://www.plantuml.com/plantuml/uml/SyfFKj2rKt3CoKnELR1Io4ZDoSa700002
    GraphRepresentation representation = compiledGraph.getGraph(GraphRepresentation.Type.PLANTUML, "TravelRecommendAgent", true);
    // 获取 PlantUML 文本
    System.out.println(">>>>>>>>>>>> online uml render site:  https://www.plantuml.com/plantuml/uml/SyfFKj2rKt3CoKnELR1Io4ZDoSa700002");
    System.out.println("=== PlantUML Start ===");
    System.out.println(representation.content());
    System.out.println("------- PlantUML End ---------");
    return representation.content();
}
```


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1755082387646-1fce409c-9c97-4457-ae02-47369131ac9f.png)

项目正常启动之后，你就会看到上面的输出。

```plain
@startuml TravelRecommendAgent
skinparam usecaseFontSize 14
skinparam usecaseStereotypeFontSize 12
skinparam hexagonFontSize 14
skinparam hexagonStereotypeFontSize 12
title "TravelRecommendAgent"
footer

powered by langgraph4j
end footer
circle start<<input>> as __START__
circle stop as __END__
usecase "task_classify"<<Node>>
usecase "task_gather"<<Node>>
usecase "draft_washer"<<Node>>
usecase "draft_publish"<<Node>>
hexagon "check state" as condition1<<Condition>>
hexagon "check state" as condition2<<Condition>>
hexagon "check state" as condition3<<Condition>>
"__START__" -down-> "task_classify"
"task_classify" .down.> "condition1"
"condition1" .down.> "task_gather": "采集"
'"task_classify" .down.> "task_gather": "采集"
"condition1" .down.> "__END__"
'"task_classify" .down.> "__END__"
"task_gather" .down.> "condition2"
"condition2" .down.> "draft_washer": "清洗"
'"task_gather" .down.> "draft_washer": "清洗"
"condition2" .down.> "__END__"
'"task_gather" .down.> "__END__"
"draft_washer" .down.> "condition3"
"condition3" .down.> "draft_publish": "发布"
'"draft_washer" .down.> "draft_publish": "发布"
"condition3" .down.> "__END__"
'"draft_washer" .down.> "__END__"
"draft_publish" -down-> "__END__"
@enduml

```


![](https://cdn.nlark.com/yuque/0/2025/webp/35158118/1755082530591-b22e8064-43b1-433f-9efb-1035975fd065.webp)


注意上面的Agent的链路，实际上是有一个条件分支的，比如数据清洗之后，不符合发布的数据，draft_publish 就不会被执行，而是直接结束；具体的实现如下图。


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1755082839135-30cf7b54-22fa-48c9-8b0b-ba44f8107666.png)

接下来就是封装一下调用入口。

```java
public class AgentExecutor {
    public OcAgentState invoke(GatherTaskEntity input) {
        return this.compiledGraph
                .invoke(Map.of(OcAgentState.INPUT, input))
                .orElseGet(() -> new OcAgentState(Map.of("Error", "NoDataResponse")));
    }
}
```

## 9.定义访问端点
最后就是暴露智能体的驱动入口，直接在AdminOfferGatherController 中新建一个接口。

```java
@GetMapping(path = "/agentRun")
public OcAgentState agentRun(Long taskId) {
    GatherTaskEntity task = gatherTaskService.getTask(taskId);
    OcAgentState state = agentExecutor.invoke(task);
    return state;
}
```

## 10.测试验证
最后就该验货了。


最理想的当然是配个前端，把整条流程点点点跑起来，让人一眼看懂每个节点的输入输出和分支走向。这里用最朴素的方式走一遍：直接基于现有接口，把数据库里已经落盘的任务捞出来，当作起始输入，触发一连串的智能体执行。


从 task_classify 到 task_gather，再到 draft_washer、draft_publish，全链路跑通，看状态怎么在 AgentState 里流转、看每个节点产出的字段有没有对齐我们约定的 schema，最后确认成功发布到正式库。


这样先把后端链路打穿，等验证稳定了，再补前端交互，把可视化监控、节点日志、失败重试这些细节一起收拾干净。


下面是一次完整执行的示例：


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1755082932893-8b0e0526-1cce-4838-be6b-e44ace5e632e.png)


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1755082962506-53275018-c800-4530-af42-ab3fe1bec1ba.png)


我这里录了一个屏，大家感受下。


[此处为语雀卡片，点击链接查看](about:blank#pUCiS)

# 三、小结
这篇就当抛砖引玉，给大家把用 LangGraph4J + Spring AI 搭出复杂多智能体的路径踩了一遍。


照着文里的顺序撸，表面看门槛不高，真落地还是一堆门道：LangGraph 的设计理念很像流程引擎，但又不止是“画线连框”，它更在意状态如何在节点间流动、分支如何优雅决策、回路如何自洽收敛。


就我个人的体感，智能体应用的开发难度不算上天，但也绝对谈不上“脚一伸就过河”——尤其在 Java 生态，能落到代码层细节的资料并不多，干货稀缺。所以别急着求“速成秘籍”，先把时间静下来，花一段功夫把 LangGraph 的玩法啃透，再挑一个真实业务把它跑起来。纸上谈兵一小时，不如线上跑通一分钟；没有真实场景托底，一切技巧都只是动听的传说。


最后说一句，如果对于智能体的开发，有什么想法，欢迎沟通交流，毕竟我也没搞过消费级别的智能体应用开发，有经验的小伙伴请不吝赐教


:::success
文章中所列相关代码，可以在tag中获取 [https://github.com/liuyueyi/JobClaw/releases/tag/0.0.3](https://github.com/liuyueyi/JobClaw/releases/tag/0.0.3)

相关实现，可以在 [https://github.com/liuyueyi/JobClaw/commit/b96552cb842f170a85b423d491a1685442b0e701](https://github.com/liuyueyi/JobClaw/commit/b96552cb842f170a85b423d491a1685442b0e701) 查看

:::

