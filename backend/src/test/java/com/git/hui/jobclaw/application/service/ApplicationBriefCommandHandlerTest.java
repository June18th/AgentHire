package com.git.hui.jobclaw.application.service;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.web.model.res.JobApplicationBriefVo;
import com.git.hui.jobclaw.web.model.res.JobApplicationEventVo;
import com.git.hui.jobclaw.web.model.res.JobApplicationVo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationBriefCommandHandlerTest {

    @Test
    void rendersApplicationBriefForBoundUser() {
        JobApplicationService service = mock(JobApplicationService.class);
        ApplicationBriefCommandHandler handler = new ApplicationBriefCommandHandler(service);
        when(service.brief(7L, 5)).thenReturn(new JobApplicationBriefVo()
                .setTotal(3)
                .setActionCount(2)
                .setPriorityA(1)
                .setOverdueFollowUps(1)
                .setDueToday(1)
                .setDueSoon(2)
                .setStaleSubmitted(1)
                .setTodayEvents(1)
                .setNext7DayEvents(1)
                .setSummary("有 1 条跟进已到期，建议优先处理。")
                .setTopActions(List.of(new JobApplicationVo()
                        .setCompanyName("Alpha")
                        .setPosition("Backend")
                        .setActionPriority("A")
                        .setSuggestedNextAction("先完成本次跟进。")))
                .setUpcomingEvents(List.of(new JobApplicationEventVo()
                        .setEventTime(LocalDateTime.of(2026, 7, 8, 10, 30)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli())
                        .setEventType("INTERVIEW")
                        .setCompanyName("Alpha")
                        .setPosition("Backend")
                        .setSuggestedPreparation("准备项目复盘。"))));

        AtomicReference<String> response = new AtomicReference<>();

        boolean handled = handler.handle(message(), conversation("7"), "/brief", content -> {
            response.set(content);
            return true;
        });

        assertThat(handled).isTrue();
        assertThat(response.get()).contains("今日投递简报");
        assertThat(response.get()).contains("行动 2 项");
        assertThat(response.get()).contains("今日截止 1");
        assertThat(response.get()).contains("临近截止 2");
        assertThat(response.get()).contains("静默投递 1");
        assertThat(response.get()).contains("Alpha / Backend");
        assertThat(response.get()).contains("准备项目复盘");
        verify(service).brief(7L, 5);
    }

    @Test
    void supportsTodayAlias() {
        ApplicationBriefCommandHandler handler = new ApplicationBriefCommandHandler(mock(JobApplicationService.class));

        assertThat(handler.supports("/brief")).isTrue();
        assertThat(handler.supports("/today")).isTrue();
    }

    @Test
    void promptsWhenUserIdIsMissing() {
        ApplicationBriefCommandHandler handler = new ApplicationBriefCommandHandler(mock(JobApplicationService.class));
        AtomicReference<String> response = new AtomicReference<>();

        boolean handled = handler.handle(message(), conversation("wechat-user"), "/brief", content -> {
            response.set(content);
            return true;
        });

        assertThat(handled).isTrue();
        assertThat(response.get()).contains("还没有识别到你的 JobClaw 用户身份");
    }

    private static ChannelReceiveMessage message() {
        return ChannelReceiveMessage.builder()
                .msgId("m1")
                .channel("test")
                .fromUserId("u1")
                .jobClawUserId("7")
                .message("/brief")
                .build();
    }

    private static UserConversationInfo conversation(String jobClawUserId) {
        return new UserConversationInfo(jobClawUserId, "test", "u1", false);
    }
}
