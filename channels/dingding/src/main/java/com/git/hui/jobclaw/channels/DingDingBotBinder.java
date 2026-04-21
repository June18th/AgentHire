package com.git.hui.jobclaw.channels;

import com.git.hui.jobclaw.core.channel.ChannelBinder;
import com.git.hui.jobclaw.core.channel.ChannelConfig;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.permission.Permission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 钉钉机器人渠道绑定
 * @author YiHui
 * @date 2026/4/15
 */
@Slf4j
@Permission(role = UserRoleEnum.NORMAL)
@RestController
@RequestMapping("/api/dingding/bind")
public class DingDingBotBinder implements ChannelBinder {
    private final DingDingBotProperties dingDingBotProperties;
    private final DingDingBotChannel dingDingBotChannel;
    private final ConfigurationManager configurationManager;

    public DingDingBotBinder(DingDingBotProperties dingDingBotProperties, DingDingBotChannel dingDingBotChannel, ConfigurationManager configurationManager) {
        this.dingDingBotProperties = dingDingBotProperties;
        this.dingDingBotChannel = dingDingBotChannel;
        this.configurationManager = configurationManager;
    }

    private void registerAccountChangeCallback() {
        configurationManager.registerCallback(dingDingBotProperties, () -> {
            dingDingBotChannel.activeChannelAccounts();
        });
    }

    @Override
    public Map<Long, List<ChannelConfig>> getAllAccounts() {
        var map = dingDingBotProperties.getAccounts();
        Map<Long, List<ChannelConfig>> res = new HashMap<>();
        map.forEach((key, value) -> {
            List<ChannelConfig> list = new ArrayList<>(value);
            res.put(Long.valueOf(key), list);
        });
        return res;
    }

    @Override
    public void bindAccount(Long userId, ChannelConfig channelInfo) {
        dingDingBotChannel.addAccount(channelInfo);
    }
}
