package com.git.hui.jobclaw.channels;

import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.DingTalkStreamTopics;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.chatbot.BotReplier;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.AbsChannel;
import com.git.hui.jobclaw.core.channel.ChannelConfig;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.channel.ChannelResponseMessage;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 钉钉机器人通道
 * 使用Flux.Sink实现异步消息响应机制
 *
 * @author YiHui
 * @date 2026/4/13
 */
@Slf4j
public class DingDingBotChannel extends AbsChannel<ChatbotMessage> {

    private final DingDingBotProperties dingDingBotProperties;

    /**
     * msgId -> Sink<ChannelResponseMessage>
     * 用于异步等待消息响应的回调机制
     */
    private final Map<String, RspEmitter> responseSinks = new ConcurrentHashMap<>();

    public record RspEmitter(Sinks.Many<String> sink, Long expireTime) {
    }

    public DingDingBotChannel(Resource agentWorkspace, ChannelRegistry channelRegistry, ChannelEventPublisher channelEventPublisher, DingDingBotProperties dingDingBotProperties) {
        super(agentWorkspace, channelRegistry, channelEventPublisher);
        this.dingDingBotProperties = dingDingBotProperties;
        if (dingDingBotProperties.isEnabled() && !CollectionUtils.isEmpty(dingDingBotProperties.getAccounts())) {
            this.dingDingBotProperties.getAccounts().forEach((k, v) -> {
                if (!CollectionUtils.isEmpty(v)) {
                    v.forEach(tmp -> registerMsgListenerCallback(k, tmp));
                }
            });
            channelRegistry.registerChannel(this);
        }
    }


    @Override
    public String name() {
        return "dingding";
    }


    /**
     * 注册钉钉消息监听器
     *
     * @param globalUserId 全局用户ID
     * @param config       渠道配置
     */
    private void registerMsgListenerCallback(String globalUserId, ChannelConfig config) {
        try {
            if (StringUtils.isBlank(config.getJobClawUserId())) {
                config.setJobClawUserId(globalUserId);
            }

            var dingTalkClient = OpenDingTalkStreamClientBuilder.custom()
                    .credential(new AuthClientCredential(config.getAppId(), config.getAppSecret()))
                    .registerCallbackListener(DingTalkStreamTopics.BOT_MESSAGE_TOPIC,
                            (OpenDingTalkCallbackListener<ChatbotMessage, Void>) chatbotMessage -> {
                                // 接收到消息
                                log.info("Received message from DingDing msg={}", JsonUtil.toStr(chatbotMessage));
                                processMessage(MsgWrapper.<ChatbotMessage>builder().msg(chatbotMessage).jobClawUserId(
                                        config.getJobClawUserId()).build());
                                final String sessionId = chatbotMessage.getSessionWebhook();
                                Sinks.Many<String> sinks = Sinks.many().multicast().onBackpressureBuffer();
                                RspEmitter emitter = new RspEmitter(sinks,
                                        chatbotMessage.getSessionWebhookExpiredTime());
                                var old = responseSinks.put(sessionId, emitter);
                                if (old != null) {
                                    // 直接结束之前的监听
                                    old.sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
                                }

                                sinks.asFlux().subscribe(response -> {
                                    log.info("Received response: {} sessionId: {}", response, sessionId);
                                    try {
                                        if (StringUtils.isNotBlank(response)) {
                                            BotReplier.fromWebhook(sessionId).replyText(response);
                                        }
                                    } catch (IOException e) {
                                        log.error("Failed to reply to DingDing: {}", response, e);
                                    }
                                });
                                return null;
                            })
                    .build();

            dingTalkClient.start();
            log.info("DingDing bot channel started for user: {}", globalUserId);
        } catch (Exception e) {
            log.error("Failed to start DingDing bot channel for user: {}", globalUserId, e);
            throw new RuntimeException("Failed to initialize DingDing channel", e);
        }
    }


    @Override
    public ChannelReceiveMessage adaptToReceive(MsgWrapper<ChatbotMessage> msgWrapper) {
        if (msgWrapper == null) {
            return null;
        }

        ChatbotMessage msg = msgWrapper.getMsg();


        return ChannelReceiveMessage.builder()
                .msgId(msg.getMsgId())
                .message(msg.getText().getContent())
                .fromUserId(msg.getConversationId())
                .jobClawUserId(msgWrapper.getJobClawUserId())
                .channel(name())
                .passThrough(Map.of("input", msg))
                .build();
    }


    @Override
    public Function<Object, ChannelResponseMessage> updatePersonalActiveChannel(MsgWrapper<ChatbotMessage> wrapper) {
        // fixme 需要注意，一个用户有多个机器人的场景，始终会以最后一个交互的机器人作为回复方; 这里应该优化为，每个机器人都维护一个最新的交互状态，方便后台主动推送消息
        var msg = wrapper.getMsg();

        // type == 1 表示个人对话
        // type == 2表示群聊, 可以通过 msg.getConversationTitle() 获取群聊名称
        String type = msg.getConversationType();
        if ("1".equals(type)) {
            // 私人与机器人的聊天
            return input -> ChannelResponseMessage.builder()
                    .jobClawUserId(wrapper.getJobClawUserId())
                    .toUserId(msg.getConversationId())
                    .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                    .content(String.valueOf(input))
                    .passThrough(Map.of("input", msg))
                    .build();
        }
        return null;
    }

    /**
     * 发送消息到钉钉
     * 该方法会创建一个异步等待机制,等待外部系统返回响应
     *
     * @param msg 响应消息
     * @return 是否发送成功
     */
    @Override
    public boolean send(ChannelResponseMessage msg) {
        if (msg == null || msg.getToUserId() == null) {
            log.warn("Invalid message or missing toUserId");
            return false;
        }

        ChatbotMessage originalMsg = (ChatbotMessage) msg.getPassThrough().get("input");
        var emitter = responseSinks.get(originalMsg.getSessionWebhook());
        if (emitter == null || emitter.expireTime <= System.currentTimeMillis()) {
            log.error("Response timeout for msgId: {}", originalMsg.getSessionWebhook());
            return false;
        }
        Sinks.Many<String> sink = emitter.sink();
        sink.emitNext(msg.getContent(), Sinks.EmitFailureHandler.FAIL_FAST);
        return true;
    }

    /**
     * 添加账号并启动监听
     *
     * @param channelConfig 渠道配置
     * @param <T>           配置类型
     */
    @Override
    public <T extends ChannelConfig> void addAccount(T channelConfig) {
        if (channelConfig instanceof DingDingBotProperties.DingDingBotAccount) {
            DingDingBotProperties.DingDingBotAccount accountConfig = (DingDingBotProperties.DingDingBotAccount) channelConfig;
            registerMsgListenerCallback(accountConfig.getUserId(), accountConfig);
            channelRegistry.registerChannel(this);
        } else {
            log.warn("Unsupported config type: {}", channelConfig.getClass().getName());
        }
    }
}
