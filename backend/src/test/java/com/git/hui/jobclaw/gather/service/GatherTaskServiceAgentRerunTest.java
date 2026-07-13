package com.git.hui.jobclaw.gather.service;

import com.git.hui.jobclaw.constants.gather.GatherTargetTypeEnum;
import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.gather.dao.entity.GatherSourceEntity;
import com.git.hui.jobclaw.gather.dao.entity.GatherTaskEntity;
import com.git.hui.jobclaw.gather.dao.repository.GatherTaskRepository;
import com.git.hui.jobclaw.gather.service.helper.LocalStorageHelper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatherTaskServiceAgentRerunTest {

    @Test
    void createsNewAgentTaskFromOriginalSourceWithoutSchedulingOfferGather() {
        GatherTaskRepository repository = mock(GatherTaskRepository.class);
        GatherSourceService sourceService = mock(GatherSourceService.class);
        GatherTaskService service = service(repository, sourceService);
        GatherTaskEntity original = new GatherTaskEntity()
                .setId(10L).setSourceId(3L).setRunnerType(GatherSourceService.RUNNER_AGENT).setModel("zhipu#glm");
        GatherSourceEntity source = new GatherSourceEntity()
                .setId(3L).setType(GatherTargetTypeEnum.TEXT.getValue()).setVersion(4)
                .setRunnerType(GatherSourceService.RUNNER_AGENT).setContent("job content");
        when(repository.findById(10L)).thenReturn(Optional.of(original));
        when(sourceService.getSource(3L)).thenReturn(source);
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any(GatherTaskEntity.class))).thenAnswer(invocation -> {
            GatherTaskEntity saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        assertThat(service.reRunAgentTask(10L)).isEqualTo(11L);

        ArgumentCaptor<GatherTaskEntity> captor = ArgumentCaptor.forClass(GatherTaskEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        GatherTaskEntity rerun = captor.getValue();
        assertThat(rerun.getSourceId()).isEqualTo(3L);
        assertThat(rerun.getSourceVersion()).isEqualTo(4);
        assertThat(rerun.getRunnerType()).isEqualTo(GatherSourceService.RUNNER_AGENT);
        assertThat(rerun.getModel()).isEqualTo("zhipu#glm");
        assertThat(rerun.getContent()).isEqualTo("job content");
        verify(sourceService).markTaskCreated(source, rerun);
    }

    @Test
    void rejectsNonAgentTaskAndTaskWithoutSource() {
        GatherTaskRepository repository = mock(GatherTaskRepository.class);
        GatherTaskService service = service(repository, mock(GatherSourceService.class));
        when(repository.findById(1L)).thenReturn(Optional.of(new GatherTaskEntity()
                .setId(1L).setRunnerType(GatherSourceService.RUNNER_DRAFT_ONLY).setSourceId(2L)));
        when(repository.findById(2L)).thenReturn(Optional.of(new GatherTaskEntity()
                .setId(2L).setRunnerType(GatherSourceService.RUNNER_AGENT)));

        assertThatThrownBy(() -> service.reRunAgentTask(1L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.reRunAgentTask(2L)).isInstanceOf(BizException.class);
    }

    private static GatherTaskService service(GatherTaskRepository repository, GatherSourceService sourceService) {
        return new GatherTaskService(repository, mock(LocalStorageHelper.class), sourceService,
                mock(JobFetchTaskEnricher.class));
    }
}
