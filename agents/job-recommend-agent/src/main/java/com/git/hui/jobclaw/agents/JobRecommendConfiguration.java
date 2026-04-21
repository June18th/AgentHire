package com.git.hui.jobclaw.agents;

import com.git.hui.jobclaw.core.agent.llm.ClientSelector;
import com.git.hui.jobclaw.plugins.jobs.JobLibraryTool;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author YiHui
 * @date 2026/4/17
 */
@Configuration
public class JobRecommendConfiguration {

    @Bean
    public JobRecommendAgent jobRecommendAgent(ClientSelector clientSelector, ChatMemory chatMemory, JobLibraryTool jobLibraryTool) {
        return new JobRecommendAgent(clientSelector, chatMemory, jobLibraryTool);
    }

}
