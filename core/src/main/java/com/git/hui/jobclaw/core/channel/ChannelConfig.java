package com.git.hui.jobclaw.core.channel;

import lombok.Builder;
import lombok.Data;

/**
 * 通道集成的配置信息
 * @author YiHui
 * @date 2026/4/8
 */
@Data
@Builder
public class ChannelConfig {
    private String appId;
    private String appSecret;
    private ConnectionMode mode;

    public enum ConnectionMode {
        WEBSOCKET,
        WEBHOOK,
        LOOP,
        ;
    }
}
