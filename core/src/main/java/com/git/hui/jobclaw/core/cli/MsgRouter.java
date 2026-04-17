package com.git.hui.jobclaw.core.cli;

import com.git.hui.jobclaw.core.agent.Agent;
import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.LlmRspCell;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.bus.event.MessageReceivedEvent;
import com.git.hui.jobclaw.core.bus.event.MessageResponseEvent;
import com.git.hui.jobclaw.core.bus.event.UserConnectedEvent;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.channel.ChannelResponseMessage;
import com.git.hui.jobclaw.core.utils.ThrowableUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

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

    private final List<Agent> agent;

    private final IIdentityAgent identityAgent;

    public MsgRouter(ChannelRegistry channelRegistry, ChannelEventPublisher channelEventPublisher, List<Agent> agent, IIdentityAgent identityAgent) {
        this.channelRegistry = channelRegistry;
        this.channelEventPublisher = channelEventPublisher;
        this.agent = agent;
        this.identityAgent = identityAgent;
    }

    private Agent getAgent() {
        return agent.get(0);
    }

    /**
     * 处理消息接收事件
     * todo 根据用户角色、用户意图路由到不同的处理器
     */
    @Async
    @EventListener
    public void onMessageReceived(MessageReceivedEvent event) {
        var msg = event.getOriginalMessage();
        String jobClawUserId = msg.getJobClawUserId();
        String fromUserId = msg.getFromUserId();
        String channel = msg.getChannel();
        String userMessage = msg.getMessage();
        Agent.UserConversationInfo conversationInfo = new Agent.UserConversationInfo(jobClawUserId, channel, fromUserId);

        // Step 1: 根据用户是否存在偏好信息，来决定个是否主动触发用户信息采集Agent，当返回true时，中断当前对话流程，进入信息采集
        // This handles soul.md → user.md → info.md initialization
        if (identityAgent.triggerToCollectIdentity(conversationInfo, userMessage)) {
            log.info("Message handled by unified initializer for user: {}", jobClawUserId);
            return; // Don't send to normal agent during initialization
        }

        // Step 2: Normal message handling - route to agent
        try {
            String response = null;
            Flux<LlmRspCell> streamRes = null;
            if (msg.isStream()) {
                streamRes = getAgent().streamResponse(conversationInfo, msg);
            } else {
                response = getAgent().respondToMultiModal(conversationInfo, msg);
            }
            ChannelResponseMessage responseMessage = ChannelResponseMessage.builder()
                    .jobClawUserId(jobClawUserId)
                    .toUserId(fromUserId)
                    .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                    .content(response)
                    .streamContents(streamRes)
                    .passThrough(msg.getPassThrough())
                    .build();

            // 返回大模型的响应给用户
            channelEventPublisher.publishMessageResponse(
                    "RSP_" + System.currentTimeMillis(),
                    msg.getMsgId(),
                    channel,
                    responseMessage
            );
        } catch (Exception e) {
            ChannelResponseMessage responseMessage = ChannelResponseMessage.builder()
                    .jobClawUserId(jobClawUserId)
                    .toUserId(fromUserId)
                    .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                    .content(ThrowableUtil.getStackTrace("糟糕，JobClaw出现故障啦~", e))
                    .passThrough(msg.getPassThrough())
                    .build();
            channelEventPublisher.publishMessageResponse("RSP_ERR_" + System.currentTimeMillis(),
                    msg.getMsgId(),
                    channel,
                    responseMessage
            );
            log.error("JobClaw出现故障啦~", e);
        }
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
