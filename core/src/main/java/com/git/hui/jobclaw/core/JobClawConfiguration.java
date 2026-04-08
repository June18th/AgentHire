package com.git.hui.jobclaw.core;

import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author YiHui
 * @date 2026/4/8
 */
@Configuration
@ComponentScan("com.git.hui.jobclaw")
public class JobClawConfiguration {

    @Bean
    public ChannelRegistry channelRegistry() {
        return new ChannelRegistry();
    }

}
