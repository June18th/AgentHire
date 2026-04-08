package com.git.hui.jobclaw.core.channel;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 响应消息
 * @author YiHui
 * @date 2026/4/8
 */
@Data
@Builder
public class ChannelResponseMessage {
    private String toUserId;
    private ResponseMessageType type;
    private String content;
    private Map<String, Object> passThrough;


    public enum ResponseMessageType {
        TEXT("text"),
        MARKDOWN("markdown"),
        CARD("card"),
        ;

        private String type;

        ResponseMessageType(String type) {
            this.type = type;
        }
    }
}
