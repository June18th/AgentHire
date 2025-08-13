package com.git.hui.offer.agents.impl;

import lombok.extern.slf4j.Slf4j;

/**
 * @author YiHui
 * @date 2025/8/13
 */
@Slf4j
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
