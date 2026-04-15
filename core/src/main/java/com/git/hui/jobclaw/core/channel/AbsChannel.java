package com.git.hui.jobclaw.core.channel;

import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 抽象通道
 * @author YiHui
 * @date 2026/4/8
 */
@Slf4j
public abstract class AbsChannel<T> implements Channel, ChannelMsgAdapter<T> {
    protected final ChannelEventPublisher channelEventPublisher;
    protected final ChannelRegistry channelRegistry;

    protected final Resource agentWorkspace;

    public AbsChannel(@Value("${agent.workspace}") Resource agentWorkspace,
                      ChannelRegistry channelRegistry,
                      ChannelEventPublisher channelEventPublisher) {
        this.agentWorkspace = agentWorkspace;
        this.channelEventPublisher = channelEventPublisher;
        this.channelRegistry = channelRegistry;
    }

    public void processMessage(MsgWrapper<T> msg) {
        var r = adaptToReceive(msg);
        var func = this.updatePersonalActiveChannel(msg);
        if (func != null) {
            channelRegistry.refreshBackendReceivedChannel(msg.getJobClawUserId(), r.getChannel(), func);
        }
        reportToAgent(r);
    }

    @Override
    public void reportToAgent(ChannelReceiveMessage msg) {
        if (StringUtils.isBlank(msg.getChannel())) {
            msg.setChannel(name());
        }
        channelEventPublisher.publishMessageReceived(msg.getChannel(), msg, true);
    }

    /**
     * 用于更新用户的激活通道，方便异步task，主动推送信息给用户
     */
    public abstract Function<Object, ChannelResponseMessage> updatePersonalActiveChannel(MsgWrapper<T> msg);
}
