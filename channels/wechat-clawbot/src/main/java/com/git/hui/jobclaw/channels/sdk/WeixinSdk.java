package com.git.hui.jobclaw.channels.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Complete Weixin ClawBot SDK.
 *
 * This SDK provides a complete implementation of the Weixin iLink Bot API protocol,
 * including:
 * - Message receiving (long polling via getUpdates)
 * - Text message sending
 * - Media message sending (images, videos, files)
 * - Typing indicators
 * - Context token management
 * - CDN upload with AES-128-ECB encryption
 *
 * Usage example:
 * <pre>{@code
 * WeixinSdk sdk = new WeixinSdk.Builder()
 *     .baseUrl("https://ilinkai.weixin.qq.com")
 *     .cdnBaseUrl("https://novac2c.cdn.weixin.qq.com/c2c")
 *     .token("your-bot-token")
 *     .channelVersion("1.0.3")
 *     .stateDir("./workspace/weixin-state")
 *     .accountId("my-account")
 *     .build();
 *
 * // Start listening for messages
 * sdk.startPolling(messages -> {
 *     System.out.println("Received: " + messages);
 * });
 *
 * // Send a text message
 * sdk.getMessageSender().sendTextMessage(userId, "Hello!", contextToken);
 *
 * // Send an image
 * sdk.getMessageSender().sendImageMessage(userId, "Check this out", "/path/to/image.jpg", contextToken);
 * }</pre>
 */
public class WeixinSdk {

    private static final Logger log = LoggerFactory.getLogger(WeixinSdk.class);

    private final WeixinApiClient apiClient;
    private final CdnUploader cdnUploader;
    private final MessageSender messageSender;
    private final ContextTokenManager contextTokenManager;
    private final String accountId;
    private final String baseUrl;
    private final String cdnBaseUrl;

    private WeixinSdk(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.cdnBaseUrl = builder.cdnBaseUrl;
        this.accountId = builder.accountId;

        this.apiClient = new WeixinApiClient(builder.baseUrl, builder.token, builder.channelVersion);
        this.cdnUploader = new CdnUploader(apiClient, builder.cdnBaseUrl);
        this.messageSender = new MessageSender(apiClient, cdnUploader);
        this.contextTokenManager = new ContextTokenManager(builder.stateDir);

        // Restore persisted context tokens
        if (accountId != null) {
            contextTokenManager.restoreContextTokens(accountId);
        }
    }

    /**
     * Get the API client for direct API calls.
     */
    public WeixinApiClient getApiClient() {
        return apiClient;
    }

    /**
     * Get the message sender for high-level message operations.
     */
    public MessageSender getMessageSender() {
        return messageSender;
    }

    /**
     * Get the context token manager.
     */
    public ContextTokenManager getContextTokenManager() {
        return contextTokenManager;
    }

    /**
     * Get account ID.
     */
    public String getAccountId() {
        return accountId;
    }

    /**
     * Get base URL.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Get CDN base URL.
     */
    public String getCdnBaseUrl() {
        return cdnBaseUrl;
    }

    /**
     * Start polling for messages in background thread.
     *
     * @param messageHandler Callback to handle received messages
     * @return Polling thread
     */
    public PollingWorker startPolling(MessageHandler messageHandler) {
        PollingWorker worker = new PollingWorker(this, messageHandler);
        Thread thread = new Thread(worker);
        thread.setDaemon(true);
        thread.setName("Weixin-Polling-" + (accountId != null ? accountId : "default"));
        thread.start();
        return worker;
    }

    /**
     * Stop polling and clean up resources.
     */
    public void shutdown() {
        try {
            apiClient.close();
            log.info("Weixin SDK shutdown complete");
        } catch (Exception e) {
            log.error("Error during shutdown", e);
        }
    }

    /**
     * Functional interface for handling received messages.
     */
    @FunctionalInterface
    public interface MessageHandler {
        void onMessage(WeixinTypes.WeixinMessage message);
    }

    /**
     * Builder for constructing WeixinSdk instances.
     */
    public static class Builder {
        private String baseUrl = "https://ilinkai.weixin.qq.com";
        private String cdnBaseUrl = "https://novac2c.cdn.weixin.qq.com/c2c";
        private String token;
        private String channelVersion = "1.0.3";
        private String stateDir = "./workspace/weixin-state";
        private String accountId;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder cdnBaseUrl(String cdnBaseUrl) {
            this.cdnBaseUrl = cdnBaseUrl;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder channelVersion(String channelVersion) {
            this.channelVersion = channelVersion;
            return this;
        }

        public Builder stateDir(String stateDir) {
            this.stateDir = stateDir;
            return this;
        }

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public WeixinSdk build() {
            if (token == null || token.trim().isEmpty()) {
                throw new IllegalArgumentException("Bot token is required");
            }
            return new WeixinSdk(this);
        }
    }

    /**
     * Background polling worker.
     */
    public static class PollingWorker implements Runnable {
        private final WeixinSdk sdk;
        private final MessageHandler messageHandler;
        private volatile boolean running = true;
        private String getUpdatesBuf = "";
        private int timeout = 35;

        PollingWorker(WeixinSdk sdk, MessageHandler messageHandler) {
            this.sdk = sdk;
            this.messageHandler = messageHandler;
        }

        @Override
        public void run() {
            log.info("Weixin polling started for account={}", sdk.getAccountId());

            while (running) {
                try {
                    WeixinTypes.GetUpdatesResp resp = sdk.getApiClient().getUpdates(getUpdatesBuf, timeout * 1000);
                    if (log.isDebugEnabled()) {
                        log.debug("Get updates response: {}", resp);
                    }

                    // Check for errors
                    if (resp.getRet() != null && resp.getRet() != 0) {
                        log.error("Get updates error: ret={} errcode={} errmsg={}",
                                resp.getRet(), resp.getErrcode(), resp.getErrmsg());

                        // Session expired
                        if (resp.getErrcode() != null &&
                                (resp.getErrcode() == 40001 || resp.getErrcode() == 40014 || resp.getErrcode() == 42001)) {
                            log.error("Session expired, please re-login");
                            Thread.sleep(5000);
                        }
                        continue;
                    }

                    // Update timeout
                    if (resp.getLongpollingTimeoutMs() != null) {
                        timeout = resp.getLongpollingTimeoutMs() / 1000 + 10;
                    }

                    // Update cursor
                    if (resp.getGetUpdatesBuf() != null) {
                        this.getUpdatesBuf = resp.getGetUpdatesBuf();
                    }

                    // Process messages
                    if (resp.getMsgs() != null && !resp.getMsgs().isEmpty()) {
                        log.info("Received {} messages", resp.getMsgs().size());

                        for (WeixinTypes.WeixinMessage msg : resp.getMsgs()) {
                            // Store context token
                            if (msg.getFromUserId() != null && !msg.getFromUserId().isEmpty() &&
                                    msg.getContextToken() != null && !msg.getContextToken().isEmpty()) {
                                sdk.getContextTokenManager().setContextToken(
                                        sdk.getAccountId() != null ? sdk.getAccountId() : "default",
                                        msg.getFromUserId(),
                                        msg.getContextToken()
                                );
                            }

                            // Call handler
                            messageHandler.onMessage(msg);
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in polling loop", e);
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            log.info("Weixin polling stopped for account={}", sdk.getAccountId());
        }

        public void stop() {
            running = false;
        }

        public boolean isRunning() {
            return running;
        }
    }
}
