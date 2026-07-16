package com.git.hui.jobclaw.agents.jobfetch;

import com.git.hui.jobclaw.agents.jobfetch.service.JobFetchService;
import com.git.hui.jobclaw.agents.jobfetch.service.model.JobFetchTaskResponse;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobAgentToolsTest {

    @Test
    void searchToolCreatesSearchTaskFromToolContext() {
        JobFetchService service = mock(JobFetchService.class);
        JobAgentTools tools = new JobAgentTools(service);
        UserConversationInfo user = new UserConversationInfo("2", "test", "conversation-1", false);
        ChannelReceiveMessage message = ChannelReceiveMessage.builder()
                .msgId("msg-1")
                .jobClawUserId("2")
                .fromUserId("conversation-1")
                .channel("test")
                .message("搜索北京 Java 实习")
                .build();
        JobFetchTaskResponse response = JobFetchTaskResponse.builder()
                .taskId("job_search_1")
                .status("PENDING")
                .jobCount(0)
                .build();
        when(service.searchJobs(user, "北京 Java 实习", message)).thenReturn(response);

        String result = tools.searchJobs(
                "北京 Java 实习", new ToolContext(Map.of("user", user, "msg", message)));

        verify(service).searchJobs(user, "北京 Java 实习", message);
        assertThat(result).contains("联网搜索", "job_search_1", "等待执行");
    }
}
