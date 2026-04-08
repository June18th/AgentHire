package com.git.hui.jobclaw.core.channel;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 接收消息
 * @author YiHui
 * @date 2026/4/8
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class ChannelReceiveMessage {
    private String channel;
    private String message;
}
