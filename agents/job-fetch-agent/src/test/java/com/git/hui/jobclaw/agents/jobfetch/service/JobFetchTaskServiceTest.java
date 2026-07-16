package com.git.hui.jobclaw.agents.jobfetch.service;

import com.git.hui.jobclaw.agents.jobfetch.crawler.JobCrawler;
import com.git.hui.jobclaw.agents.jobfetch.search.JobSearchCandidate;
import com.git.hui.jobclaw.agents.jobfetch.search.JobSearchProperties;
import com.git.hui.jobclaw.agents.jobfetch.search.JobSearchProvider;
import com.git.hui.jobclaw.agents.jobfetch.service.model.FetchedJobInfo;
import com.git.hui.jobclaw.agents.jobfetch.service.model.JobFetchTaskEntity;
import com.git.hui.jobclaw.agents.jobfetch.service.repository.JobFetchTaskRepository;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import org.junit.jupiter.api.Test;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobFetchTaskServiceTest {

    @Test
    void generatesUniqueDatabaseSafeTaskIds() {
        var ids = IntStream.range(0, 10_000)
                .mapToObj(ignored -> JobFetchTaskService.generateTaskId())
                .toList();

        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids).allSatisfy(id -> {
            assertThat(id).startsWith("job_");
            assertThat(id.length()).isLessThanOrEqualTo(64);
        });
    }

    @Test
    void restoresSearchTaskAndContinuesAfterOneCandidateFails() {
        TestFixture fixture = fixture();
        JobFetchTaskEntity task = searchTask();
        when(fixture.taskRepository.findByTaskId(task.getTaskId())).thenReturn(Optional.of(task));
        when(fixture.searchProvider.search("北京 Java 实习", 8)).thenReturn(List.of(
                candidate("https://8.8.8.8/jobs/failed"),
                candidate("https://8.8.8.8/jobs/success")));
        FetchedJobInfo job = job("示例公司", "Java 实习生");
        when(fixture.jobCrawler.crawl(any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("blocked"))
                .thenReturn(List.of(job));
        when(fixture.persistService.save(anyList(), isNull(), isNull()))
                .thenReturn(new JobInfoPersistService.SaveRes(1, 0));

        fixture.service.executePersistedTask(task.getTaskId());

        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        assertThat(task.getJobCount()).isEqualTo(1);
        assertThat(task.getErrorMessage()).isEqualTo("部分页面未解析成功: 1/2");
        assertThat(job.getRelatedLink()).isEqualTo("https://8.8.8.8/jobs/success");
        assertThat(job.getSource()).contains("智谱搜索", "https://8.8.8.8/jobs/success");
        verify(fixture.persistService).save(anyList(), isNull(), isNull());
    }

    @Test
    void failsSearchTaskWhenEveryCandidateCannotBeParsed() {
        TestFixture fixture = fixture();
        JobFetchTaskEntity task = searchTask();
        when(fixture.searchProvider.search("北京 Java 实习", 8))
                .thenReturn(List.of(candidate("https://8.8.8.8/jobs/failed")));
        when(fixture.jobCrawler.crawl(any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("blocked"));

        assertThatThrownBy(() -> fixture.service.executeSearchTaskAsync(
                task,
                new UserConversationInfo("2", "test", "conversation-1", false),
                task.getInputContent(),
                message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Job search task failed");

        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getErrorMessage()).contains("均未解析出职位");
        verify(fixture.persistService, never()).save(anyList(), any(), any());
    }

    private TestFixture fixture() {
        JobFetchTaskService service = new JobFetchTaskService();
        JobFetchTaskRepository taskRepository = mock(JobFetchTaskRepository.class);
        JobCrawler jobCrawler = mock(JobCrawler.class);
        JobInfoPersistService persistService = mock(JobInfoPersistService.class);
        ChannelEventPublisher publisher = mock(ChannelEventPublisher.class);
        JobSearchProvider searchProvider = mock(JobSearchProvider.class);
        JobSearchProperties properties = new JobSearchProperties();
        properties.setMaxResults(8);
        properties.setMaxPages(5);

        when(searchProvider.provider()).thenReturn("zhipu");
        when(searchProvider.isAvailable()).thenReturn(true);
        ReflectionTestUtils.setField(service, "taskRepository", taskRepository);
        ReflectionTestUtils.setField(service, "jobCrawler", jobCrawler);
        ReflectionTestUtils.setField(service, "jobExtractorList", List.of());
        ReflectionTestUtils.setField(service, "channelEventPublisher", publisher);
        ReflectionTestUtils.setField(service, "jobInfoSaveService", persistService);
        ReflectionTestUtils.setField(service, "jobScheduler", mock(JobScheduler.class));
        ReflectionTestUtils.setField(service, "jobSearchProviders", List.of(searchProvider));
        ReflectionTestUtils.setField(service, "jobSearchProperties", properties);
        return new TestFixture(service, taskRepository, jobCrawler, persistService, searchProvider);
    }

    private JobFetchTaskEntity searchTask() {
        return new JobFetchTaskEntity()
                .setTaskId("job_search_1")
                .setJobClawUserId("2")
                .setChannel("test")
                .setConversionId("conversation-1")
                .setTaskType("SEARCH")
                .setInputContent("北京 Java 实习")
                .setOriginMessage("帮我搜索北京 Java 实习")
                .setStatus("PENDING")
                .setJobCount(0);
    }

    private JobSearchCandidate candidate(String url) {
        return new JobSearchCandidate("Java 实习招聘", url, "2026 校招岗位", "智谱搜索", "2026-07-16");
    }

    private FetchedJobInfo job(String company, String position) {
        FetchedJobInfo job = new FetchedJobInfo();
        job.setCompanyName(company);
        job.setPosition(position);
        return job;
    }

    private ChannelReceiveMessage message() {
        return ChannelReceiveMessage.builder()
                .msgId("msg-1")
                .jobClawUserId("2")
                .fromUserId("conversation-1")
                .channel("test")
                .message("帮我搜索北京 Java 实习")
                .build();
    }

    private record TestFixture(JobFetchTaskService service,
                               JobFetchTaskRepository taskRepository,
                               JobCrawler jobCrawler,
                               JobInfoPersistService persistService,
                               JobSearchProvider searchProvider) {
    }
}
