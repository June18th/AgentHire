package com.git.hui.jobclaw.channels;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 微信ClawBot相关的配置
 * @author YiHui
 * @date 2026/4/8
 */
@Data
@ConfigurationProperties(prefix = "agent.channels.wechat.clawbot")
public class WxChatClawBotProperties {
    private boolean enabled;
    private String baseUrl;
    private String cdnBaseUrl;

    private List<WxClawBotAccount> accounts;
}
