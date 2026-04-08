//package com.git.hui.jobclaw.channels;
//
//import com.git.hui.jobclaw.channels.sdk.MediaDownloader;
//import com.git.hui.jobclaw.channels.sdk.MessageBuilder;
//import com.git.hui.jobclaw.channels.sdk.WeixinSdk;
//import com.git.hui.jobclaw.channels.sdk.WeixinTypes;
//import com.git.hui.jobclaw.core.agent.Agent;
//import com.git.hui.jobclaw.core.channel.Channel;
//import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
//import com.git.hui.jobclaw.core.channel.ChannelRegistry;
//import com.git.hui.jobclaw.core.channel.ChannelResponseMessage;
//import lombok.Data;
//import lombok.EqualsAndHashCode;
//import lombok.experimental.SuperBuilder;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.core.io.Resource;
//
//import java.io.PrintWriter;
//import java.io.StringWriter;
//import java.nio.file.Path;
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * WeChat channel using ClawBot API with WeixinSdk.
// * Refactored to use the complete Weixin SDK for all interactions.
// */
//public class WeClawBotChannel implements Channel {
//
//    private static final Logger log = LoggerFactory.getLogger(WeClawBotChannel.class);
//
//    private final Agent agent;
//    private final ChannelRegistry channelRegistry;
//
//    // SDK instance
//    private final WeixinSdk weixinSdk;
//
//    // session save path
//    private final String mediaState;
//
//    // Media storage directory (under agent workspace)
//    private final String mediaDir;
//
//    public WeClawBotChannel(String botToken, String baseUrl, String cdnBaseUrl, Agent agent, ChannelRegistry channelRegistry, Resource agentWorkspace) {
//        this.agent = agent;
//        this.channelRegistry = channelRegistry;
//
//        // Resolve media directory under workspace
//        try {
//            Path workspacePath = agentWorkspace.getFile().toPath();
//            this.mediaState = workspacePath.resolve("wx").resolve("state").toString();
//            this.mediaDir = workspacePath.resolve("wx").resolve("media").toString();
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to resolve workspace path", e);
//        }
//
//        if (botToken != null && !botToken.isBlank()) {
//            // Initialize WeixinSdk
//            this.weixinSdk = new WeixinSdk.Builder().baseUrl(baseUrl).cdnBaseUrl(cdnBaseUrl).token(botToken).channelVersion("1.0.3").stateDir(mediaState).accountId("default").build();
//
//            channelRegistry.registerChannel(this);
//
//            // Start polling for messages
//            log.info("WeChat ClawBot listener started, polling for messages...");
//            weixinSdk.startPolling(message -> {
//                try {
//                    processMessage(message);
//                } catch (Exception e) {
//                    log.error("Error processing message", e);
//                }
//            });
//
//            log.info("Started WeChat ClawBot integration with bot token: {}...", botToken.substring(0, Math.min(8, botToken.length())));
//        } else {
//            this.weixinSdk = null;
//            log.warn("WeChat ClawBot not configured (missing bot-token). Please complete the onboarding process and restart the application.");
//        }
//    }
//
//    @Override
//    public String name() {
//        return "wechat-clawbot";
//    }
//
//    public boolean send(ChannelResponseMessage message) {
//        log.warn("sendMessage(String) called without user ID. Use sendMessage(String userId, String message) instead.");
//        if (weixinSdk == null) {
//            log.error("WeixinSdk not initialized");
//            return false;
//        }
//
//        try {
//            // Get context token from manager
//            String contextToken = (String) message.getPassThrough().get("contextToken");
//            weixinSdk.getMessageSender().sendTextMessage(message.getToUserId(), message.getContent(), contextToken);
//            log.debug("Message send: {}", message);
//            return true;
//        } catch (Exception e) {
//            log.error("Failed to send message: {}", message, e);
//            return false;
//        }
//    }
//
//
//    /**
//     * Process incoming message using WeixinSdk types
//     */
//    private void processMessage(WeixinTypes.WeixinMessage message) throws Exception {
//        String fromUser = message.getFromUserId();
//        String msgContextToken = message.getContextToken();
//
//        if (fromUser == null || fromUser.isBlank()) {
//            log.debug("Skipping message with empty from_user_id");
//            return;
//        }
//
//        // Update user context and store token
//        if (msgContextToken != null && !msgContextToken.isBlank()) {
//            weixinSdk.getContextTokenManager().setContextToken("default", fromUser, msgContextToken);
//        }
//
//        // Extract message text using MessageBuilder
//        String text = MessageBuilder.extractText(message);
//
//        // Build complete message (text + media)
//        List<Agent.ImageContent> images = new ArrayList<>();
//        List<Agent.FileContent> files = new ArrayList<>();
//
//        // Add text if present
//        String messageText = text;
//
//        // Process media items and collect them
//        if (message.getItemList() != null && !message.getItemList().isEmpty()) {
//            for (WeixinTypes.MessageItem item : message.getItemList()) {
//                if (item.getType() == null) {
//                    continue;
//                }
//
//                try {
//                    switch (item.getType()) {
//                        case WeixinTypes.MessageItemType.IMAGE -> {
//                            Path imagePath = handleImageMessage(fromUser, item.getImageItem(), msgContextToken);
//                            if (imagePath != null) {
//                                images.add(new Agent.ImageContent(imagePath, "image/jpeg"));
//                            }
//                        }
//                        case WeixinTypes.MessageItemType.VIDEO -> {
//                            Path videoPath = handleVideoMessage(fromUser, item.getVideoItem(), msgContextToken);
//                            if (videoPath != null) {
//                                files.add(new Agent.FileContent(videoPath, videoPath.getFileName().toString(), "video/mp4"));
//                            }
//                        }
//                        case WeixinTypes.MessageItemType.FILE -> {
//                            Path filePath = handleFileMessage(fromUser, item.getFileItem(), msgContextToken);
//                            if (filePath != null) {
//                                String fileName = item.getFileItem().getFileName();
//                                files.add(new Agent.FileContent(filePath, fileName, "application/octet-stream"));
//                            }
//                        }
//                        case WeixinTypes.MessageItemType.VOICE -> {
//                            String voiceText = handleVoiceMessage(fromUser, item.getVoiceItem(), msgContextToken);
//                            if (voiceText != null) {
//                                // For voice, prefer transcription; append to existing text
//                                messageText = (messageText == null || messageText.isEmpty()) ? voiceText : messageText + "\n" + voiceText;
//                            }
//                        }
//                        default -> log.debug("Unsupported message type: {}", item.getType());
//                    }
//                } catch (Exception e) {
//                    log.error("Error handling media message type={}", item.getType(), e);
//                }
//            }
//        }
//
//        // Build the multi-modal message using Lombok builder
//        Agent.MultiModalMessage multiModalMessage = Agent.MultiModalMessage.builder().text(messageText).images(images).files(files).build();
//
//        // Send to agent if there's any content
//        if (!multiModalMessage.getText().isBlank() || multiModalMessage.hasMedia()) {
//            log.info("Received multi-modal message from {}: {}", fromUser, multiModalMessage);
//
//            // Publish event with text representation
//            String eventText = multiModalMessage.toString();
//            channelRegistry.publishMessageReceivedEvent(WeClawBotMessageReceivedEvent.builder().fromUserId(fromUser).message(eventText).channel(name()).build());
//
//            // Get agent response using multi-modal API
//            try {
//                String response = agent.respondToMultiModal(getConversationId(fromUser), multiModalMessage);
//                weixinSdk.getMessageSender().sendTextMessage(fromUser, response, msgContextToken);
//                log.info("Response sent to {}", fromUser);
//            } catch (Exception e) {
//                log.error("Failed to send response to {}", fromUser, e);
//
//                // Convert exception stack trace to string
//                StringWriter sw = new StringWriter();
//                PrintWriter pw = new PrintWriter(sw);
//                e.printStackTrace(pw);
//                String stackTrace = sw.toString();
//
//                // Send error message with stack trace (truncated if too long)
//                String errorMessage = stackTrace.length() > 1000 ? stackTrace.substring(0, 1000) + "\n... (truncated)" : stackTrace;
//
//                weixinSdk.getMessageSender().sendTextMessage(fromUser, "Error: " + errorMessage, msgContextToken);
//            }
//        } else {
//            log.debug("Skipping empty message");
//        }
//    }
//
//    /**
//     * Handle image message - download and return file path
//     * @return Path to downloaded image or null if failed
//     */
//    private Path handleImageMessage(String fromUser, WeixinTypes.ImageItem imageItem, String contextToken) throws Exception {
//        if (imageItem == null) {
//            return null;
//        }
//
//        log.info("Received image message from {}", fromUser);
//
//        MediaDownloader downloader = new MediaDownloader(weixinSdk.getCdnBaseUrl());
//        try {
//            // Download and decrypt image
//            Path imagePath = downloader.downloadImage(imageItem, mediaDir, null);
//            log.info("Image downloaded to: {}", imagePath);
//
//            // Return path for multi-modal message
//            return imagePath;
//        } finally {
//            downloader.close();
//        }
//    }
//
//    /**
//     * Handle video message - download and return file path
//     * @return Path to downloaded video or null if failed
//     */
//    private Path handleVideoMessage(String fromUser, WeixinTypes.VideoItem videoItem, String contextToken) throws Exception {
//        if (videoItem == null) {
//            return null;
//        }
//
//        log.info("Received video message from {}", fromUser);
//
//        MediaDownloader downloader = new MediaDownloader(weixinSdk.getCdnBaseUrl());
//        try {
//            // Download and decrypt video
//            Path videoPath = downloader.downloadVideo(videoItem, mediaDir, null);
//            log.info("Video downloaded to: {}", videoPath);
//
//            // Return path for multi-modal message
//            return videoPath;
//        } finally {
//            downloader.close();
//        }
//    }
//
//    /**
//     * Handle file message - download and return file path
//     * @return Path to downloaded file or null if failed
//     */
//    private Path handleFileMessage(String fromUser, WeixinTypes.FileItem fileItem, String contextToken) throws Exception {
//        if (fileItem == null) {
//            return null;
//        }
//
//        String fileName = fileItem.getFileName() != null ? fileItem.getFileName() : "unknown";
//        log.info("Received file message from {}: {}", fromUser, fileName);
//
//        MediaDownloader downloader = new MediaDownloader(weixinSdk.getCdnBaseUrl());
//        try {
//            // Download and decrypt file
//            Path filePath = downloader.downloadFile(fileItem, mediaDir, null);
//            log.info("File downloaded to: {}", filePath);
//
//            // Return path for multi-modal message
//            return filePath;
//        } finally {
//            downloader.close();
//        }
//    }
//
//    /**
//     * Handle voice message - use transcription or download audio
//     * @return transcribed text or null if failed
//     */
//    private String handleVoiceMessage(String fromUser, WeixinTypes.VoiceItem voiceItem, String contextToken) throws Exception {
//        if (voiceItem == null) {
//            return null;
//        }
//
//        log.info("Received voice message from {}", fromUser);
//
//        // Check if voice-to-text is available (preferred)
//        if (voiceItem.getText() != null && !voiceItem.getText().isBlank()) {
//            log.info("Voice transcription: {}", voiceItem.getText());
//            // Return transcribed text directly
//            return voiceItem.getText();
//        }
//
//        // Fallback: download audio file and return as text placeholder
//        MediaDownloader downloader = new MediaDownloader(weixinSdk.getCdnBaseUrl());
//        try {
//            // Download voice (try to transcode SILK to WAV)
//            Path voicePath = downloader.downloadVoice(voiceItem, mediaDir, true);
//            log.info("Voice downloaded to: {}", voicePath);
//
//            // Return placeholder text (audio files need special handling)
//            return "[Voice message: " + voicePath.getFileName() + "]";
//        } finally {
//            downloader.close();
//        }
//    }
//
//    /**
//     * Get conversation ID for user
//     */
//    private String getConversationId(String userId) {
//        return "wechat-" + userId;
//    }
//
//    /**
//     * Shutdown the SDK
//     */
//    public void shutdown() {
//        if (weixinSdk != null) {
//            weixinSdk.shutdown();
//        }
//    }
//
//    /**
//     * Inner class for channel events
//     */
//    @Data
//    @SuperBuilder(toBuilder = true)
//    @EqualsAndHashCode(callSuper = true)
//    public static class WeClawBotMessageReceivedEvent extends ChannelReceiveMessage {
//        private String fromUserId;
//    }
//}
