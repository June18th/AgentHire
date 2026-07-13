package com.git.hui.jobclaw.channels;

import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.service.IUserService;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.AbsStreamChannel;
import com.git.hui.jobclaw.core.channel.ChannelConfig;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.channel.ChannelResponseMessage;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import com.git.hui.jobclaw.core.utils.MimeUtils;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import com.git.hui.jobclaw.core.utils.files.ChannelStorageHelper;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lark.oapi.core.request.EventReq;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.event.CustomEventHandler;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.cardkit.v1.model.ContentCardElementReq;
import com.lark.oapi.service.cardkit.v1.model.ContentCardElementReqBody;
import com.lark.oapi.service.cardkit.v1.model.ContentCardElementResp;
import com.lark.oapi.service.cardkit.v1.model.CreateCardReq;
import com.lark.oapi.service.cardkit.v1.model.CreateCardReqBody;
import com.lark.oapi.service.cardkit.v1.model.CreateCardResp;
import com.lark.oapi.service.cardkit.v1.model.PatchCardElementReq;
import com.lark.oapi.service.cardkit.v1.model.PatchCardElementReqBody;
import com.lark.oapi.service.cardkit.v1.model.PatchCardElementResp;
import com.lark.oapi.service.cardkit.v1.model.SettingsCardReq;
import com.lark.oapi.service.cardkit.v1.model.SettingsCardReqBody;
import com.lark.oapi.service.cardkit.v1.model.SettingsCardResp;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.lark.oapi.service.im.v1.model.GetMessageResourceReq;
import com.lark.oapi.service.im.v1.model.GetMessageResourceResp;
import com.lark.oapi.service.im.v1.model.P1MessageReceivedV1;
import com.lark.oapi.service.im.v1.model.P1MessageReceivedV1Data;
import com.lark.oapi.service.im.v1.model.P1MessageReadV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import com.lark.oapi.ws.Client;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 飞书机器人通道（基于飞书OpenAPI 2.5.3）
 * https://open.feishu.cn/document/server-side-sdk/java-sdk-guide/handle-events
 * @author YiHui
 * @date 2026/4/13
 */
@Slf4j
public class FeiShuBotChannel extends AbsStreamChannel<FeiShuBotChannel.ChatbotMessageEx> {

    private final FeiShuBotProperties FeiShuBotProperties;
    private final ChannelStorageHelper channelStorageHelper;

    /**
     * key = robotId, value = 构建AICard用于流式返回的卡片管理
     */
    private final Map<String, StreamCardManager> cardManagers = new ConcurrentHashMap<>();
    private final Map<String, Client> feishuClients = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> inboundMessageDedup = new ConcurrentHashMap<>();
    private static final long INBOUND_DEDUP_WINDOW_MS = 10 * 60 * 1000L;

    public FeiShuBotChannel(Resource agentWorkspace,
                            ChannelRegistry channelRegistry,
                            ChannelEventPublisher channelEventPublisher,
                            FeiShuBotProperties FeiShuBotProperties,
                            ConfigurationManager configurationManager, ChannelStorageHelper channelStorageHelper) {
        super(agentWorkspace, channelRegistry, channelEventPublisher, configurationManager);
        this.FeiShuBotProperties = FeiShuBotProperties;
        this.channelStorageHelper = channelStorageHelper;
    }

    @Override
    public void activeChannelAccounts() {
        log.info("[FeiShu] Start to active all channel accounts....");
        if (FeiShuBotProperties.isEnabled() && !CollectionUtils.isEmpty(FeiShuBotProperties.getAccounts())) {
            // 虚拟线程初始化，提升启动速度
            Thread.ofVirtual().start(() -> {
                FeiShuBotProperties.getAccounts().forEach((jobClawUserId, accounts) -> {
                    if (!CollectionUtils.isEmpty(accounts)) {
                        accounts.forEach(account -> registerMsgListenerCallback(jobClawUserId, account));
                    }
                });
                channelRegistry.registerChannel(this);
            });
        }
    }

    @Override
    public ChannelConfig.ChannelEnum channel() {
        return ChannelConfig.ChannelEnum.FEI_SHU;
    }

    /**
     * 飞书消息扩展类（增加卡片ID、机器人ID）
     */
    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class ChatbotMessageEx {
        private String robotId;
        private String aiCardId;
        private String openId;
        private String messageId;

        private String content;

        /**
         * p2p：单聊
         * group： 群组
         */
        private String chatType;

        /**
         * 消息类型
         */
        private String msgType;
    }

    /**
     * 注册飞书消息监听回调
     */
    private void registerMsgListenerCallback(String ownUserId, ChannelConfig config) {
        try {
            FeiShuBotProperties.FeiShuBotAccount account = (FeiShuBotProperties.FeiShuBotAccount) config;
            if (StringUtils.isBlank(account.getOwnerJobClawUserId())) {
                account.setOwnerJobClawUserId(ownUserId);
            }
            String appId = config.getAppId();
            if (feishuClients.containsKey(appId)) {
                this.channelRegistry.refreshChannelHeartBeatInfoIgnoreNull(ownUserId, name(), buildHeartBeatCallback(ownUserId));
                log.info("[FeiShu] Feishu bot channel already started for user: {} - {}", ownUserId, appId);
                return;
            }

            EventDispatcher eventDispatcher = EventDispatcher.newBuilder("", "")
                    .onP1MessageReadV1(new ImService.P1MessageReadV1Handler() {
                        @Override
                        public void handle(P1MessageReadV1 p1MessageReadV1) throws Exception {

                        }
                    })
                    .onCustomizedEvent("message", new CustomEventHandler() {
                        @Override
                        public void handle(EventReq eventReq) {
                            handleRawFeiShuEvent(ownUserId, account, appId, "message", eventReq);
                        }
                    })
                    .onCustomizedEvent("im.message.receive_v1", new CustomEventHandler() {
                        @Override
                        public void handle(EventReq eventReq) {
                            handleRawFeiShuEvent(ownUserId, account, appId, "im.message.receive_v1", eventReq);
                        }
                    })
                    .build();

            // 初始化飞书客户端
            Client feishuClient = new Client.Builder(config.getAppId(), config.getAppSecret())
                    .eventHandler(eventDispatcher)
                    .build();
            this.cardManagers.put(config.getAppId(), new StreamCardManager((com.git.hui.jobclaw.channels.FeiShuBotProperties.FeiShuBotAccount) config));
            feishuClient.start();
            this.feishuClients.put(appId, feishuClient);

            // 刷新心跳配置
            this.channelRegistry.refreshChannelHeartBeatInfoIgnoreNull(ownUserId, name(), buildHeartBeatCallback(ownUserId));
            log.info("[FeiShu] Feishu bot channel started for user: {} - {}, eventTypes=message,im.message.receive_v1",
                    ownUserId, account.getAppId());
        } catch (Exception e) {
            log.error("[FeiShu] Failed to start Feishu bot channel for user: {}", ownUserId, e);
            throw new RuntimeException("Failed to initialize Feishu channel", e);
        }
    }

    private void handleRawFeiShuEvent(String ownUserId, FeiShuBotProperties.FeiShuBotAccount account, String appId,
                                      String eventType, EventReq eventReq) {
        String payload = readEventPayload(eventReq);
        log.info("[FeiShu] Raw event received. appId={}, eventType={}, messageId={}",
                appId, eventType, extractRawEventMessageId(payload));
        if (log.isDebugEnabled()) {
            log.debug("[FeiShu] Raw event payload. appId={}, eventType={}, payload={}",
                    appId, eventType, abbreviatePayload(payload));
        }
        try {
            ChatbotMessageEx msg = switch (eventType) {
                case "message" -> toChatbotMessage(Jsons.DEFAULT.fromJson(payload, P1MessageReceivedV1.class).getEvent(), appId);
                case "im.message.receive_v1" -> toChatbotMessage(Jsons.DEFAULT.fromJson(payload, P2MessageReceiveV1.class).getEvent(), appId);
                default -> null;
            };
            handleInboundMessage(ownUserId, account, appId, msg);
        } catch (Exception e) {
            log.error("[FeiShu] Failed to handle raw event. appId={}, eventType={}, payload={}",
                    appId, eventType, abbreviatePayload(payload), e);
        }
    }

    private String readEventPayload(EventReq eventReq) {
        if (eventReq == null) {
            return "";
        }
        if (StringUtils.isNotBlank(eventReq.getPlain())) {
            return eventReq.getPlain();
        }
        byte[] body = eventReq.getBody();
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }

    private String abbreviatePayload(String payload) {
        return StringUtils.abbreviate(StringUtils.defaultString(payload), 2000);
    }

    private String extractRawEventMessageId(String payload) {
        try {
            JsonObject root = Jsons.DEFAULT.fromJson(payload, JsonObject.class);
            if (root == null || !root.has("event")) {
                return "";
            }
            JsonObject event = root.getAsJsonObject("event");
            if (event.has("message")) {
                JsonObject message = event.getAsJsonObject("message");
                return message.has("message_id") ? message.get("message_id").getAsString() : "";
            }
            return event.has("open_message_id") ? event.get("open_message_id").getAsString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private void handleInboundMessage(String ownUserId, FeiShuBotProperties.FeiShuBotAccount account, String appId, ChatbotMessageEx msg) {
        if (msg == null || StringUtils.isBlank(msg.getOpenId())) {
            log.warn("[FeiShu] Invalid inbound message event. appId={}, msg={}", appId, Jsons.DEFAULT.toJson(msg));
            return;
        }
        if (isDuplicateInboundMessage(appId, msg.getMessageId())) {
            log.info("[FeiShu] Duplicate inbound message skipped. appId={}, messageId={}", appId, msg.getMessageId());
            return;
        }
        var cardManager = cardManagers.get(appId);
        String cardId = null;
        if (account.isStream()) {
            // AIDEV-NOTE: isolate each inbound Feishu reply
            // AIDEV-NOTE: fallback when cardkit is unavailable
            if (cardManager == null) {
                log.warn("[FeiShu] Streaming card manager missing, continue without aiCard. appId={}, openId={}", appId, msg.getOpenId());
            } else {
                cardId = cardManager.initStreamAiCardId(msg.getOpenId());
                if (StringUtils.isNotBlank(cardId)) {
                    aiCardStatus.startAiCard(appId, msg.getOpenId(), cardId);
                } else {
                    log.warn("[FeiShu] Streaming card unavailable, continue without aiCard. appId={}, openId={}", appId, msg.getOpenId());
                }
            }
        }
        msg.setAiCardId(cardId);
        log.info("[FeiShu] Inbound message accepted. appId={}, openId={}, messageId={}, msgType={}, chatType={}, aiCardId={}",
                appId, msg.getOpenId(), msg.getMessageId(), msg.getMsgType(), msg.getChatType(), cardId);
        processMessage(MsgWrapper.<ChatbotMessageEx>builder().jobClawUserId(ownUserId).msg(msg).build());
    }

    private boolean isDuplicateInboundMessage(String appId, String messageId) {
        if (StringUtils.isBlank(messageId)) {
            return false;
        }
        long now = System.currentTimeMillis();
        inboundMessageDedup.entrySet().removeIf(entry -> now - entry.getValue() > INBOUND_DEDUP_WINDOW_MS);
        return inboundMessageDedup.putIfAbsent(appId + ":" + messageId, now) != null;
    }

    private ChatbotMessageEx toChatbotMessage(P2MessageReceiveV1Data eventData, String appId) {
        if (eventData == null || eventData.getSender() == null || eventData.getSender().getSenderId() == null
                || eventData.getMessage() == null) {
            return null;
        }
        return new ChatbotMessageEx()
                .setRobotId(appId)
                .setOpenId(eventData.getSender().getSenderId().getOpenId())
                .setMessageId(eventData.getMessage().getMessageId())
                .setMsgType(eventData.getMessage().getMessageType())
                .setChatType(eventData.getMessage().getChatType())
                .setContent(eventData.getMessage().getContent());
    }

    private ChatbotMessageEx toChatbotMessage(P1MessageReceivedV1Data eventData, String appId) {
        if (eventData == null) {
            return null;
        }
        String msgType = eventData.getMsgType();
        String content = switch (StringUtils.defaultString(msgType)) {
            case "text" -> JsonUtil.toStr(Map.of("text", StringUtils.defaultString(eventData.getTextWithoutAtBot(), eventData.getText())));
            case "image" -> JsonUtil.toStr(Map.of("image_key", StringUtils.defaultString(eventData.getImageKey())));
            case "file", "sticker" -> JsonUtil.toStr(Map.of(
                    "file_key", StringUtils.defaultString(eventData.getFileKey()),
                    "file_name", StringUtils.defaultString(eventData.getTitle(), "feishu-file")));
            default -> JsonUtil.toStr(Map.of("text", StringUtils.defaultString(eventData.getText())));
        };
        return new ChatbotMessageEx()
                .setRobotId(appId)
                .setOpenId(eventData.getOpenId())
                .setMessageId(eventData.getOpenMessageId())
                .setMsgType(msgType)
                .setChatType(eventData.getChatType())
                .setContent(content);
    }

    @Override
    public ChannelReceiveMessage adaptToReceive(MsgWrapper<ChatbotMessageEx> msgWrapper) {
        if (msgWrapper == null) {
            return null;
        }

        String robotOwnerUserId = msgWrapper.getJobClawUserId();
        ChatbotMessageEx msg = msgWrapper.getMsg();


        String jobClawUserId = null;
        ChannelConfig channelConfig = this.cardManagers.get(msgWrapper.getMsg().getRobotId()).account;
        String feishuId = msgWrapper.getMsg().getOpenId();
        var user = SpringUtil.getBean(IUserService.class).getUser(feishuId, channel());
        if (channelConfig.getScope() == ChannelConfig.ChannelScope.OWNER) {
            // 仅作者才能使用
            if (user == null || !String.valueOf(user.userId()).equals(robotOwnerUserId)) {
                errorResponse(msg, "这个机器人只为创作者本人服务哦~，若您是机器人的拥有者，请到个人中心->渠道配置->飞书机器人->绑定飞书账号: " + feishuId);
                return null;
            }
            jobClawUserId = robotOwnerUserId;
        } else if (channelConfig.getScope() == ChannelConfig.ChannelScope.LOGIN) {
            if (user == null) {
                log.warn("[DingDing] Failed to find user for staffId: {}", feishuId);
                errorResponse(msg, "您的个人求职派还没有绑定飞书渠道哦，请到个人中心->渠道配置->飞书机器人->绑定飞书账号: " + feishuId);
                return null;
            }
            jobClawUserId = String.valueOf(user.userId());
        } else if (channelConfig.getScope() == ChannelConfig.ChannelScope.VIP) {
            if (user == null) {
                errorResponse(msg, "这个机器人属于VIP专享哦~，请先到个人中心->渠道配置->飞书机器人->绑定飞书账号: " + feishuId);
                return null;
            } else if (user.role() != UserRoleEnum.VIP && user.role() != UserRoleEnum.ADMIN) {
                errorResponse(msg, "这个机器人属于VIP专享哦~，您可以到求职派开通VIP既可畅享对话");
                return null;
            }
            jobClawUserId = String.valueOf(user.userId());
        } else if (channelConfig.getScope() == ChannelConfig.ChannelScope.PUBLIC) {
            if (user == null) {
                // 公开的所有人都可以访问的场景，对于没有绑定的用户，直接使用钉钉的用户体系
                jobClawUserId = "F-" + feishuId;
            } else {
                jobClawUserId = String.valueOf(user.userId());
            }
        }
        msgWrapper.setJobClawUserId(jobClawUserId);


        JsonElement node = Jsons.DEFAULT.toJsonTree(Jsons.DEFAULT.fromJson(msg.getContent(), Map.class));
        String content = "";
        ChannelReceiveMessage.MediaMsg mediaMsg = null;
        ChannelReceiveMessage.FileMsg fileMsg = null;
        if ("text".equals(msg.getMsgType())) {
            // 文本消息
            content = node.getAsJsonObject().get("text").getAsString();
        } else if ("post".equals(msg.getMsgType())) {
            // 富文本
            // fixme 对于富文本，这里先只实现提取文本内容，忽略图片、文件
            JsonArray ary = node.getAsJsonObject().get("content").getAsJsonArray();
            StringBuilder contents = new StringBuilder();
            for (var tmp : ary) {
                for (var t : tmp.getAsJsonArray()) {
                    JsonObject obj = t.getAsJsonObject();
                    if (obj.get("tag").getAsString().equals("text")) {
                        contents.append(obj.get("text").getAsString()).append("\n");
                    }
                }
            }
            content = contents.toString();
        } else if ("image".equals(msg.getMsgType())) {
            // 图片
            var imgKey = node.getAsJsonObject().get("image_key").getAsString();
            var bytes = cardManagers.get(msg.getRobotId()).downloadResource(msg.getMessageId(), imgKey, "image");
            var tmpFile = channelStorageHelper.autoSaveFile(msgWrapper.getJobClawUserId(), name(), bytes, "png");
            mediaMsg = ChannelReceiveMessage.MediaMsg.builder().filePath(Path.of(tmpFile)).mimeType("image/png").build();
        } else if ("file".equals(msg.getMsgType()) || "sticker".equals(msg.getMsgType())) {
            // 文件 or 表情包
            var fileKey = node.getAsJsonObject().get("file_key").getAsString();
            var fileName = node.getAsJsonObject().get("file_name").getAsString();
            var fileType = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".") + 1) : "txt";
            var bytes = cardManagers.get(msg.getRobotId()).downloadResource(msg.getMessageId(), fileKey, msg.getMsgType());
            var tmpFile = channelStorageHelper.autoSaveFile(msgWrapper.getJobClawUserId(), name(), bytes, fileType);
            fileMsg = ChannelReceiveMessage.FileMsg.builder().filePath(Path.of(tmpFile)).fileName(fileName).mimeType(MimeUtils.mimeByExt(fileType)).build();
        } else if ("audio".equals(msg.getMsgType())) {
            // 音频
            responseToUser(ChannelResponseMessage.builder()
                    .passThrough(Map.of("input", msg))
                    .toUserId(msg.getOpenId())
                    .jobClawUserId(msgWrapper.getJobClawUserId())
                    .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                    .streamContents(Flux.just(new LlmRspCell(null, "暂不支持语音消息的处理哦~", null)))
                    .build());
        } else if ("video".equals(msg.getMsgType())) {
            // 视频
            responseToUser(ChannelResponseMessage.builder()
                    .passThrough(Map.of("input", msg))
                    .toUserId(msg.getOpenId())
                    .jobClawUserId(msgWrapper.getJobClawUserId())
                    .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                    .streamContents(Flux.just(new LlmRspCell(null, "暂不支持视频消息的处理哦~", null)))
                    .build());
            return null;
        } else {
            // 表示不支持的消息类型
            responseToUser(ChannelResponseMessage.builder()
                    .passThrough(Map.of("input", msg))
                    .toUserId(msg.getOpenId())
                    .jobClawUserId(msgWrapper.getJobClawUserId())
                    .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                    .streamContents(Flux.just(new LlmRspCell(null, "现在JobClaw还不支持处理这种类型的消息哦~", null)))
                    .build());
            return null;
        }


        return ChannelReceiveMessage.builder()
                .msgId(msg.getMessageId())
                .message(content)
                .fromUserId(msg.getOpenId())
                .jobClawUserId(msgWrapper.getJobClawUserId())
                .files(fileMsg != null ? List.of(fileMsg) : null)
                .medias(mediaMsg != null ? List.of(mediaMsg) : null)
                .channel(name())
                .groupTalk("group".equals(msg.getChatType()))
                .stream(true)
                .passThrough(Map.of("input", msg))
                .build();
    }

    private void errorResponse(ChatbotMessageEx msg, String content) {
        responseToUser(ChannelResponseMessage.builder()
                .passThrough(Map.of("input", msg))
                .toUserId(msg.getOpenId())
                .jobClawUserId(null)
                .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                .streamContents(Flux.just(new LlmRspCell(null, content, null)))
                .build());
    }

    @Override
    public boolean saveHeartBeatConfig(MsgWrapper<ChatbotMessageEx> wrapper, boolean force) {
        var msg = wrapper.getMsg();
        String type = "group".equals(msg.getChatType()) ? "group" : "im";
        var prefix = buildHeartBeatKey(wrapper.getJobClawUserId(), msg.getRobotId(), type);
        String oldValue = configurationManager.getProperty(prefix);
        if (!force && StringUtils.isNotBlank(oldValue)) {
            var oldResponse = JsonUtil.toObj(oldValue, ChannelResponseMessage.class);
            var oldInput = oldResponse != null && oldResponse.getPassThrough() != null
                    ? oldResponse.getPassThrough().get("input")
                    : null;
            ChatbotMessageEx oldMsg = oldInput instanceof ChatbotMessageEx
                    ? (ChatbotMessageEx) oldInput
                    : JsonUtil.toObj(JsonUtil.toStr(oldInput), ChatbotMessageEx.class);
            if (oldMsg != null && StringUtils.equals(oldMsg.getMessageId(), msg.getMessageId())) {
                return false;
            }
        }

        // AIDEV-NOTE: keep Feishu heartbeat context fresh
        var response = ChannelResponseMessage.builder()
                .jobClawUserId(wrapper.getJobClawUserId())
                .toUserId(msg.getOpenId())
                .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                .passThrough(Map.of("input", msg))
                .build();
        String value = JsonUtil.toStr(response);
        configurationManager.updatePropertiesSilently(Map.of(prefix, value));
        log.info("[FeiShu] Heartbeat context refreshed. key={}, messageId={}", prefix, msg.getMessageId());
        return true;
    }

    @Override
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
     * 响应消息给飞书用户（核心：支持流式/非流式响应）
     */
    @Override
    public boolean responseToUser(ChannelResponseMessage msg) {
        if (msg == null || msg.getToUserId() == null) {
            log.warn("[FeiShu] Invalid message or missing toUserId");
            return false;
        }

        if (msg.getPassThrough() == null || !(msg.getPassThrough().get("input") instanceof ChatbotMessageEx originalMsg)) {
            log.warn("[FeiShu] Missing original Feishu message context, toUserId={}", msg.getToUserId());
            return false;
        }

        // 流式返回的场景
        var cardManager = cardManagers.get(originalMsg.getRobotId());
        if (cardManager == null) {
            log.warn("[FeiShu] Missing card manager for robotId={}", originalMsg.getRobotId());
            return false;
        }
        var stream = msg.getStreamContents();
        String feiShuOpenId = originalMsg.getOpenId();
        String cardId = originalMsg.getAiCardId();
        if (stream != null) {
            if (StringUtils.isBlank(cardId)) {
                // 通常是后台主动给用户发送消息的场景
                cardId = aiCardStatus.getActiveAiCard(originalMsg.getRobotId(), feiShuOpenId);
                if (cardId == null) {
                    cardId = cardManager.initStreamAiCardId(originalMsg.getOpenId());
                }
            }

            if (StringUtils.isBlank(cardId)) {
                String content = stream
                        .filter(cell -> cell != null && StringUtils.isNotBlank(cell.content()))
                        .map(LlmRspCell::content)
                        .collectList()
                        .map(parts -> String.join("", parts))
                        .block();
                if (StringUtils.isBlank(content)) {
                    content = "我刚才没有生成出有效回复，请你再发一次或换个说法试试。";
                }
                return cardManager.directReply(originalMsg.getOpenId(), content, ResponseType.TEXT);
            } else {
                StringBuilder thinking = new StringBuilder();
                StringBuilder content = new StringBuilder();
                StringBuilder toolInfo = new StringBuilder();
                StringBuilder toolResultInfo = new StringBuilder();
                String finalCardId = cardId;
                stream.doOnNext(response -> {
                            if (response == null) {
                                return;
                            }
                            if (log.isDebugEnabled()) {
                                log.debug("[FeiShu] Received response chunk: {}", response);
                            }
                            boolean thinkingChanged = false;
                            boolean contentChanged = false;
                            boolean toolInfoChanged = false;
                            boolean toolResultChanged = false;
                            if (StringUtils.isNotBlank(response.thinking())) {
                                thinking.append(response.thinking());
                                thinkingChanged = true;
                            }
                            if (StringUtils.isNotBlank(response.tool())) {
                                toolInfo.append(response.tool()).append("\n");
                                toolInfoChanged = true;
                                log.info("[FeiShu] Tool call detected: {}", response.tool());
                            }
                            if (StringUtils.isNotBlank(response.toolResult())) {
                                toolResultInfo.append(response.toolResult()).append("\n");
                                toolResultChanged = true;
                                log.info("[FeiShu] Tool result received, length={}", response.toolResult().length());
                            }
                            if (!StringUtils.isEmpty(response.content())) {
                                if (content.isEmpty()) {
                                    // 表示首次响应正文内容，此时为了更好的用户体验，我们可以调用飞书接口，将面板关闭
                                    cardManager.autoCloseThinkingHeader(finalCardId);
                                }

                                content.append(response.content());
                                contentChanged = true;
                            }
                            // 更新工具调用信息
                            if (toolInfoChanged) {
                                cardManager.updateStreamingCard(finalCardId, toolInfo.toString(), StreamCardUpdateContentType.TOOL_REQ);
                            }
                            // 更新工具执行结果
                            if (toolResultChanged) {
                                cardManager.updateStreamingCard(finalCardId, toolResultInfo.toString(), StreamCardUpdateContentType.TOOL_RSP);
                            }
                            if (thinkingChanged || contentChanged) {
                                cardManager.updateStreamingCard(finalCardId, thinking.toString(), content.toString(), false);
                            }
                            aiCardStatus.answerAiCard(originalMsg.robotId, feiShuOpenId, finalCardId);
                        })
                        .doOnError(error -> {
                            log.error("[FeiShu] Error in stream response for cardId: {}", finalCardId, error);
                            // 发生错误时，标记卡片为结束状态
                            cardManager.updateStreamingCard(finalCardId, thinking.toString(), "抱歉，生成回复时遇到了错误。", true);
                            aiCardStatus.finishAiCard(originalMsg.robotId, feiShuOpenId, finalCardId);
                        })
                        .doOnComplete(() -> {
                            log.info("[FeiShu] Stream response completed for cardId: {}, total length: {}", finalCardId, content.length());
                            // 流式响应完成，标记卡片为结束状态
                            String finalContent = content.toString();
                            if (StringUtils.isBlank(finalContent)) {
                                log.warn("[FeiShu] Stream response completed with blank content for cardId: {}", finalCardId);
                                finalContent = "我刚才没有生成出有效回复，请你再发一次或换个说法试试。";
                            }
                            cardManager.updateStreamingCard(finalCardId, thinking.toString(), finalContent, true);
                            aiCardStatus.finishAiCard(originalMsg.robotId, feiShuOpenId, finalCardId);
                        }).subscribe();
            }
        } else {
            // 非流式返回的场景，直接回复
            String content = msg.getContent();
            if (StringUtils.isBlank(content)) {
                content = "出现故障了~现在暂无返回，请稍后再试";
            }

            if (StringUtils.isBlank(cardId)) {
                cardId = aiCardStatus.getActiveAiCard(originalMsg.getRobotId(), feiShuOpenId);
                if (cardId != null) {
                    cardManager.updateStreamingCard(cardId, "", content, true);
                    aiCardStatus.finishAiCard(originalMsg.getRobotId(), feiShuOpenId, cardId);
                    return true;
                }

                // 如果会话链接有效，那就通过这个回调发送消息即可
                if (cardManager.directReply(originalMsg.getOpenId(), content, ResponseType.TEXT)) {
                    return true;
                }

                // 后台主动给用户推送消息的场景，主动创建一个AiCard，用于推送消息
                cardId = cardManager.initStreamAiCardId(originalMsg.getOpenId());
                if (cardId == null) {
                    return false;
                }
            }
            cardManager.updateStreamingCard(cardId, "", content, true);
            // 主动结束这个流式卡片，避免被再次更新
            aiCardStatus.finishAiCard(originalMsg.getRobotId(), feiShuOpenId, cardId);
        }
        return true;
    }


    @Override
    public <T extends ChannelConfig> void addAccount(T channelConfig) {
        if (channelConfig instanceof FeiShuBotProperties.FeiShuBotAccount) {
            FeiShuBotProperties.FeiShuBotAccount account = (FeiShuBotProperties.FeiShuBotAccount) channelConfig;
            registerMsgListenerCallback(account.getOwnerJobClawUserId(), account);
            channelRegistry.registerChannel(this);
        } else {
            log.warn("[FeiShu] Unsupported config type: {}", channelConfig.getClass().getName());
        }
    }


    private enum StreamCardUpdateContentType {
        // 思考过程
        THINKING("thinking_1"),
        // 正文
        CONTENT("content_1"),
        // Tool to execute
        TOOL_REQ("tool_req"),
        // tool response
        TOOL_RSP("tool_rsp");
        private String key;

        StreamCardUpdateContentType(String key) {
            this.key = key;
        }
    }

    private enum ResponseType {
        TEXT("text") {
            @Override
            public String buildContent(String content) {
                return "{\"text\":\"" + content + "\"}";
            }
        },
        CARD("interactive") {
            @Override
            public String buildContent(String content) {
                return "{\"type\":\"card\",\"data\":{\"card_id\":\"" + content + "\"}}";
            }
        },
        ;

        private String type;

        ResponseType(String type) {
            this.type = type;
        }

        public abstract String buildContent(String content);
    }

    public static class StreamCardManager {
        private static final long STREAM_CARD_UPDATE_INTERVAL_MS = 500L;
        private static final int STREAM_CARD_UPDATE_MIN_DELTA_CHARS = 24;
        private static final String THINKING_PANEL_ID = "thinking_panel";

        private com.lark.oapi.Client client;
        // 每个卡片的计数序号，用于更新卡片时传入这个序号，要求单调递增
        private Map<String, Integer> aiCardSeq = new ConcurrentHashMap<>();
        // AIDEV-NOTE: throttle high-frequency Feishu card updates
        private Map<String, StreamCardUpdateState> updateStates = new ConcurrentHashMap<>();
        private final Set<String> collapsedThinkingPanels = ConcurrentHashMap.newKeySet();

        private com.git.hui.jobclaw.channels.FeiShuBotProperties.FeiShuBotAccount account;

        public StreamCardManager(FeiShuBotProperties.FeiShuBotAccount account) {
            this.account = account;
            this.client = com.lark.oapi.Client.newBuilder(account.getAppId(), account.getAppSecret()).build();
        }

        private String initStreamAiCardId(String receiveId) {
            String cardId = createStreamingCard();
            if (cardId != null) {
                if (directReply(receiveId, cardId, ResponseType.CARD)) {
                    aiCardSeq.put(cardId, 0);
                    updateStates.put(cardId, new StreamCardUpdateState());
                    return cardId;
                }
            }
            return null;
        }

        private boolean directReply(String receiveId, String content, ResponseType type) {
            // 构造消息内容
            CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType("open_id") // 固定使用 open_id
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                            .receiveId(receiveId) // 接收人
                            .uuid(UUID.randomUUID().toString())
                            .msgType(type.type)    // 消息类型：文本
                            .content(type.buildContent(content))
                            .build())
                    .build();

            // 调用接口发送消息
            try {
                CreateMessageResp resp = client.im().message().create(req);
                if (resp == null || !resp.success() || resp.getData() == null || StringUtils.isBlank(resp.getData().getMessageId())) {
                    log.warn("[FeiShu] Direct reply failed. receiveId={}, type={}, response={}",
                            receiveId, type.type, Jsons.DEFAULT.toJson(resp));
                    return false;
                }
                log.info("[FeiShu] Direct reply success. receiveId={}, type={}, messageId={}",
                        receiveId, type.type, resp.getData().getMessageId());
                if (log.isDebugEnabled()) {
                    log.debug("[FeiShu] Direct reply success: {}", resp.getData().getMessageId());
                }
                return true;
            } catch (Exception e) {
                log.error("[FeiShu] Direct reply error", e);
                return false;
            }
        }


        private String createStreamingCard() {
            CreateCardReq req = CreateCardReq.newBuilder()
                    .createCardReqBody(
                            CreateCardReqBody
                                    .newBuilder()
                                    .type("card_json")
                                    .data("""
                                            {
                                                "schema": "2.0",
                                                "header": {
                                                },
                                                "config": {
                                                    "update_multi": true,
                                                    "streaming_mode": true,
                                                    "summary": {
                                                        "content": ""
                                                    },
                                                    "streaming_config": {
                                                        "print_frequency_ms": {
                                                            "default": 70,
                                                            "android": 70,
                                                            "ios": 70,
                                                            "pc": 70
                                                        },
                                                        "print_step": {
                                                            "default": 1,
                                                            "android": 1,
                                                            "ios": 1,
                                                            "pc": 1
                                                        },
                                                        "print_strategy": "fast"
                                                    }
                                                },
                                                "body": {
                                                    "elements": [
                                                        {
                                                          "tag": "collapsible_panel",
                                                          "element_id": "thinking_panel",
                                                          "expanded": true,
                                                          "header": {
                                                            "title": {
                                                              "tag": "plain_text",
                                                              "content": "🤔 推理（可折叠）",
                                                              "element_id": "thinking_title"
                                                            }
                                                          },
                                                          "elements": [
                                                            {
                                                                "tag": "markdown",
                                                                "content": "",
                                                                "element_id": "thinking_1"
                                                            },
                                                            {
                                                                "tag": "markdown",
                                                                "content": "",
                                                                "element_id": "tool_req"
                                                            },
                                                            {
                                                                "tag": "markdown",
                                                                "content": "",
                                                                "element_id": "tool_rsp"
                                                            }
                                                          ]
                                                        },
                                                        {
                                                              "tag": "hr"
                                                        },
                                                        {
                                                            "tag": "markdown",
                                                            "content": "",
                                                            "element_id": "content_1"
                                                        }
                                                    ]
                                                }
                                            }
                                            """)
                                    .build())
                    .build();

            try {

                CreateCardResp resp = client.cardkit().v1().card().create(req);
                // 从返回里拿 card_id
                if (resp == null || !resp.success() || resp.getData() == null || StringUtils.isBlank(resp.getData().getCardId())) {
                    log.warn("[FeiShu] Create streaming card failed: {}", Jsons.DEFAULT.toJson(resp));
                    return null;
                }
                log.info("[FeiShu] Create streaming card success. cardId={}", resp.getData().getCardId());
                return resp.getData().getCardId();
            } catch (Exception e) {
                log.error("[FeiShu] Create streaming card error", e);
                return null;
            }
        }

        private void updateStreamingCard(String cardId, String thinking, String content, boolean finish) {
            if (StringUtils.isBlank(cardId)) {
                return;
            }
            if (StringUtils.isBlank(content)) {
                if (StringUtils.isBlank(thinking)) {
                    return;
                }
                thinking = "> " + thinking.trim().replaceAll("\n", "\n> ");
                updateStreamingCard(cardId, thinking, StreamCardUpdateContentType.THINKING, finish);
            } else {
                updateStreamingCard(cardId, content, StreamCardUpdateContentType.CONTENT, finish);
            }
            if (finish) {
                completeCard(cardId);
            }
        }

        private void updateStreamingCard(String cardId, String content, StreamCardUpdateContentType type) {
            updateStreamingCard(cardId, content, type, false);
        }

        private void updateStreamingCard(String cardId, String content, StreamCardUpdateContentType type, boolean force) {
            try {
                if (StringUtils.isBlank(cardId) || StringUtils.isEmpty(content)) {
                    return;
                }
                if (!shouldUpdateCard(cardId, type, content, force)) {
                    return;
                }

                // 流式更新卡片内容
                int seq = incrSeq(cardId);
                ContentCardElementReq req = ContentCardElementReq.newBuilder()
                        .cardId(cardId)
                        .elementId(type.key)
                        .contentCardElementReqBody(ContentCardElementReqBody.newBuilder()
                                .uuid(UUID.randomUUID().toString())
                                .content(content)
                                .sequence(seq)
                                .build())
                        .build();

                ContentCardElementResp resp = client.cardkit().v1().cardElement().content(req);
                if (resp == null || !resp.success()) {
                    log.warn("[FeiShu] Update streaming card failed. cardId={}, type={}, response={}",
                            cardId, type.key, Jsons.DEFAULT.toJson(resp));
                    return;
                }
                recordCardUpdate(cardId, type, content);
                if (log.isDebugEnabled()) {
                    log.debug("[FeiShu] Update streaming card success. cardId={}, type={}, seq={}",
                            cardId, type.key, seq);
                }
            } catch (Exception e) {
                log.error("[FeiShu] Update streaming card error", e);
            }
        }

        private boolean shouldUpdateCard(String cardId, StreamCardUpdateContentType type, String content, boolean force) {
            StreamCardUpdateState state = updateStates.computeIfAbsent(cardId, key -> new StreamCardUpdateState());
            String updateKey = type.key;
            String previousContent = state.lastContentByElement.get(updateKey);
            if (Objects.equals(previousContent, content)) {
                state.duplicateSkipCount.incrementAndGet();
                return false;
            }
            if (force || previousContent == null) {
                return true;
            }

            long now = System.currentTimeMillis();
            long lastAt = state.lastUpdateAtByElement.getOrDefault(updateKey, 0L);
            int previousLength = previousContent.length();
            int deltaChars = Math.abs(content.length() - previousLength);
            if (now - lastAt < STREAM_CARD_UPDATE_INTERVAL_MS && deltaChars < STREAM_CARD_UPDATE_MIN_DELTA_CHARS) {
                state.throttleSkipCount.incrementAndGet();
                return false;
            }
            return true;
        }

        private void recordCardUpdate(String cardId, StreamCardUpdateContentType type, String content) {
            updateStates.computeIfAbsent(cardId, key -> new StreamCardUpdateState()).record(type.key, content);
        }

        private void autoCloseThinkingHeader(String cardId) {
            if (StringUtils.isBlank(cardId) || !collapsedThinkingPanels.add(cardId)) {
                return;
            }
            try {
                int seq = incrSeq(cardId);
                PatchCardElementReq req = PatchCardElementReq.newBuilder()
                        .cardId(cardId)
                        .elementId(THINKING_PANEL_ID)
                        .patchCardElementReqBody(PatchCardElementReqBody.newBuilder()
                                .partialElement("{\"expanded\":false}")
                                .uuid(UUID.randomUUID().toString())
                                .sequence(seq)
                                .build())
                        .build();
                PatchCardElementResp resp = client.cardkit().v1().cardElement().patch(req);
                if (resp == null || !resp.success()) {
                    collapsedThinkingPanels.remove(cardId);
                    log.warn("[FeiShu] Collapse thinking panel failed. cardId={}, response={}",
                            cardId, Jsons.DEFAULT.toJson(resp));
                    return;
                }
                if (log.isDebugEnabled()) {
                    log.debug("[FeiShu] Collapsed thinking panel. cardId={}, seq={}", cardId, seq);
                }
            } catch (Exception e) {
                collapsedThinkingPanels.remove(cardId);
                log.warn("[FeiShu] Collapse thinking panel error. cardId={}", cardId, e);
            }
        }

        private void completeCard(String cardId) {
            StreamCardUpdateState updateState = updateStates.get(cardId);
            // 设置卡片更新完成
            // 流式更新卡片内容
            int seq = incrSeq(cardId);
            SettingsCardReq req = SettingsCardReq.newBuilder()
                    .cardId(cardId)
                    .settingsCardReqBody(SettingsCardReqBody.newBuilder()
                            .settings("""
                                    {
                                    "config": {
                                            "streaming_mode": false,
                                            "summary": {
                                                "content": ""
                                            },
                                            "streaming_config": {
                                                "print_frequency_ms": {
                                                    "default": 70,
                                                    "android": 70,
                                                    "ios": 70,
                                                    "pc": 70
                                                },
                                                "print_step": {
                                                    "default": 1,
                                                    "android": 1,
                                                    "ios": 1,
                                                    "pc": 1
                                                },
                                                "print_strategy": "fast"
                                            }
                                        }
                                    }
                                    """)
                            .uuid(UUID.randomUUID().toString())
                            .sequence(seq)
                            .build())
                    .build();

            // 发起请求
            try {
                SettingsCardResp resp = client.cardkit().v1().card().settings(req);
                if (resp == null || !resp.success()) {
                    log.warn("[FeiShu] Complete streaming card failed. cardId={}, response={}",
                            cardId, Jsons.DEFAULT.toJson(resp));
                }
                if (updateState != null) {
                    log.info("[FeiShu] Stream card update metrics. cardId={}, updates={}, duplicateSkips={}, throttleSkips={}",
                            cardId,
                            updateState.updateCount.get(),
                            updateState.duplicateSkipCount.get(),
                            updateState.throttleSkipCount.get());
                }
                if (log.isDebugEnabled()) {
                    log.debug("[FeiShu] Complete streaming card success: {}", Jsons.DEFAULT.toJson(resp));
                }
            } catch (Exception e) {
                log.error("[FeiShu] Complete streaming card error", e);
            } finally {
                removeSeq(cardId);
                updateStates.remove(cardId);
                collapsedThinkingPanels.remove(cardId);
            }
        }

        private int incrSeq(String cardId) {
            // 流式更新卡片内容
            int seq = aiCardSeq.getOrDefault(cardId, 0) + 1;
            aiCardSeq.put(cardId, seq);
            return seq;
        }

        private void removeSeq(String cardId) {
            aiCardSeq.remove(cardId);
        }

        private static class StreamCardUpdateState {
            private final Map<String, Long> lastUpdateAtByElement = new ConcurrentHashMap<>();
            private final Map<String, String> lastContentByElement = new ConcurrentHashMap<>();
            private final AtomicInteger updateCount = new AtomicInteger();
            private final AtomicInteger duplicateSkipCount = new AtomicInteger();
            private final AtomicInteger throttleSkipCount = new AtomicInteger();

            private void record(String elementId, String content) {
                updateCount.incrementAndGet();
                lastUpdateAtByElement.put(elementId, System.currentTimeMillis());
                lastContentByElement.put(elementId, content);
            }
        }


        /**
         * 资源文件下载
         * @param msgId
         * @param fileKey
         * @param type
         */
        public byte[] downloadResource(String msgId, String fileKey, String type) {
            // 创建请求对象
            GetMessageResourceReq req = GetMessageResourceReq.newBuilder()
                    .messageId(msgId)
                    .fileKey(fileKey)
                    .type(type)
                    .build();
            // 发起请求
            try {
                GetMessageResourceResp resp = client.im().v1().messageResource().get(req);
                return resp.getData().toByteArray();
            } catch (Exception e) {
                log.error("[FeiShu] Download resource error: {}.{}", fileKey, type, e);
                return null;
            }
        }
    }
}
