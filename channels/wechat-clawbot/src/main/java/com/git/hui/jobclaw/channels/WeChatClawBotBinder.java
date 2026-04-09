package com.git.hui.jobclaw.channels;

import com.git.hui.jobclaw.channels.sdk.WeixinSdk;
import com.git.hui.jobclaw.channels.sdk.WeixinTypes;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户扫码绑定微信ClawBot
 *
 * @author YiHui
 * @date 2026/4/8
 */
@Slf4j
@RestController
@RequestMapping("/api/wechat/clawbot")
public class WeChatClawBotBinder {
    private static final String ACCOUNT_PREFIX = "agent.channels.wechat.clawbot.accounts";

    private final ConfigurationManager configurationManager;
    private final WxChatClawBotProperties wxChatClawBotProperties;
    private final WeixinSdk weixinSdk;

    public WeChatClawBotBinder(ConfigurationManager configurationManager, WxChatClawBotProperties wxChatClawBotProperties) {
        this.configurationManager = configurationManager;
        this.wxChatClawBotProperties = wxChatClawBotProperties;

        // 初始化 WeixinSdk (用于生成二维码和检查状态)
        this.weixinSdk = new WeixinSdk.Builder()
                .baseUrl(this.wxChatClawBotProperties.getBaseUrl())
                .cdnBaseUrl(this.wxChatClawBotProperties.getCdnBaseUrl())
                .token("")
                .channelVersion("1.0.3")
                .loginBuild();
    }

    /**
     * 生成绑定二维码
     */
    /**
     * 生成绑定二维码
     */
    @Operation(summary = "生成绑定二维码")
    @PostMapping("/qrcode")
    public BindQrInfo genBindQrCode() {
        try {
            WeixinTypes.BindQrCodeResp resp = weixinSdk.generateBindQrCode();

            if (resp.getQrcode() == null || resp.getQrcode().isBlank()) {
                log.error("Failed to get QR code: {}", resp.getErrmsg());
                return new BindQrInfo(false, "Failed to get QR code: " + resp.getErrmsg(), null, null);
            }

            log.info("QR code generated successfully");
            return new BindQrInfo(true, "success", resp.getQrcode(), resp.getQrcodeImgContent());
        } catch (Exception e) {
            log.error("Failed to get QR code", e);
            return new BindQrInfo(false, "Error: " + e.getMessage(), null, null);
        }
    }

    /**
     * 检查登录状态
     */
    @Operation(summary = "检查登录状态")
    @GetMapping("/status")
    public LoginStatus checkLoginStatus(@RequestParam String qrCode) {
        if (qrCode == null || qrCode.isBlank()) {
            return new LoginStatus(false, "No QR code available", null);
        }

        try {
            WeixinTypes.QrCodeStatusResp resp = weixinSdk.checkQrCodeStatus(qrCode);
            String status = resp.getStatus();

            switch (status) {
                case "wait":
                    return new LoginStatus(false, "Waiting for scan", null);

                case "scaned":
                    return new LoginStatus(false, "Scanned, but not logged in", null);

                case "expired":
                    return new LoginStatus(false, "QR code expired", null);

                case "confirmed":
                    String botToken = resp.getBotToken();
                    String botId = resp.getIlinkBotId();
                    String ilinkUserId = resp.getIlinkUserId();
                    // todo 需要保存用户信息
                    log.info("WeChat login successful! botToken:{} BotID: {}, UserID: {}",
                            botToken,
                            botId,
                            ilinkUserId);

                    Map<String, Object> obj = new HashMap<>();
                    obj.put(ACCOUNT_PREFIX + "[0].app-id", botId);
                    obj.put(ACCOUNT_PREFIX + "[0].app-secret", botToken);
                    obj.put(ACCOUNT_PREFIX + "[0].user-id", ilinkUserId);
                    obj.put(ACCOUNT_PREFIX + "[0].mode", "LOOP");
                    configurationManager.updateProperties(obj);

                    return new LoginStatus(true, botId, ilinkUserId);

                default:
                    return new LoginStatus(false, "Unknown status: " + status, null);
            }

        } catch (Exception e) {
            log.error("Failed to check login status", e);
            return new LoginStatus(false, e.getMessage(), null);
        }
    }


    public record BindQrInfo(boolean success, String msg, String qrCode, String qrUrl) {
    }

    public record LoginStatus(boolean success, String msg, String accountId) {
    }
}
