package com.git.hui.jobclaw.core.bus.listener;

import com.git.hui.jobclaw.core.agent.Agent;
import com.git.hui.jobclaw.core.agent.identity.collector.IdentityCollectorSelector;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.bus.event.MessageReceivedEvent;
import com.git.hui.jobclaw.core.bus.event.MessageResponseEvent;
import com.git.hui.jobclaw.core.bus.event.UserConnectedEvent;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.channel.ChannelResponseMessage;
import com.git.hui.jobclaw.core.utils.ThrowableUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 消息事件监听器 - 处理消息接收和响应事件
 *
 * @author YiHui
 * @date 2026/4/8
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageEventListener {

    private final ChannelRegistry channelRegistry;

    private final ChannelEventPublisher channelEventPublisher;

    private final Agent agent;

    private final IdentityCollectorSelector soulCollectorSelector;


    /**
     * 处理消息接收事件
     * todo 根据用户角色、用户意图路由到不同的处理器
     */
    @Async
    @EventListener
    public void onMessageReceived(MessageReceivedEvent event) {
        var msg = event.getOriginalMessage();
        String jobClawUserId = msg.getJobClawUserId();
        String conversationId = msg.getFromUserId();
        String channel = msg.getChannel();
        String userMessage = msg.getMessage();

        // Check if user is in active soul collection mode
        var soulCollector = soulCollectorSelector.getCollector(jobClawUserId);
        var collectionState = soulCollector.getCollectionState(jobClawUserId);
        if (collectionState.isPresent() && collectionState.get().isInProgress()) {
            log.info("[{}] Processing soul collection answer from user: {}",
                    soulCollector.getCollectorType(),
                    jobClawUserId);
            // Process the answer and ask next question
            soulCollector.processAnswer(jobClawUserId, userMessage, channel, conversationId);
            return; // Don't send to normal agent
        }

        // Check if we should initiate active soul collection for new user
        // Trigger on first message if no soul exists
        if (soulCollector.shouldInitiateCollection(jobClawUserId)) {
            log.info("[{}] Initiating active soul collection for new user: {} on first message",
                    soulCollector.getCollectorType(),
                    jobClawUserId);
            // 开虚拟线程，执行采集
            soulCollector.initiateCollection(jobClawUserId, channel, conversationId);
            return;
        }

        // Normal message handling - route to agent
        try {
            String response = agent.respondToMultiModal(jobClawUserId, conversationId, msg);
            ChannelResponseMessage responseMessage = ChannelResponseMessage.builder()
                    .jobClawUserId(jobClawUserId)
                    .toUserId(conversationId)
                    .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                    .content(response)
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
                    .toUserId(conversationId)
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
        channel.send(event.getResponseMessage());
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