package com.git.hui.jobclaw.gather.service;

import com.git.hui.jobclaw.agents.jobfetch.service.model.JobFetchTaskEntity;
import com.git.hui.jobclaw.agents.jobfetch.service.repository.JobFetchTaskRepository;
import com.git.hui.jobclaw.web.model.res.TaskVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 为 gather 任务列表补充 IM JobFetch 侧关联信息。
 */
@Service
public class JobFetchTaskEnricher {

    private final JobFetchTaskRepository jobFetchTaskRepository;

    @Autowired
    public JobFetchTaskEnricher(JobFetchTaskRepository jobFetchTaskRepository) {
        this.jobFetchTaskRepository = jobFetchTaskRepository;
    }

    public void enrich(List<TaskVo> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Set<Long> gatherTaskIds = tasks.stream()
                .filter(task -> GatherSourceService.RUNNER_IM_FETCH.equals(task.getRunnerType()))
                .map(TaskVo::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (gatherTaskIds.isEmpty()) {
            return;
        }

        Map<Long, JobFetchTaskEntity> linked = new HashMap<>();
        for (Long gatherTaskId : gatherTaskIds) {
            jobFetchTaskRepository.findFirstByGatherTaskId(gatherTaskId)
                    .ifPresent(entity -> linked.put(gatherTaskId, entity));
        }

        for (TaskVo task : tasks) {
            JobFetchTaskEntity entity = linked.get(task.getTaskId());
            if (entity == null) {
                continue;
            }
            task.setJobFetchBizTaskId(entity.getTaskId());
            task.setJobFetchChannel(entity.getChannel());
            task.setJobFetchUserId(entity.getJobClawUserId());
        }
    }
}
