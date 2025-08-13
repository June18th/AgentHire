package com.git.hui.offer.agents.impl;

import com.git.hui.offer.agents.OcAgentState;
import com.git.hui.offer.web.model.res.GatherVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 草稿数据清洗Agent
 *
 * @author YiHui
 * @date 2025/8/13
 */
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
