package com.git.hui.jobclaw.agents.jobfetch.llm;

import com.git.hui.jobclaw.core.agent.llm.ClientSelector;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 *
 * @author YiHui
 * @date 2026/4/18
 */
@Component
@RequiredArgsConstructor
public class JobLlmCaller {
    private final ClientSelector clientSelector;

    public ChatModel getChatModel(String jobClawUserId, boolean hasImg) {
        return (ChatModel) clientSelector.getUserPreferredModel(jobClawUserId, hasImg);
    }

}
