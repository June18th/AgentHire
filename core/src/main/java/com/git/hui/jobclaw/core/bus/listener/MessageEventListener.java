package com.git.hui.jobclaw.core.bus.listener;

import com.git.hui.jobclaw.core.bus.event.MessageReceivedEvent;
import com.git.hui.jobclaw.core.bus.event.MessageResponseEvent;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
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

    /**
     * 处理消息接收事件
     * 根据用户角色路由到不同的处理器
     */
    @Async
    @EventListener
    public void onMessageReceived(MessageReceivedEvent event) {
        // 由 RoutingAgent 来统一接收消息，然后根据意图识别，分配到不同的具体执行单元
        // 然后具体执行单元执行完毕之后，会发送一个 MessageResponseEvent 消息，然后触发下面的监听
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
        channel.send(event.getResponseMessage());
    }

}