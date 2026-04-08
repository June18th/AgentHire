package com.git.hui.jobclaw.channels.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Example usage of Weixin ClawBot SDK.
 * 
 * This example demonstrates:
 * 1. Creating an SDK instance
 * 2. Starting message polling
 * 3. Handling different message types (text, image, video, file, voice)
 * 4. Sending text, image, video, and file messages
 * 5. Using typing indicators
 * 6. Downloading and processing media files
 */
public class WeixinSdkExample {

    private static final Logger log = LoggerFactory.getLogger(WeixinSdkExample.class);
    private static final String MEDIA_DIR = "./workspace/weixin-media";

    public static void main(String[] args) {
        // Create SDK instance
        WeixinSdk sdk = new WeixinSdk.Builder()
            .baseUrl("https://ilinkai.weixin.qq.com")
            .cdnBaseUrl("https://novac2c.cdn.weixin.qq.com/c2c")
            .token("your-bot-token-here")
            .channelVersion("1.0.3")
            .stateDir("./workspace/weixin-state")
            .accountId("example-account")
            .build();

        log.info("Weixin SDK initialized");

        // Start polling for messages
        Thread pollingThread = sdk.startPolling(message -> {
            try {
                handleMessage(sdk, message);
            } catch (Exception e) {
                log.error("Error handling message", e);
            }
        });

        log.info("Message polling started on thread: {}", pollingThread.getName());

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Weixin SDK...");
            sdk.shutdown();
        }));

        // Keep the application running
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Handle incoming message.
     */
    private static void handleMessage(WeixinSdk sdk, WeixinTypes.WeixinMessage message) throws Exception {
        String fromUser = message.getFromUserId();
        String contextToken = message.getContextToken();
        String text = MessageBuilder.extractText(message);

        log.info("Received message from {}: {}", fromUser, text);

        // Show typing indicator
        showTypingIndicator(sdk, fromUser, contextToken);

        // Process different message types
        if (message.getItemList() != null) {
            for (WeixinTypes.MessageItem item : message.getItemList()) {
                if (item.getType() != null) {
                    switch (item.getType()) {
                        case WeixinTypes.MessageItemType.TEXT:
                            handleTextMessage(sdk, fromUser, text, contextToken);
                            break;
                        case WeixinTypes.MessageItemType.IMAGE:
                            handleImageMessage(sdk, fromUser, item.getImageItem(), contextToken);
                            break;
                        case WeixinTypes.MessageItemType.VIDEO:
                            handleVideoMessage(sdk, fromUser, item.getVideoItem(), contextToken);
                            break;
                        case WeixinTypes.MessageItemType.FILE:
                            handleFileMessage(sdk, fromUser, item.getFileItem(), contextToken);
                            break;
                        case WeixinTypes.MessageItemType.VOICE:
                            handleVoiceMessage(sdk, fromUser, item.getVoiceItem(), contextToken);
                            break;
                    }
                }
            }
        }
    }

    /**
     * Handle text message.
     */
    private static void handleTextMessage(WeixinSdk sdk, String fromUser, String text, String contextToken) throws Exception {
        if (text == null || text.isEmpty()) {
            return;
        }

        // Simple echo bot example
        if (text.equalsIgnoreCase("hello") || text.equalsIgnoreCase("hi")) {
            sdk.getMessageSender().sendTextMessage(
                fromUser,
                "Hello! How can I help you today?",
                contextToken
            );
        } else if (text.equalsIgnoreCase("image")) {
            // Send an image (you would replace this with actual image path)
            sdk.getMessageSender().sendImageMessage(
                fromUser,
                "Here's an image for you",
                "/path/to/sample-image.jpg",
                contextToken
            );
        } else if (text.equalsIgnoreCase("help")) {
            String helpText = "Available commands:\n" +
                            "- hello: Greet me\n" +
                            "- image: Receive a sample image\n" +
                            "- help: Show this help message";
            sdk.getMessageSender().sendTextMessage(fromUser, helpText, contextToken);
        } else {
            // Echo back
            sdk.getMessageSender().sendTextMessage(
                fromUser,
                "You said: " + text,
                contextToken
            );
        }
    }

    /**
     * Show typing indicator before responding.
     */
    private static void showTypingIndicator(WeixinSdk sdk, String fromUser, String contextToken) {
        try {
            String typingTicket = sdk.getMessageSender().getTypingTicket(fromUser, contextToken);
            if (typingTicket != null && !typingTicket.isEmpty()) {
                // Show typing
                sdk.getMessageSender().sendTyping(fromUser, typingTicket, WeixinTypes.TypingStatus.TYPING);
                
                // Simulate processing time
                Thread.sleep(1000);
                
                // Cancel typing
                sdk.getMessageSender().sendTyping(fromUser, typingTicket, WeixinTypes.TypingStatus.CANCEL);
            }
        } catch (Exception e) {
            log.warn("Failed to show typing indicator: {}", e.getMessage());
        }
    }

    /**
     * Handle image message - download and respond.
     */
    private static void handleImageMessage(WeixinSdk sdk, String fromUser, 
                                          WeixinTypes.ImageItem imageItem, 
                                          String contextToken) throws Exception {
        log.info("Processing image message");
        
        // Create media downloader
        MediaDownloader downloader = new MediaDownloader(sdk.getCdnBaseUrl());
        
        try {
            // Download and decrypt image
            Path imagePath = downloader.downloadImage(imageItem, MEDIA_DIR, null);
            log.info("Image downloaded to: {}", imagePath);
            
            // Process the image (e.g., analyze with AI, resize, etc.)
            // For demo, just acknowledge receipt
            sdk.getMessageSender().sendTextMessage(
                fromUser,
                "收到图片！已保存到: " + imagePath.getFileName(),
                contextToken
            );
            
            // Optional: Send the image back
            // sdk.getMessageSender().sendImageMessage(fromUser, "这是你发的图片", imagePath.toString(), contextToken);
            
        } finally {
            downloader.close();
        }
    }

    /**
     * Handle video message - download and respond.
     */
    private static void handleVideoMessage(WeixinSdk sdk, String fromUser,
                                          WeixinTypes.VideoItem videoItem,
                                          String contextToken) throws Exception {
        log.info("Processing video message");
        
        MediaDownloader downloader = new MediaDownloader(sdk.getCdnBaseUrl());
        
        try {
            // Download and decrypt video
            Path videoPath = downloader.downloadVideo(videoItem, MEDIA_DIR, null);
            log.info("Video downloaded to: {}", videoPath);
            
            sdk.getMessageSender().sendTextMessage(
                fromUser,
                "收到视频！已保存到: " + videoPath.getFileName(),
                contextToken
            );
            
        } finally {
            downloader.close();
        }
    }

    /**
     * Handle file attachment - download and respond.
     */
    private static void handleFileMessage(WeixinSdk sdk, String fromUser,
                                         WeixinTypes.FileItem fileItem,
                                         String contextToken) throws Exception {
        log.info("Processing file message: {}", fileItem.getFileName());
        
        MediaDownloader downloader = new MediaDownloader(sdk.getCdnBaseUrl());
        
        try {
            // Download and decrypt file
            String fileName = fileItem.getFileName() != null ? fileItem.getFileName() : "unknown_file";
            Path filePath = downloader.downloadFile(fileItem, MEDIA_DIR, fileName);
            log.info("File downloaded to: {}", filePath);
            
            sdk.getMessageSender().sendTextMessage(
                fromUser,
                "收到文件: " + fileName + " (" + fileItem.getLen() + " bytes)",
                contextToken
            );
            
        } finally {
            downloader.close();
        }
    }

    /**
     * Handle voice message - download, transcode, and respond.
     */
    private static void handleVoiceMessage(WeixinSdk sdk, String fromUser,
                                          WeixinTypes.VoiceItem voiceItem,
                                          String contextToken) throws Exception {
        log.info("Processing voice message");
        
        // Check if voice-to-text is available
        if (voiceItem.getText() != null && !voiceItem.getText().isEmpty()) {
            log.info("Voice text: {}", voiceItem.getText());
            
            // Use the transcribed text for processing
            sdk.getMessageSender().sendTextMessage(
                fromUser,
                "听到你说: " + voiceItem.getText(),
                contextToken
            );
        }
        
        MediaDownloader downloader = new MediaDownloader(sdk.getCdnBaseUrl());
        
        try {
            // Download voice (try to transcode SILK to WAV)
            Path voicePath = downloader.downloadVoice(voiceItem, MEDIA_DIR, true);
            log.info("Voice downloaded to: {}", voicePath);
            
            // If no text transcription, acknowledge audio receipt
            if (voiceItem.getText() == null || voiceItem.getText().isEmpty()) {
                sdk.getMessageSender().sendTextMessage(
                    fromUser,
                    "收到语音消息！已保存到: " + voicePath.getFileName(),
                    contextToken
                );
            }
            
        } finally {
            downloader.close();
        }
    }
}
