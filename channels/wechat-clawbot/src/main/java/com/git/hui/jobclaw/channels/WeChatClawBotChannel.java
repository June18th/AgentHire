package com.git.hui.jobclaw.channels;

import com.git.hui.jobclaw.channels.sdk.MessageBuilder;
import com.git.hui.jobclaw.channels.sdk.WeixinSdk;
import com.git.hui.jobclaw.channels.sdk.WeixinTypes;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.AbsChannel;
import com.git.hui.jobclaw.core.channel.ChannelConfig;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.channel.ChannelResponseMessage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WeChat channel using ClawBot API with WeixinSdk.
 * Refactored to use the complete Weixin SDK for all interactions.
 */
public class WeChatClawBotChannel extends AbsChannel<WeixinTypes.WeixinMessage> {

    private static final Logger log = LoggerFactory.getLogger(WeChatClawBotChannel.class);

    // session save path
    private final String stateDir;

    // Media storage directory (under agent workspace)
    private final Path mediaDir;

    private final Map<String, WeixinSdk> accountMap;

    private final WxChatClawBotProperties wxChatClawBotProperties;

    private final ChannelRegistry channelRegistry;

    public WeChatClawBotChannel(Resource agentWorkspace,
                                WxChatClawBotProperties wxBotProperties,
                                ChannelRegistry channelRegistry,
                                ChannelEventPublisher channelEventPublisher) {
        super(channelEventPublisher);
        this.wxChatClawBotProperties = wxBotProperties;
        this.channelRegistry = channelRegistry;

        // Resolve media directory under workspace
        try {
            Path workspacePath = agentWorkspace.getFile().toPath();
            this.stateDir = workspacePath.resolve("channel").resolve("wx").resolve("state").toString();
            this.mediaDir = workspacePath.resolve("channel").resolve("wx").resolve("media");
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve workspace path", e);
        }

        this.accountMap = new ConcurrentHashMap<>();
        this.loadAllAccounts();
    }

    @Override
    public String name() {
        return "wechat-clawbot";
    }

    /**
     * 加载所有用户，并启动轮询的消息侦听
     */
    public void loadAllAccounts() {
        if (!CollectionUtils.isEmpty(this.wxChatClawBotProperties.getAccounts())) {
            this.channelRegistry.registerChannel(this);
            this.wxChatClawBotProperties.getAccounts().values()
                    .stream().filter(s -> StringUtils.isNotBlank(s.getAppSecret()))
                    .forEach(this::addAccount);
        } else {
            log.warn(
                    "WeChat ClawBot not configured (missing bot-token). Please complete the onboarding process and restart the application.");
        }
    }

    @Override
    public <T extends ChannelConfig> void addAccount(T account) {
        WxClawBotAccount botAccount = (WxClawBotAccount) account;
        // Initialize WeiXinSdk
        String botToken = account.getAppSecret();
        String userId = botAccount.getUserId();
        var sdk = new WeixinSdk.Builder()
                .baseUrl(this.wxChatClawBotProperties.getBaseUrl())
                .cdnBaseUrl(this.wxChatClawBotProperties.getCdnBaseUrl())
                .token(botToken)
                .channelVersion("1.0.3")
                .stateDir(stateDir)
                .mediaDir(mediaDir.resolve(userId).toString())
                .accountId(userId)
                .build();
        var lastSdk = this.accountMap.put(userId, sdk);
        if (lastSdk != null) {
            // 账号更新的场景，停止旧的账号；启动新的账号
            log.info("account updated, stop last sdk, userId: {}, lastSdk: {}", userId, lastSdk);
            lastSdk.shutdown();
            log.info("account updated, start new sdk, userId: {}, sdk: {}", userId, sdk);
        }
        // 轮询监听新的账号消息
        sdk.startPolling(new WeixinSdk.MessageHandler() {
            @Override
            public void onMessage(WeixinTypes.WeixinMessage message) {
                try {
                    processMessage(message);
                } catch (Exception e) {
                    log.error("Error processing message", e);
                }
            }

            @Override
            public void onDisconnect() {
                account.setState(ChannelConfig.ChannelState.ERROR);
            }
        });
        log.info("Started WeChat ClawBot integration with bot token: {}...",
                botToken.substring(0, Math.min(8, botToken.length())));
    }

    /**
     * Process incoming message using WeixinSdk types
     */
    @Override
    public ChannelReceiveMessage adaptToReceive(WeixinTypes.WeixinMessage message) {
        String fromUser = message.getFromUserId();
        String msgContextToken = message.getContextToken();

        if (fromUser == null || fromUser.isBlank()) {
            log.debug("Skipping message with empty from_user_id");
            return null;
        }


        // Build complete message (text + media)
        List<ChannelReceiveMessage.MediaMsg> medias = new ArrayList<>();
        List<ChannelReceiveMessage.FileMsg> files = new ArrayList<>();

        // Add text if present
        String messageText = MessageBuilder.extractText(message);
        var builder = ChannelReceiveMessage.builder()
                .msgId("WX_" + message.getMessageId())
                .channel(name())
                .fromUserId(message.getFromUserId())
                .passThrough(Map.of("msgContentToken", msgContextToken))
                .message(messageText)
                .files(files)
                .medias(medias);

        // Process media items and collect them
        if (!CollectionUtils.isEmpty(message.getItemList())) {
            for (WeixinTypes.MessageItem item : message.getItemList()) {
                if (item.getImageItem() != null && item.getImageItem().getLocalPath() != null) {
                    medias.add(ChannelReceiveMessage.MediaMsg
                            .builder()
                            .mimeType("image/jpeg")
                            .filePath(item.getImageItem().getLocalPath())
                            .build());
                }
                if (item.getFileItem() != null && item.getFileItem().getLocalPath() != null) {
                    files.add(ChannelReceiveMessage.FileMsg
                            .builder()
                            .fileName(item.getFileItem().getFileName())
                            .filePath(item.getFileItem().getLocalPath())
                            .mimeType("application/octet-stream")
                            .build());
                }
                if (item.getVideoItem() != null && item.getVideoItem().getLocalPath() != null) {
                    medias.add(ChannelReceiveMessage.MediaMsg
                            .builder()
                            .filePath(item.getVideoItem().getLocalPath())
                            .mimeType("video/mp4")
                            .build());
                }
                if (item.getVoiceItem() != null && item.getVoiceItem().getLocalPath() != null) {
                    files.add(ChannelReceiveMessage.FileMsg
                            .builder()
                            .fileName(item.getVoiceItem().getLocalPath().getFileName().toString())
                            .filePath(item.getVoiceItem().getLocalPath())
                            .mimeType("audio/mp3")
                            .build());
                }
            }
        }
        return builder.build();
    }

    /**
     * Shutdown the SDK
     */
    public void shutdown() {
        accountMap.values().forEach(WeixinSdk::shutdown);
    }


    // todo 这里现在只实现了发送文本消息，对于图片、文件、视频、语音等消息，待进一步实现
    @Override
    public boolean send(ChannelResponseMessage message) {
        var weixinSdk = this.accountMap.get(message.getToUserId());
        if (weixinSdk == null) {
            log.error("WeixinSdk not initialized");
            return false;
        }

        try {
            // 微信的ClawBot不支持主动发送消息，需要通过问询中的 contextToken 来回复
            // Get context token from manager
            String contextToken = (String) message.getPassThrough().get("msgContentToken");
            if (contextToken != null) {
                weixinSdk.getMessageSender().sendTextMessage(message.getToUserId(), message.getContent(), contextToken);
            }
            log.debug("Message send: {}", message);
            return true;
        } catch (Exception e) {
            log.error("Failed to send message: {}", message, e);
            return false;
        }
    }
}
