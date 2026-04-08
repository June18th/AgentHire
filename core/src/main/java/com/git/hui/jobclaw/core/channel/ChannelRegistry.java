package com.git.hui.jobclaw.core.channel;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class ChannelRegistry {

    private final Map<String, Channel> channels;
    private String defaultChannelName;

    public ChannelRegistry() {
        this.channels = new HashMap<>();
    }

    public void registerChannel(Channel channel) {
        channels.put(channel.name(), channel);
        if (channels.size() == 1) {
            this.defaultChannelName = channel.name();
        }
    }

    public Channel getChannel(String channelName) {
        if (StringUtils.isBlank(channelName)) {
            return channels.get(defaultChannelName);
        }
        return channels.get(channelName);
    }
}
