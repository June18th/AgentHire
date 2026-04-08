package com.git.hui.jobclaw.core.bus;

import com.git.hui.jobclaw.core.channel.ChannelConfig;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.channel.ChannelResponseMessage;

/**
 * 事件发布器接口 - 用于发布通道相关事件
 * AIDEV-NOTE: 统一的事件发布接口，支持各类消息和用户事件
 *
 * @author YiHui
 * @date 2026/4/8
 */
public interface ChannelEventPublisher {

    void publishMessageReceived(String channel, ChannelReceiveMessage message, boolean needReply);

    void publishMessageResponse(String responseId, String relatedMessageId, String channel, ChannelResponseMessage responseMessage);

    void publishProactiveMessage(String responseId, String channelName, ChannelResponseMessage responseMessage, int priority);

    void publishUserConnected(ChannelConfig channelUser, boolean isNewUser, String sourceIp);

    void publishUserDisconnected(ChannelConfig channelUser, String reason);
}
