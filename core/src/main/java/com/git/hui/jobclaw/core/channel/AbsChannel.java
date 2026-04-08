package com.git.hui.jobclaw.core.channel;

import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import org.apache.commons.lang3.StringUtils;

/**
 * 抽象通道
 * @author YiHui
 * @date 2026/4/8
 */
public abstract class AbsChannel<T> implements Channel {
    protected final ChannelEventPublisher channelEventPublisher;

    public AbsChannel(ChannelEventPublisher channelEventPublisher) {
        this.channelEventPublisher = channelEventPublisher;
    }

    public void processMessage(T msg) {
        var r = adaptToReceive(msg);
        report(r);
    }

    protected abstract ChannelReceiveMessage adaptToReceive(T msg);

    @Override
    public void report(ChannelReceiveMessage msg) {
        if (StringUtils.isBlank(msg.getChannel())) {
            msg.setChannel(name());
        }
        channelEventPublisher.publishMessageReceived(msg.getChannel(), msg, true);
    }

}
