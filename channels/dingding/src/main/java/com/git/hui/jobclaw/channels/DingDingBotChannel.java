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
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.AbsStreamChannel;
import com.git.hui.jobclaw.core.channel.ChannelConfig;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.channel.ChannelResponseMessage;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

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
public class DingDingBotChannel extends AbsStreamChannel<DingDingBotChannel.ChatbotMessageEx> {

    private final DingDingBotProperties dingDingBotProperties;

    /**
     * key = robotId, value = 构建AICard用于流式返回的卡片管理
     */
    private final Map<String, CardManager> cardManagers = new ConcurrentHashMap<>();


    public DingDingBotChannel(Resource agentWorkspace,
                              ChannelRegistry channelRegistry,
                              ChannelEventPublisher channelEventPublisher,
                              DingDingBotProperties dingDingBotProperties,
                              ConfigurationManager configurationManager) {
        super(agentWorkspace, channelRegistry, channelEventPublisher, configurationManager);
        this.dingDingBotProperties = dingDingBotProperties;
    }

    @Override
    public void activeChannelAccounts() {
        log.info("[DingDing] Start to active all channel accounts....");
        if (dingDingBotProperties.isEnabled() && !CollectionUtils.isEmpty(dingDingBotProperties.getAccounts())) {
            // 虚拟线程的方式进行初始化，加快应用启动速度
            Thread.ofVirtual().start(() -> {
                this.dingDingBotProperties.getAccounts().forEach((k, v) -> {
                    if (!CollectionUtils.isEmpty(v)) {
                        v.forEach(tmp -> registerMsgListenerCallback(k, tmp));
                    }
                });
                channelRegistry.registerChannel(this);
            });
        }
    }

    @Override
    public String name() {
        return "dingding";
    }

    @Data
    @NoArgsConstructor
    public static class ChatbotMessageEx extends ChatbotMessage {
        private String robotId;
        private String aiCardId;
    }


    /**
     * 注册钉钉消息监听器，用于接收钉钉机器人接收到的消息
     *
     * @param jobClawUserId 全局用户ID
     * @param config       渠道配置
     */
    private void registerMsgListenerCallback(String jobClawUserId, ChannelConfig config) {
        try {
            if (StringUtils.isBlank(config.getJobClawUserId())) {
                config.setJobClawUserId(jobClawUserId);
            }

            var dingTalkClient = OpenDingTalkStreamClientBuilder.custom()
                    .credential(new AuthClientCredential(config.getAppId(), config.getAppSecret()))
                    .registerCallbackListener(DingTalkStreamTopics.BOT_MESSAGE_TOPIC,
                            (OpenDingTalkCallbackListener<ChatbotMessage, Void>) chatbotMessage -> {
                                // 接收到消息之后，发送到消息总线 bus，然后通过sink方式接收大模型的返回，最后将结果响应给用户
                                log.info("[DingDing] Received message from DingDing msg={}", JsonUtil.toStr(chatbotMessage));
                                String aiCardId = aiCardStatus.getActiveAiCard(config.getAppId(), jobClawUserId);
                                if (StringUtils.isBlank(aiCardId)) {
                                    aiCardId = initStreamAiCardId(config.getAppId(), chatbotMessage);
                                    aiCardStatus.startAiCard(config.getAppId(), jobClawUserId, aiCardId);
                                }
                                ChatbotMessageEx msgEx = new ChatbotMessageEx();
                                BeanUtils.copyProperties(chatbotMessage, msgEx);
                                msgEx.setAiCardId(aiCardId);
                                msgEx.setRobotId(config.getAppId());
                                processMessage(MsgWrapper.<ChatbotMessageEx>builder().msg(msgEx).jobClawUserId(
                                        config.getJobClawUserId()).build());
                                return null;
                            })
                    .build();
            this.cardManagers.put(config.getAppId(), new CardManager((DingDingBotProperties.DingDingBotAccount) config));
            dingTalkClient.start();
            // 初始化时，基于配置维护用于主动给channel发送消息的心跳信息
            this.channelRegistry.refreshChannelHeartBeatInfoIgnoreNull(jobClawUserId, name(), buildHeartBeatCallback(jobClawUserId));
            log.info("[DingDing] DingDing bot channel started for user: {} - {}", jobClawUserId, config.getAppId());
        } catch (Exception e) {
            log.error("[DingDing] Failed to start DingDing bot channel for user: {}", jobClawUserId, e);
            throw new RuntimeException("Failed to initialize DingDing channel", e);
        }
    }


    @Override
    public ChannelReceiveMessage adaptToReceive(MsgWrapper<ChatbotMessageEx> msgWrapper) {
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
    public boolean saveHeartBeatConfig(MsgWrapper<ChatbotMessageEx> wrapper, boolean force) {
        var msg = wrapper.getMsg();
        String type = "1".equals(msg.getConversationType()) ? "im" : "group";
        var prefix = buildHeartBeatKey(wrapper.getJobClawUserId(), msg.getRobotId(), type);
        // 如果存在配置，则不进行更新
        if (!force && configurationManager.getProperty(prefix) != null) {
            return false;
        }

        // 保存配置
        var response = ChannelResponseMessage.builder()
                .jobClawUserId(wrapper.getJobClawUserId())
                .toUserId(msg.getSenderStaffId())
                .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                .passThrough(Map.of("input", msg))
                .build();
        String value = JsonUtil.toStr(response);
        configurationManager.updateProperties(Map.of(prefix, value));
        return true;
    }

    public Function<Object, ChannelResponseMessage> buildHeartBeatCallback(String jobClawUserId) {
        // fixme 群聊的主动推送消息的场景需要考虑如何做支持
        for (String robotId : cardManagers.keySet()) {
            var key = buildHeartBeatKey(jobClawUserId, robotId, "im");
            String value = configurationManager.getProperty(key);
            if (StringUtils.isNotBlank(value)) {
                var response = JsonUtil.toObj(value, ChannelResponseMessage.class);
                var input = response.getPassThrough().get("input");
                if (!(input instanceof ChatbotMessageEx)) {
                    var msg = JsonUtil.toObj(JsonUtil.toStr(input), ChatbotMessageEx.class);
                    msg.setAiCardId(null);
                    response.setPassThrough(Map.of("input", msg));
                } else {
                    ((ChatbotMessageEx) input).setAiCardId(null);
                }

                return object -> {
                    if (object instanceof Flux<?>) {
                        response.setStreamContents((Flux<LlmRspCell>) object);
                    } else {
                        response.setContent(String.valueOf(object));
                    }
                    return response;
                };
            }
        }
        return null;
    }

    private String buildHeartBeatKey(String jobClawUserId, String robotId, String type) {
        return String.format(HEART_BEAT_CONFIG_PREFIX + ".%s.%s", name(), jobClawUserId, robotId, type);
    }

    /**
     * 发送消息到钉钉
     * 该方法会创建一个异步等待机制,等待外部系统返回响应
     *
     * @param msg 响应消息
     * @return 是否发送成功
     */
    @Override
    public boolean responseToUser(ChannelResponseMessage msg) {
        if (msg == null || msg.getToUserId() == null) {
            log.warn("[DingDing] Invalid message or missing toUserId");
            return false;
        }

        ChatbotMessageEx originalMsg = (ChatbotMessageEx) msg.getPassThrough().get("input");
        // 流式返回的场景
        CardManager cardManager = cardManagers.get(originalMsg.getRobotId());
        var stream = msg.getStreamContents();
        String cardId = originalMsg.getAiCardId();
        if (stream != null) {
            if (StringUtils.isBlank(cardId)) {
                // 通常是后台主动给用户发送消息的场景
                cardId = aiCardStatus.getActiveAiCard(originalMsg.getRobotId(), msg.getJobClawUserId());
                if (cardId == null) {
                    cardId = initStreamAiCardId(originalMsg.getRobotId(), originalMsg);
                }
            }

            if (StringUtils.isBlank(cardId)) {
                var content = stream.blockLast();
                return directReply(originalMsg, content.content());
            } else {
                StringBuilder thinking = new StringBuilder();
                StringBuilder content = new StringBuilder();
                String finalCardId = cardId;
                stream.doOnNext(response -> {
                            log.debug("[DingDing] Received response chunk: {}", response);
                            if (StringUtils.isNotBlank(response.thinking())) {
                                thinking.append(response.thinking());
                            }
                            if (!StringUtils.isEmpty(response.content())) {
                                content.append(response.content());
                            }
                            cardManager.streamUpdate(finalCardId, thinking.toString(), content.toString(), false);
                            aiCardStatus.answerAiCard(originalMsg.robotId, msg.getJobClawUserId(), finalCardId);
                        })
                        .doOnError(error -> {
                            log.error("[DingDing] Error in stream response for cardId: {}", finalCardId, error);
                            // 发生错误时，标记卡片为结束状态
                            cardManager.streamUpdate(finalCardId, thinking.toString(), "抱歉，生成回复时遇到了错误。", true);
                            aiCardStatus.finishAiCard(originalMsg.robotId, msg.getJobClawUserId(), finalCardId);
                        })
                        .doOnComplete(() -> {
                            log.info("[DingDing] Stream response completed for cardId: {}, total length: {}", finalCardId,
                                    content.length());
                            // 流式响应完成，标记卡片为结束状态
                            cardManager.streamUpdate(finalCardId, thinking.toString(), content.toString(), true);
                            aiCardStatus.finishAiCard(originalMsg.robotId, msg.getJobClawUserId(), finalCardId);
                        }).subscribe();
            }
        } else {
            // 非流式返回的场景，直接回复
            String content = msg.getContent();
            if (StringUtils.isBlank(content)) {
                content = "出现故障了~现在暂无返回，请稍后再试";
            }

            if (StringUtils.isBlank(cardId)) {
                cardId = aiCardStatus.getActiveAiCard(originalMsg.getRobotId(), msg.getJobClawUserId());
                if (cardId != null) {
                    cardManager.streamUpdate(cardId, "", content, true);
                    return true;
                }

                // 如果会话链接有效，那就通过这个回调发送消息即可
                if (directReply(originalMsg, content)) {
                    return true;
                }

                // 后台主动给用户推送消息的场景，主动创建一个AiCard，用于推送消息
                cardId = initStreamAiCardId(originalMsg.getRobotId(), originalMsg);
                if (cardId == null) {
                    return false;
                }
            }
            cardManager.streamUpdate(cardId, "", content, true);
        }
        return true;
    }

    private boolean directReply(ChatbotMessage originalMsg, String content) {
        if (originalMsg.getSessionWebhookExpiredTime() < System.currentTimeMillis()) {
            // 会话已经过期，无法通过这种方式正确返回
            return false;
        }

        try {
            BotReplier.fromWebhook(originalMsg.getSessionWebhook()).replyText(content);
            return true;
        } catch (IOException e) {
            log.error("[DingDing] Failed to reply to DingDing: {}", content, e);
            return false;
        }
    }

    private String initStreamAiCardId(String robotId, ChatbotMessage chatbotMessage) {
        CardManager cardManager = cardManagers.get(robotId);
        if (StringUtils.isNotBlank(cardManager.dingDingBotAccount.getAiCardId())) {
            String cardId = CardManager.genAiCardTrackId();
            if (Objects.equals(chatbotMessage.getConversationType(), "1")) {
                cardManager.createImCard(cardId, robotId, chatbotMessage.getSenderStaffId());
            } else {
                cardManager.createGroupCard(cardId, robotId, chatbotMessage.getSenderStaffId(),
                        chatbotMessage.getConversationId());
            }
            return cardId;
        }
        return null;
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
            log.warn("[DingDing] Unsupported config type: {}", channelConfig.getClass().getName());
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
                log.error("[DingDing] createClient get exception, msg:{}", e.getMessage());
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
                log.info("[DingDing] getCorpToken, resp:{}", response.getBody());
                JSONObject obj = JSON.parseObject(response.getBody());
                this.accessToken = obj.getString("access_token");
                this.expireTime = System.currentTimeMillis() + obj.getLongValue("expires_in") * 1000 - 60_000;
            } catch (Exception e) {
                log.error("[DingDing] getCorpToken get exception, msg:{}", e.getMessage());
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
                if (log.isDebugEnabled()) {
                    log.debug("[DingDing] CardManager#initImCard get resp:{}", JSON.toJSONString(resp));
                }
            } catch (Exception e) {
                log.warn("[DingDing] CardManager#initImCard get exception, msg:{}", e.getMessage());
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
                    log.debug("[DingDing] CardManager#initGroupCard get resp:{}", JSON.toJSONString(rsp));
                }
            } catch (Exception e) {
                log.warn("[DingDing] CardManager#initGroupCard get exception, msg:{}", e.getMessage());
            }
        }

        private void streamUpdate(String outTrackId, String thinking, String content, boolean isFinalize) {
            try {
                String showValue;
                if (StringUtils.isNotBlank(content)) {
                    showValue = content;
                } else {
                    showValue = "Thinking: " + thinking;
                }

                StreamingUpdateHeaders headers = new StreamingUpdateHeaders();
                headers.xAcsDingtalkAccessToken = getAccessToken();
                StreamingUpdateRequest request =
                        new StreamingUpdateRequest()
                                .setOutTrackId(outTrackId)
                                .setGuid(UUID.randomUUID().toString())
                                .setKey("content")
                                .setContent(showValue)
                                .setIsFull(true)
                                .setIsFinalize(isFinalize);
                var res = client.streamingUpdateWithOptions(request, headers, new RuntimeOptions());
                if (log.isDebugEnabled()) {
                    log.debug("[DingDing] CardManager#streamUpdate get res:{}", JSON.toJSONString(res));
                }

            } catch (Exception e) {
                log.error("[DingDing] CardManager#streamUpdate get exception, msg:{}", e.getMessage());
            }
        }
    }

}
