package com.git.hui.jobclaw.channels;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.aliyun.dingtalkcard_1_0.Client;
import com.aliyun.dingtalkcard_1_0.models.CreateAndDeliverHeaders;
import com.aliyun.dingtalkcard_1_0.models.CreateAndDeliverRequest;
import com.aliyun.dingtalkcard_1_0.models.CreateAndDeliverResponse;
import com.aliyun.dingtalkcard_1_0.models.StreamingUpdateHeaders;
import com.aliyun.dingtalkcard_1_0.models.StreamingUpdateRequest;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiGettokenRequest;
import com.dingtalk.api.response.OapiGettokenResponse;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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

    private final Map<String, CardManager> cardManagers = new ConcurrentHashMap<>();

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
                                // 接收到消息之后，发送到消息总线 bus，然后通过sink方式接收大模型的返回，最后将结果响应给用户
                                log.info("Received message from DingDing msg={}", JsonUtil.toStr(chatbotMessage));
                                processMessage(MsgWrapper.<ChatbotMessage>builder().msg(chatbotMessage)
                                        .jobClawUserId(config.getJobClawUserId()).build());

                                var sinks = autoInitRspSink(chatbotMessage);
                                responseToUser(config.getAppId(), sinks, chatbotMessage);
                                return null;
                            })
                    .build();
            this.cardManagers.put(config.getAppId(),
                    new CardManager((DingDingBotProperties.DingDingBotAccount) config));
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
                .stream(true)
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
        if (msg.getStreamContents() != null) {
            // 流式返回的场景
            var stream = msg.getStreamContents();
            stream.doOnNext(s -> sink.emitNext(s, Sinks.EmitFailureHandler.FAIL_FAST))
                    .doOnError(e -> sink.emitError(e, Sinks.EmitFailureHandler.FAIL_FAST))
                    .doOnComplete(() -> sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST))
                    .subscribe();
        } else {
            sink.emitNext(msg.getContent(), Sinks.EmitFailureHandler.FAIL_FAST);
        }
        return true;
    }


    private Sinks.Many<String> autoInitRspSink(ChatbotMessage chatbotMessage) {
        final String sessionId = chatbotMessage.getSessionWebhook();
        Sinks.Many<String> sinks = Sinks.many().multicast().onBackpressureBuffer();
        RspEmitter emitter = new RspEmitter(sinks, chatbotMessage.getSessionWebhookExpiredTime());
        var old = responseSinks.put(sessionId, emitter);
        if (old != null) {
            // 直接结束之前的监听
            old.sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
        }
        return sinks;
    }

    private void responseToUser(String robotId, Sinks.Many<String> sinks, ChatbotMessage chatbotMessage) {
        // 钉钉的AI卡片支持流式返回，因此我们可以借助它来实现大模型的流式输出
        // 操作流程：
        // 1-> 先创建AiCard卡片
        // 2-> 调用AiCard的update，来实现流式输出
        // 3-> 执行完毕之后关闭AiCard

        CardManager cardManager = cardManagers.get(robotId);
        if (StringUtils.isNotBlank(cardManager.dingDingBotAccount.getAiCardId())) {
            String cardId = CardManager.genAiCardTrackId();
            if (Objects.equals(chatbotMessage.getConversationType(), "1")) {
                cardManager.createImCard(cardId, robotId, chatbotMessage.getSenderStaffId());
            } else {
                cardManager.createGroupCard(cardId, robotId, chatbotMessage.getSenderStaffId(),
                        chatbotMessage.getConversationId());
            }

            StringBuilder content = new StringBuilder();
            sinks.asFlux()
                    .doOnNext(response -> {
                        log.debug("Received response chunk: {}", response);
                        content.append(response);
                        cardManager.streamUpdate(cardId, content.toString(), false);
                    })
                    .doOnError(error -> {
                        log.error("Error in stream response for cardId: {}", cardId, error);
                        // 发生错误时，标记卡片为结束状态
                        cardManager.streamUpdate(cardId, "抱歉，生成回复时遇到了错误。", true);
                    })
                    .doOnComplete(() -> {
                        log.info("Stream response completed for cardId: {}, total length: {}", cardId,
                                content.length());
                        // 流式响应完成，标记卡片为结束状态
                        cardManager.streamUpdate(cardId, content.toString(), true);
                    }).subscribe();
        } else {
            // 没有配置AICard，直接一次性返回
            final String sessionId = chatbotMessage.getSessionWebhook();
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
        }
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
            registerMsgListenerCallback(accountConfig.getJobClawUserId(), accountConfig);
            channelRegistry.registerChannel(this);
        } else {
            log.warn("Unsupported config type: {}", channelConfig.getClass().getName());
        }
    }

    public static class CardManager {
        private final DingDingBotProperties.DingDingBotAccount dingDingBotAccount;
        private Client client;
        private String accessToken;
        private long expireTime;


        public CardManager(DingDingBotProperties.DingDingBotAccount dingDingBotAccount) {
            this.dingDingBotAccount = dingDingBotAccount;
            // 使用虚拟线程进行异步初始化
            Thread.ofVirtual().start(() -> {
                initCorpToken();
                initClient();
            });
        }

        public static String genAiCardTrackId() {
            return "ai_card_" + System.currentTimeMillis();
        }

        private void initClient() {
            try {
                Config config = new Config();
                config.protocol = "https";
                config.regionId = "central";
                this.client = new com.aliyun.dingtalkcard_1_0.Client(config);
            } catch (Exception e) {
                log.error("createClient get exception, msg:{}", e.getMessage());
            }
        }

        private void initCorpToken() {
            try {
                DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/gettoken");
                OapiGettokenRequest request = new OapiGettokenRequest();
                request.setAppkey(this.dingDingBotAccount.getAppId());
                request.setAppsecret(this.dingDingBotAccount.getAppSecret());
                request.setHttpMethod("GET");
                OapiGettokenResponse response = client.execute(request);
                log.info("getCorpToken, resp:{}", response.getBody());
                JSONObject obj = JSON.parseObject(response.getBody());
                this.accessToken = obj.getString("access_token");
                this.expireTime = System.currentTimeMillis() + obj.getLongValue("expires_in") * 1000 - 60_000;
            } catch (Exception e) {
                log.error("getCorpToken get exception, msg:{}", e.getMessage());
            }
        }

        private String getAccessToken() {
            if (System.currentTimeMillis() > expireTime) {
                initCorpToken();
            }
            return accessToken;
        }


        /**
         * 创建私聊的AI卡片
         * @param outTrackId
         * @param robotCode
         * @param userId
         */
        private void createImCard(String outTrackId, String robotCode, String userId) {
            try {
                CreateAndDeliverHeaders headers = new CreateAndDeliverHeaders();
                headers.xAcsDingtalkAccessToken = getAccessToken();

                CreateAndDeliverRequest.CreateAndDeliverRequestImRobotOpenDeliverModel imRobotOpenDeliverModel
                        = new CreateAndDeliverRequest.CreateAndDeliverRequestImRobotOpenDeliverModel().setSpaceType(
                        "IM_ROBOT").setRobotCode(robotCode);


                CreateAndDeliverRequest.CreateAndDeliverRequestImRobotOpenSpaceModel imRobotOpenSpaceModel
                        = new CreateAndDeliverRequest.CreateAndDeliverRequestImRobotOpenSpaceModel().setSupportForward(
                        true);

                Map<String, String> cardDataMap = new HashMap<>();
                cardDataMap.put("content", "# 正在思考中...");

                CreateAndDeliverRequest.CreateAndDeliverRequestCardData cardData = new CreateAndDeliverRequest.CreateAndDeliverRequestCardData()
                        .setCardParamMap(cardDataMap);


                CreateAndDeliverRequest request
                        = new CreateAndDeliverRequest()
                        .setOutTrackId(outTrackId)
                        .setUserId(userId)
                        .setCardTemplateId(this.dingDingBotAccount.getAiCardId())
                        .setCallbackType("STREAM")
                        .setCardData(cardData)
                        .setImRobotOpenSpaceModel(imRobotOpenSpaceModel)
                        .setImRobotOpenDeliverModel(imRobotOpenDeliverModel)
                        .setOpenSpaceId("dtv1.card//im_robot." + userId)
                        .setUserIdType(1);

                CreateAndDeliverResponse resp = client.createAndDeliverWithOptions(request, headers,
                        new RuntimeOptions());
                log.info("CardManager#initImCard get resp:{}", JSON.toJSONString(resp));
            } catch (Exception e) {
                log.warn("CardManager#initImCard get exception, msg:{}", e.getMessage());
            }
        }

        private void createGroupCard(String outTrackId, String robotCode, String userId, String conversationId) {
            try {
                CreateAndDeliverHeaders headers = new CreateAndDeliverHeaders();
                headers.xAcsDingtalkAccessToken = getAccessToken();

                CreateAndDeliverRequest.CreateAndDeliverRequestImGroupOpenDeliverModel imGroupOpenDeliverModel = new CreateAndDeliverRequest.CreateAndDeliverRequestImGroupOpenDeliverModel()
                        .setRobotCode(robotCode)
                        // 卡片接收人
                        .setRecipients(List.of(userId));

                CreateAndDeliverRequest.CreateAndDeliverRequestImGroupOpenSpaceModel imGroupOpenSpaceModel = new CreateAndDeliverRequest.CreateAndDeliverRequestImGroupOpenSpaceModel()
                        .setSupportForward(true);

                Map<String, String> cardDataMap = new HashMap<>();
                cardDataMap.put("content", "# 正在思考中...");

                CreateAndDeliverRequest.CreateAndDeliverRequestCardData cardData = new CreateAndDeliverRequest.CreateAndDeliverRequestCardData()
                        .setCardParamMap(cardDataMap);
                CreateAndDeliverRequest createAndDeliverRequest = new CreateAndDeliverRequest()
                        .setUserId(userId)
                        .setCardTemplateId(this.dingDingBotAccount.getAiCardId())
                        // 用于标识卡片的唯一 ID，业务需自行建立关联关系，用于后续的卡片更新
                        .setOutTrackId(outTrackId)
                        .setCallbackType("STREAM")
                        .setCardData(cardData)
                        .setImGroupOpenSpaceModel(imGroupOpenSpaceModel)
                        .setImGroupOpenDeliverModel(imGroupOpenDeliverModel)
                        .setOpenSpaceId("dtv1.card//im_group." + conversationId)
                        .setUserIdType(1);
                var rsp = client.createAndDeliverWithOptions(createAndDeliverRequest, headers, new RuntimeOptions());
                if (log.isDebugEnabled()) {
                    log.debug("CardManager#initGroupCard get resp:{}", JSON.toJSONString(rsp));
                }
            } catch (Exception e) {
                log.warn("CardManager#initGroupCard get exception, msg:{}", e.getMessage());
            }
        }

        private void streamUpdate(String outTrackId, String content, boolean isFinalize) {
            try {
                StreamingUpdateHeaders headers = new StreamingUpdateHeaders();
                headers.xAcsDingtalkAccessToken = getAccessToken();
                StreamingUpdateRequest request =
                        new StreamingUpdateRequest()
                                .setOutTrackId(outTrackId)
                                .setGuid(UUID.randomUUID().toString())
                                .setKey("content")
                                .setContent(content)
                                .setIsFull(true)
                                .setIsFinalize(isFinalize);
                var res = client.streamingUpdateWithOptions(request, headers, new RuntimeOptions());
                if (log.isDebugEnabled()) {
                    log.debug("CardManager#streamUpdate get res:{}", JSON.toJSONString(res));
                }

            } catch (Exception e) {
                log.error("CardManager#streamUpdate get exception, msg:{}", e.getMessage());
            }
        }
    }

}
