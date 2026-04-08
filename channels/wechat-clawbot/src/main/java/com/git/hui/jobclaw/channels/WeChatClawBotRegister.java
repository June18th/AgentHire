package com.git.hui.jobclaw.channels;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;

/**
 * 用户扫码绑定微信ClawBot
 *
 * @author YiHui
 * @date 2026/4/8
 */
@Slf4j
public class WeChatClawBotRegister {

    private final WxChatClawBotProperties wxChatClawBotProperties;
    private static final String DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    public WeChatClawBotRegister(WxChatClawBotProperties wxChatClawBotProperties) {
        this.wxChatClawBotProperties = wxChatClawBotProperties;
    }

    public BindQrInfo genBindQrCode() {
        try {
            HttpGet request = new HttpGet(DEFAULT_BASE_URL + "/ilink/bot/get_bot_qrcode?bot_type=3");
            request.setHeader("Content-Type", "application/json");
            request.setHeader("AuthorizationType", "ilink_bot_token");

            JsonNode response = httpClient.execute(request, res -> {
                String json = EntityUtils.toString(res.getEntity());
                return objectMapper.readTree(json);
            });

            String qrCode = response.path("qrcode").asText("");
            String qrImg = response.path("qrcode_img_content").asText("");

            if (qrCode.isBlank()) {
                return new BindQrInfo(false, "Failed to get QR code", null, null);
            }

            log.info("QR code generated successfully: " + qrImg);
            return new BindQrInfo(true, "success", qrCode, qrImg);
        } catch (Exception e) {
            log.error("Failed to get QR code", e);
            return new BindQrInfo(false, "Error: " + e.getMessage(), null, null);
        }
    }

    public record BindQrInfo(boolean success, String msg, String qrCode, String qrUrl) {
    }


    /**
     * Check login status
     */
    private String checkLoginStatus(String qrCode) {
        if (qrCode == null || qrCode.isBlank()) {
            return "{\"success\": false, \"error\": \"No QR code available\"}";
        }

        try {
            HttpGet request = new HttpGet(DEFAULT_BASE_URL + "/ilink/bot/get_qrcode_status?qrcode=" + qrCode);
            request.setHeader("Content-Type", "application/json");
            request.setHeader("AuthorizationType", "ilink_bot_token");
            request.setHeader("iLink-App-ClientVersion", "1");

            JsonNode response = httpClient.execute(request, res -> {
                String json = EntityUtils.toString(res.getEntity());
                return objectMapper.readTree(json);
            });

            String status = response.path("status").asText("");

            switch (status) {
                case "wait":
                    return "{\"success\": true, \"status\": \"waiting\"}";

                case "scaned":
                    return "{\"success\": true, \"status\": \"scanned\"}";

                case "expired":
                    return "{\"success\": true, \"status\": \"expired\"}";

                case "confirmed":
                    String botToken = response.path("bot_token").asText("");
                    String botId = response.path("ilink_bot_id").asText("");
                    String ilinkUserId = response.path("ilink_user_id").asText("");
                    // todo 需要保存用户信息
                    log.info("WeChat login successful! BotID: {}", botId);
                    return "{\"success\": true, \"status\": \"success\", \"bot_id\": \"" + botId + "\"}";

                default:
                    return "{\"success\": false, \"error\": \"Unknown status: \" + status}";
            }

        } catch (Exception e) {
            log.error("Failed to check login status", e);
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
