package com.git.hui.jobclaw.core.router;

import com.git.hui.jobclaw.core.agent.BizAgent;
import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.LlmCaller;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.bus.event.MessageReceivedEvent;
import com.git.hui.jobclaw.core.bus.event.MessageResponseEvent;
import com.git.hui.jobclaw.core.bus.event.UserConnectedEvent;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.channel.ChannelResponseMessage;
import com.git.hui.jobclaw.core.router.intent.AgentRegistry;
import com.git.hui.jobclaw.core.router.intent.AgentRouter;
import com.git.hui.jobclaw.core.router.intent.IntentClassifier;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.router.intent.SessionAgentBinder;
import com.git.hui.jobclaw.core.router.intent.classifier.IntentClassificationRes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Optional;

/**
 * 消息网关路由，所有channel传入的消息都需要先经过MsgRouter来实现路由转发到具体的Agent，执行相关业务操作，然后再将消息分发出去
 *
 * @author YiHui
 * @date 2026/4/17
 */
@Slf4j
@Component
public class MsgRouter {

    private final ChannelRegistry channelRegistry;

    private final ChannelEventPublisher channelEventPublisher;

    private final LlmCaller llmCaller;

    private final IIdentityAgent identityAgent;

    // ==== Intent & Agent Routing ====
    private final IntentClassifier intentClassifier;
    private final AgentRouter agentRouter;
    private final SessionAgentBinder sessionBinder;
    private final AgentRegistry agentRegistry;

    public MsgRouter(ChannelRegistry channelRegistry,
                     ChannelEventPublisher channelEventPublisher,
                     LlmCaller llmCaller,
                     IIdentityAgent identityAgent,
                     IntentClassifier compositeIntentClassifier,
                     AgentRouter agentRouter,
                     SessionAgentBinder sessionBinder,
                     AgentRegistry agentRegistry) {
        this.channelRegistry = channelRegistry;
        this.channelEventPublisher = channelEventPublisher;
        this.llmCaller = llmCaller;
        this.identityAgent = identityAgent;
        this.intentClassifier = compositeIntentClassifier;
        this.agentRouter = agentRouter;
        this.sessionBinder = sessionBinder;
        this.agentRegistry = agentRegistry;
    }

    private LlmCaller getLlmCaller() {
        return llmCaller;
    }

    /**
     * 处理消息接收事件
     * 流程：身份检查 → 意图识别 → Agent路由 → 执行响应
     */
    @Async
    @EventListener
    public void onMessageReceived(MessageReceivedEvent event) {
        var msg = event.getOriginalMessage();
        String jobClawUserId = msg.getJobClawUserId();
        String fromUserId = msg.getFromUserId();
        String channel = msg.getChannel();
        String userMessage = msg.getMessage();
        LlmCaller.UserConversationInfo conversationInfo = new LlmCaller.UserConversationInfo(jobClawUserId, channel, fromUserId);
        String sessionId = conversationInfo.conversationId();

        // Step 1: 根据用户是否存在偏好信息，来决定个是否主动触发用户信息采集Agent，当返回true时，中断当前对话流程，进入信息采集
        // This handles soul.md → user.md → info.md initialization
        if (identityAgent.triggerToCollectIdentity(conversationInfo, userMessage)) {
            log.info("Message handled by unified initializer for user: {}", jobClawUserId);
            return; // Don't send to normal agent during initialization
        }

        // Step 2: 判断是否需要意图识别
        if (!sessionBinder.needsIntentRecognition(jobClawUserId, sessionId, userMessage)) {
            // 继续使用绑定的Agent
            String agentId = sessionBinder.getBoundAgentId(jobClawUserId, sessionId).orElse(null);
            routeToAgent(agentId, msg, conversationInfo);
            return;
        }

        // Step 3: 检查Agent切换命令
        if (intentClassifier.isAgentSwitchCommand(userMessage)) {
            Optional<String> targetAgentId = intentClassifier.parseAgentSwitchCommand(userMessage);
            if (targetAgentId.isPresent() && agentRegistry.hasAgent(targetAgentId.get())) {
                sessionBinder.bind(jobClawUserId, sessionId, targetAgentId.get());
                routeToAgent(targetAgentId.get(), msg, conversationInfo);
                return;
            }
            // 无效的Agent ID，继续意图识别
        }

        // Step 4: 意图识别
        IntentClassificationRes classification = intentClassifier.classify(conversationInfo, userMessage, java.util.Collections.emptyList());
        log.info("Intent classification: {}", classification);

        // Step 5: 重置指令
        if (classification.intentType() == PresetAgentIntro.RESET) {
            sessionBinder.unbind(jobClawUserId, sessionId);
            sendTextResponse(msg, "会话状态已重置，请告诉我您想要做什么？");
            return;
        }

        // Step 6: 帮助指令
        if (classification.intentType() == PresetAgentIntro.HELP) {
            sendHelpResponse(msg);
            return;
        }

        // Step 7: 列出可用Agent指令
        if (classification.intentType() == PresetAgentIntro.LIST_AGENTS) {
            sendAgentsListResponse(msg);
            return;
        }

        // Step 7: 路由到Agent
        Optional<String> currentBoundAgent = sessionBinder.getBoundAgentId(jobClawUserId, sessionId);
        AgentRouter.RouterResult routeResult = agentRouter.route(classification, currentBoundAgent);

        // Step 8: 绑定会话状态（如果是新会话）
        if (routeResult.isNewSession()) {
            sessionBinder.bind(jobClawUserId, sessionId, routeResult.agentId());
            sessionBinder.addIntentHistory(jobClawUserId, sessionId, classification.intentType(), classification.confidence());
        }

        // Step 9: 执行Agent
        routeToAgent(routeResult.agentId(), msg, conversationInfo);
    }

    /**
     * 路由到指定Agent执行
     */
    private void routeToAgent(String agentId, ChannelReceiveMessage msg, LlmCaller.UserConversationInfo conversationInfo) {
        // 获取Agent
        BizAgent agent = agentRegistry.getAgent(agentId).orElseGet(() -> agentRegistry.getDefaultAgent().orElse(null));

        if (agent == null) {
            log.error("未找到Agent: {}", agentId);
            sendTextResponse(msg, "没有找到可用的Agent哦，请稍后再试。");
            return;
        }

        log.info("Agent 开始执行用户请求：{}", agent.getAgentIntro());
        // 执行Agent
        try {
            String response = null;
            Flux<LlmRspCell> streamRes = null;
            if (msg.isStream()) {
                streamRes = agent.stream(conversationInfo, msg);
            } else {
                response = agent.process(conversationInfo, msg);
            }

            if (response == null && streamRes == null) {
                // Agent处理失败，回退到LLM响应
                fallbackToLlm(msg, conversationInfo);
                return;
            }

            sendTextResponse(msg, response, streamRes);
        } catch (Exception e) {
            log.error("Agent执行失败: {}", agentId, e);
            fallbackToLlm(msg, conversationInfo);
        }
    }

    /**
     * 回退到LLM响应
     */
    private void fallbackToLlm(ChannelReceiveMessage msg, LlmCaller.UserConversationInfo conversationInfo) {
        try {
            if (msg.isStream()) {
                Flux<LlmRspCell> streamRes = getLlmCaller().streamResponse(conversationInfo, msg);
                sendTextResponse(msg, null, streamRes);
            } else {
                var response = getLlmCaller().respondToMultiModal(conversationInfo, msg);
                sendTextResponse(msg, response);
            }
        } catch (Exception e) {
            log.error("LLM响应失败", e);
            sendTextResponse(msg, "抱歉，服务出现故障，请稍后再试。");
        }
    }

    private void sendTextResponse(ChannelReceiveMessage msg, String content) {
        this.sendTextResponse(msg, content, null);
    }

    /**
     * 发送文本响应
     */
    private void sendTextResponse(ChannelReceiveMessage msg, String content, Flux<LlmRspCell> streamContents) {
        ChannelResponseMessage responseMessage = ChannelResponseMessage.builder()
                .jobClawUserId(msg.getJobClawUserId())
                .toUserId(msg.getFromUserId())
                .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                .content(content)
                .streamContents(streamContents)
                .passThrough(msg.getPassThrough())
                .build();

        channelEventPublisher.publishMessageResponse(
                "RSP_" + System.currentTimeMillis(),
                msg.getMsgId(),
                msg.getChannel(),
                responseMessage
        );
    }

    /**
     * 发送帮助响应
     */
    private void sendHelpResponse(ChannelReceiveMessage msg) {
        String helpText = """
                您好！我是求职派助手，请问有什么可以帮助您的？

                可用命令：
                                
                /agents - 返回所有的agents列表
                                
                /agent <名称> - 切换到指定Agent
                                
                /reset - 重置会话状态
                                
                /help - 显示帮助

                我可以帮您：
                - 推荐岗位 - 根据您的偏好推荐合适的岗位
                - 订阅推送 - 订阅您感兴趣的岗位推送通知
                - 查询状态 - 查看您的投递记录和面试状态
                - 收集信息 - 帮您收集和整理岗位信息
                """;

        sendTextResponse(msg, helpText);
    }

    /**
     * 发送可用Agent列表响应
     */
    private void sendAgentsListResponse(ChannelReceiveMessage msg) {
        var agents = agentRegistry.getAllAgents();

        StringBuilder sb = new StringBuilder();
        sb.append("📋 当前可用的 Agent 列表：\n\n");

        if (agents.isEmpty()) {
            sb.append("暂无可用的Agent。");
        } else {
            for (int i = 0; i < agents.size(); i++) {
                BizAgent agent = agents.get(i);
                if (agent.getAgentIntro().equals(PresetAgentIntro.DEFAULT)) {
                    sb.append(String.format("%d. **%s**\n\n", i + 1, "ChatAgent"));
                    sb.append(String.format("   %s\n\n", "你可以和我进行自由对话哦~"));
                    if (i < agents.size() - 1) {
                        sb.append("\n");
                    }
                } else {
                    var intro = agent.getAgentIntro();
                    sb.append(String.format("%d. **%s**\n\n", i + 1, intro.getAgentId()));
                    sb.append(String.format("   %s\n\n", intro.getDescription()));
                    if (i < agents.size() - 1) {
                        sb.append("\n");
                    }
                }
            }
            sb.append("\n💡 提示：使用 `/agent <名称>` 命令可以切换到指定Agent");
        }

        sendTextResponse(msg, sb.toString());
    }

    /**
     * 处理消息响应事件 - 发送响应到通道
     */
    @Async
    @EventListener
    public void onMessageResponse(MessageResponseEvent event) {
        // 这里接收到业务Agent的执行返回，此时我们需要将返回结果发送到对应的通道中
        // 这里需要通过 ChannelRegistry 找到对应的通道，然后执行消息响应
        var channel = channelRegistry.getChannel(event.getChannel());
        if (channel == null) {
            log.error("找不到对应的通道，请确认这个通道是否正常注册：{}", event.getChannel());
            return;
        }
        log.debug("Publishing MessageResponseEvent: responseId={}, channel={}, msg={}",
                event.getResponseId(),
                event.getChannel(),
                event.getResponseMessage());
        channel.responseToUser(event.getResponseMessage());
    }


    /**
     * 接收到用户连接事件
     *
     * @param event
     */
    @Async
    @EventListener
    public void onImBotConnected(UserConnectedEvent event) {
        // 给用户发送一个欢迎的消息
        String template = """
                您已经成功联通求职派啦，现在您可以直接通过对话和求职派进行沟通了~
                """;
        // 发送一条欢迎语句给连接用户
        channelEventPublisher.publishProactiveMessage("HI_" + System.currentTimeMillis(),
                event.getUserId(),
                event.getChannel(),
                template);
    }
}
