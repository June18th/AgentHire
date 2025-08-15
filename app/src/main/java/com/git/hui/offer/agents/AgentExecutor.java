package com.git.hui.offer.agents;

import com.git.hui.offer.agents.impl.DraftPublishAgent;
import com.git.hui.offer.agents.impl.DraftWasherAgent;
import com.git.hui.offer.agents.impl.TaskClassifyAgent;
import com.git.hui.offer.agents.impl.TaskGatherAgent;
import com.git.hui.offer.components.context.ReqInfoContext;
import com.git.hui.offer.gather.dao.entity.GatherTaskEntity;
import com.git.hui.offer.web.model.res.GatherVo;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * @author YiHui
 * @date 2025/8/13
 */
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
        try {
            return this.compiledGraph
                    .invoke(Map.of(OcAgentState.INPUT, input))
                    .orElseGet(() -> new OcAgentState(Map.of("Error", "NoDataResponse")));
        } finally {
            SseEmitter sseEmitter = (SseEmitter) ReqInfoContext.getReqInfo().getContextVar(ReqInfoContext.REQ_INFO_KEY);
            if (sseEmitter != null) {
                try {
                    sseEmitter.send(Map.of("cmd", "over"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                sseEmitter.complete();
            }
        }
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

    /**
     * 打印 plantUml 格式流程图
     *
     * @return
     */
    private String printPlantUml() {
        // 在线 mermaid绘制地址：https://mermaid.live/
        // GraphRepresentation representation = compiledGraph.getGraph(GraphRepresentation.Type.MERMAID, "校招派智能体", true);
        // System.out.println(">>>>>>>>>>>> online mermaid render site:  https://mermaid.live/");

        // 在线uml绘制地址： https://www.plantuml.com/plantuml/uml/SyfFKj2rKt3CoKnELR1Io4ZDoSa700002
        GraphRepresentation representation = compiledGraph.getGraph(GraphRepresentation.Type.PLANTUML, "TravelRecommendAgent", true);
        // 获取 PlantUML 文本
        System.out.println(">>>>>>>>>>>> online uml render site:  https://www.plantuml.com/plantuml/uml/SyfFKj2rKt3CoKnELR1Io4ZDoSa700002");
        System.out.println("=== PlantUML Start ===\n\n");
        System.out.println(representation.content());
        System.out.println("------- PlantUML End ---------");
        return representation.content();
    }
}
