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
    private final Map<String, Sinks.One<String>> responseSinks = new ConcurrentHashMap<>();

    public DingDingBotChannel(Resource agentWorkspace, ChannelRegistry channelRegistry, ChannelEventPublisher channelEventPublisher, DingDingBotProperties dingDingBotProperties) {
        super(agentWorkspace, channelRegistry, channelEventPublisher);
        this.dingDingBotProperties = dingDingBotProperties;
        if (dingDingBotProperties.isEnabled() && !CollectionUtils.isEmpty(dingDingBotProperties.getAccounts())) {
            this.dingDingBotProperties.getAccounts().forEach(this::registerMsgListenerCallback);
            channelRegistry.registerChannel(this);
        }
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
                                Sinks.One<String> sink = Sinks.one();
                                final String sessionId = chatbotMessage.getSessionWebhook();
                                responseSinks.put(sessionId, sink);

                                // fixme 现在的方式为一问一答、对于一问多答，或者主动推送消息，这种方式则有限制；后续进行扩展
                                sink.asMono().doOnSuccess(response -> {
                                            log.info("Received response for sessionId: {}", sessionId);
                                            try {
                                                BotReplier.fromWebhook(sessionId).replyText(response);
                                            } catch (IOException e) {
                                                log.error("Failed to reply to DingDing", e);
                                                throw new RuntimeException(e);
                                            }
                                            responseSinks.remove(sessionId);
                                        })
                                        .doOnError(error -> {
                                            log.error("Timeout or error waiting for response for sessionId: {}",
                                                    sessionId,
                                                    error);
                                            responseSinks.remove(sessionId);
                                        })
                                        .subscribe();

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
    public String name() {
        return "dingding";
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
        Sinks.One<String> sink = responseSinks.get(originalMsg.getSessionWebhook());
        if (sink != null) {
            sink.tryEmitValue(msg.getContent());
        } else {
            log.warn("No pending response sink found for msgId: {}", originalMsg.getSessionWebhook());
        }
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
