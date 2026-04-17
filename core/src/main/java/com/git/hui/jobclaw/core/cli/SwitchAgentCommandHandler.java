package com.git.hui.jobclaw.core.cli;

import com.git.hui.jobclaw.core.agent.LlmCaller;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.router.intent.AgentRegistry;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.router.intent.SessionAgentBinder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;

/**
 * 列出Agent命令处理器 (/agents)
 *
 * AIDEV-NOTE: 显示所有可用的Agent列表及其描述
 *
 * @author YiHui
 * @date 2026/4/17
 */
@Component
public class SwitchAgentCommandHandler implements SystemCommandHandler {
    private final SessionAgentBinder sessionBinder;
    private final AgentRegistry agentRegistry;

    public SwitchAgentCommandHandler(SessionAgentBinder sessionBinder, AgentRegistry agentRegistry) {
        this.sessionBinder = sessionBinder;
        this.agentRegistry = agentRegistry;
    }

    private Optional<String> parseAgentSwitchCommand(String message) {
        String agentId = message.trim().substring("/agent ".length()).trim();
        if (agentId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(agentId);
    }

    @Override
    public boolean handle(ChannelReceiveMessage msg, LlmCaller.UserConversationInfo conversationInfo, String command, Function<String, Boolean> process) {
        // 检查Agent切换命令，绑定到新的Agent，并返回
        Optional<String> targetAgentId = parseAgentSwitchCommand(command);
        if (targetAgentId.isPresent() && agentRegistry.hasAgent(targetAgentId.get())) {
            sessionBinder.bind(conversationInfo.jobClawUserId(), conversationInfo.conversationId(), targetAgentId.get());
            return process.apply("已切换到Agent：" + targetAgentId.get());
        }
        // 无效的Agent ID，继续意图识别
        return false;
    }

    @Override
    public PresetAgentIntro getIntentType() {
        return PresetAgentIntro.SWITCH_AGENT;
    }

    @Override
    public String getDescription() {
        return "Agent手动切换";
    }
}
