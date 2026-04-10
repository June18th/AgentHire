package com.git.hui.jobclaw.core.channel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 通道集成的配置信息
 * @author YiHui
 * @date 2026/4/8
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelConfig {
    private String appId;
    private String appSecret;
    private ConnectionMode mode;
    private ChannelState state;

    public enum ConnectionMode {
        WEBSOCKET,
        WEBHOOK,
        LOOP,
        ;
    }

    public enum ChannelState {
        NORMAL,
        ERROR,
        ;
    }
}
