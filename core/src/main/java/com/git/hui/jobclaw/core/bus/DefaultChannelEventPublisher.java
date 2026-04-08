package com.git.hui.jobclaw.core.bus;

import com.git.hui.jobclaw.core.bus.event.MessageReceivedEvent;
import com.git.hui.jobclaw.core.bus.event.MessageResponseEvent;
import com.git.hui.jobclaw.core.bus.event.UserConnectedEvent;
import com.git.hui.jobclaw.core.bus.event.UserDisconnectedEvent;
import com.git.hui.jobclaw.core.channel.ChannelConfig;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.channel.ChannelResponseMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 事件发布器实现 - 使用Spring Event机制
 *
 * @author YiHui
 * @date 2026/4/8
 */
@Slf4j
@Component
public class DefaultChannelEventPublisher implements ChannelEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public DefaultChannelEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publishMessageReceived(String channel, ChannelReceiveMessage message, boolean needReply) {
        try {
            MessageReceivedEvent event = new MessageReceivedEvent(this, message.getMsgId(), message, needReply);

            eventPublisher.publishEvent(event);
            log.debug("Published MessageReceivedEvent: channel={}, msg={}", channel, message);
        } catch (Exception e) {
            log.error("Failed to publish MessageReceivedEvent: msg={}", message, e);
        }
    }

    @Override
    public void publishMessageResponse(String responseId, String relatedMessageId,
                                       String channel, ChannelResponseMessage responseMessage) {
        try {
            MessageResponseEvent event = MessageResponseEvent.reply(
                    this, responseId, relatedMessageId, channel, responseMessage
            );

            eventPublisher.publishEvent(event);
            log.debug("Published MessageResponseEvent (reply): responseId={}, channel={}, msg={}",
                    responseId, channel, responseMessage);
        } catch (Exception e) {
            log.error("Failed to publish MessageResponseEvent: responseId={}", responseId, e);
        }
    }

    @Override
    public void publishProactiveMessage(String responseId, String channel, ChannelResponseMessage responseMessage, int priority) {
        try {
            MessageResponseEvent event = MessageResponseEvent.proactive(this, responseId, channel, responseMessage, priority
            );

            eventPublisher.publishEvent(event);
            log.info("Published MessageResponseEvent (proactive): responseId={}, channel={}, msg={}",
                    responseId, channel, responseMessage);
        } catch (Exception e) {
            log.error("Failed to publish proactive MessageResponseEvent: responseId={}", responseId, e);
        }
    }

    @Override
    public void publishUserConnected(ChannelConfig channelUser, boolean isNewUser, String sourceIp) {
        try {
            UserConnectedEvent event = new UserConnectedEvent(
                    this, channelUser, isNewUser, sourceIp
            );

            eventPublisher.publishEvent(event);
            log.info("Published UserConnectedEvent: channel={}, isNew={}", channelUser, isNewUser);
        } catch (Exception e) {
            log.error("Failed to publish UserConnectedEvent: channel={}", channelUser, e);
        }
    }

    @Override
    public void publishUserDisconnected(ChannelConfig channelUser, String reason) {
        try {
            UserDisconnectedEvent event = new UserDisconnectedEvent(
                    this, channelUser, reason
            );

            eventPublisher.publishEvent(event);
            log.info("Published UserDisconnectedEvent: channel={}, reason={}",
                    channelUser, reason);
        } catch (Exception e) {
            log.error("Failed to publish UserDisconnectedEvent: channel={}", channelUser, e);
        }
    }
}
