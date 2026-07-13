package com.git.hui.jobclaw.gather.service;

import com.git.hui.jobclaw.constants.gather.GatherTargetTypeEnum;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.gather.dao.entity.GatherTaskEntity;
import com.git.hui.jobclaw.gather.model.GatherTaskResultBo;
import com.git.hui.jobclaw.gather.model.GatherTaskSaveBo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobFetchGatherBridgeServiceTest {

    @Test
    void registersUrlTaskAndReturnsGatherLink() throws Exception {
        GatherTaskService taskService = mock(GatherTaskService.class);
        when(taskService.directAddTask(any(), eq(GatherSourceService.OWNER_IM), eq(GatherSourceService.RUNNER_IM_FETCH)))
                .thenReturn(new GatherTaskEntity().setId(21L).setSourceId(8L));
        JobFetchGatherBridgeService bridge = new JobFetchGatherBridgeService(taskService);

        var link = bridge.register(conversation(), "URL", "https://example.com/jobs");

        assertThat(link.gatherTaskId()).isEqualTo(21L);
        assertThat(link.gatherSourceId()).isEqualTo(8L);
        ArgumentCaptor<GatherTaskSaveBo> captor = ArgumentCaptor.forClass(GatherTaskSaveBo.class);
        verify(taskService).directAddTask(captor.capture(), eq(GatherSourceService.OWNER_IM),
                eq(GatherSourceService.RUNNER_IM_FETCH));
        assertThat(captor.getValue().type()).isEqualTo(GatherTargetTypeEnum.HTTP_URL);
        assertThat(captor.getValue().model()).isEqualTo("user-7");
        assertThat(captor.getValue().content()).isEqualTo("https://example.com/jobs");
    }

    @Test
    void mapsFilesAndHtmlToExpectedTypes() throws Exception {
        GatherTaskService taskService = mock(GatherTaskService.class);
        when(taskService.directAddTask(any(), any(), any()))
                .thenReturn(new GatherTaskEntity().setId(1L).setSourceId(2L));
        JobFetchGatherBridgeService bridge = new JobFetchGatherBridgeService(taskService);

        bridge.register(conversation(), "FILE", "jobs.csv");
        bridge.register(conversation(), "FILE", "poster.png");
        bridge.register(conversation(), "TEXT", "<html><body>jobs</body></html>");

        ArgumentCaptor<GatherTaskSaveBo> captor = ArgumentCaptor.forClass(GatherTaskSaveBo.class);
        verify(taskService, org.mockito.Mockito.times(3)).directAddTask(captor.capture(), any(), any());
        assertThat(captor.getAllValues()).extracting(GatherTaskSaveBo::type)
                .containsExactly(GatherTargetTypeEnum.CSV_FILE, GatherTargetTypeEnum.IMAGE, GatherTargetTypeEnum.HTML_TEXT);
    }

    @Test
    void synchronizesRunningSuccessAndFailureResults() {
        GatherTaskService taskService = mock(GatherTaskService.class);
        JobFetchGatherBridgeService bridge = new JobFetchGatherBridgeService(taskService);

        bridge.markRunning(12L);
        bridge.markSuccess(12L, List.of(1L), List.of(2L));
        bridge.markFailed(13L, "network error");

        verify(taskService).markExternalTaskProcessing(12L);
        ArgumentCaptor<GatherTaskResultBo> captor = ArgumentCaptor.forClass(GatherTaskResultBo.class);
        verify(taskService).saveTaskResult(eq(12L), captor.capture());
        assertThat(captor.getValue().isSuccess()).isTrue();
        assertThat(captor.getValue().insertDraftIds()).containsExactly(1L);
        assertThat(captor.getValue().updateDraftIds()).containsExactly(2L);
        verify(taskService).saveTaskResult(eq(13L), captor.capture());
        assertThat(captor.getValue().isSuccess()).isFalse();
        assertThat(captor.getValue().msg()).isEqualTo("network error");
    }

    @Test
    void returnsNoLinkWhenRegistrationFails() throws Exception {
        GatherTaskService taskService = mock(GatherTaskService.class);
        when(taskService.directAddTask(any(), any(), any())).thenThrow(new IllegalStateException("db unavailable"));

        assertThat(new JobFetchGatherBridgeService(taskService)
                .register(conversation(), "TEXT", "job text")).isNull();
    }

    private static UserConversationInfo conversation() {
        return new UserConversationInfo("user-7", "wechat", "conversation-1", false);
    }
}
