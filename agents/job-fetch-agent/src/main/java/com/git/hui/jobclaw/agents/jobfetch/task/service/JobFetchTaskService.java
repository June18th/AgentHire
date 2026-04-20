package com.git.hui.jobclaw.agents.jobfetch.task.service;

import com.git.hui.jobclaw.agents.jobfetch.crawler.JobCrawler;
import com.git.hui.jobclaw.agents.jobfetch.extract.JobExtractor;
import com.git.hui.jobclaw.agents.jobfetch.extract.impl.TextJobExtractor;
import com.git.hui.jobclaw.agents.jobfetch.model.JobInfo;
import com.git.hui.jobclaw.agents.jobfetch.task.model.JobFetchTaskEntity;
import com.git.hui.jobclaw.agents.jobfetch.task.model.JobFetchTaskResponse;
import com.git.hui.jobclaw.agents.jobfetch.task.model.JobFetchTaskStatus;
import com.git.hui.jobclaw.agents.jobfetch.task.repository.JobFetchTaskRepository;
import com.git.hui.jobclaw.core.agent.LlmCaller;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 职位抓取任务服务
 *
 * @author YiHui
 * @date 2026/4/20
 */
@Slf4j
@Service
public class JobFetchTaskService {

    @Autowired
    private JobFetchTaskRepository taskRepository;

    @Autowired
    private JobCrawler jobCrawler;

    @Autowired
    private List<JobExtractor> jobExtractorList;

    @Autowired
    private ChannelEventPublisher channelEventPublisher;

    /**
     * 创建URL抓取任务
     */
    public JobFetchTaskResponse createUrlTask(LlmCaller.UserConversationInfo userConversationInfo,
                                              String url,
                                              ChannelReceiveMessage msg) {
        String taskId = generateTaskId();

        // 创建任务记录
        JobFetchTaskEntity task = new JobFetchTaskEntity()
                .setTaskId(taskId)
                .setJobClawUserId(userConversationInfo.jobClawUserId())
                .setChannel(userConversationInfo.channel())
                .setConversionId(userConversationInfo.conversationId())
                .setTaskType("URL")
                .setInputContent(url)
                .setOriginMessage(msg.getMessage())
                .setStatus(JobFetchTaskStatus.PENDING.name())
                .setJobCount(0);

        taskRepository.save(task);
        log.info("创建URL抓取任务: taskId={}, url={}", taskId, url);

        // 异步执行任务
        Thread.ofVirtual().start(() -> executeUrlTaskAsync(task, userConversationInfo, url, msg));
        return buildTaskResponse(task);
    }

    /**
     * 创建文本/文件提取任务
     */
    public JobFetchTaskResponse createTextOrFileTask(LlmCaller.UserConversationInfo userConversationInfo,
                                                     String text,
                                                     String path,
                                                     ChannelReceiveMessage msg) {
        String taskId = generateTaskId();

        // 创建任务记录
        JobFetchTaskEntity task = new JobFetchTaskEntity()
                .setTaskId(taskId)
                .setJobClawUserId(userConversationInfo.jobClawUserId())
                .setChannel(userConversationInfo.channel())
                .setConversionId(userConversationInfo.conversationId())
                .setTaskType(CollectionUtils.isEmpty(msg.getFiles()) && CollectionUtils.isEmpty(msg.getMedias()) ? "TEXT" : "FILE")
                .setInputContent(text != null ? text : path)
                .setOriginMessage(msg.getMessage())
                .setStatus(JobFetchTaskStatus.PENDING.name())
                .setJobCount(0);

        taskRepository.save(task);
        log.info("创建文本/文件提取任务: taskId={}, type={}", taskId, task.getTaskType());

        // 异步执行任务
        Thread.ofVirtual().start(() -> executeTextOrFileTaskAsync(task, userConversationInfo, text, path, msg));
        return buildTaskResponse(task);
    }

    /**
     * 查询任务状态
     */
    public JobFetchTaskResponse queryTask(String jobClawUserId, String taskId) {
        JobFetchTaskEntity task = taskRepository.findByJobClawUserIdAndTaskId(jobClawUserId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));

        return buildTaskResponse(task);
    }

    /**
     * 查询用户的任务列表(按创建时间倒序)
     */
    public List<JobFetchTaskResponse> listTasks(String jobClawUserId) {
        List<JobFetchTaskEntity> tasks = taskRepository.findByJobClawUserIdOrderByCreateTimeDesc(jobClawUserId);
        return tasks.stream()
                .map(this::buildTaskResponse)
                .toList();
    }

    /**
     * 异步执行URL抓取任务
     */
    @Async
    public void executeUrlTaskAsync(JobFetchTaskEntity task,
                                    LlmCaller.UserConversationInfo userConversationInfo,
                                    String url,
                                    ChannelReceiveMessage msg) {
        String taskId = task.getTaskId();
        try {
            // 更新状态为运行中
            updateTaskStatus(task, JobFetchTaskStatus.RUNNING, null);

            log.info("开始执行URL抓取任务: {}", taskId);

            // 执行抓取
            List<JobInfo> jobs = jobCrawler.crawl(userConversationInfo, url, msg.getMessage());

            // 更新任务成功
            int jobCount = jobs != null ? jobs.size() : 0;
            updateTaskSuccess(task, jobCount);

            log.info("URL抓取任务完成: taskId={}, jobCount={}", taskId, jobCount);

            // TODO: 推送结果给用户(SSE或WebSocket)
            pushTaskResult(task, jobs);

        } catch (Exception e) {
            log.error("URL抓取任务失败: taskId={}", taskId, e);
            updateTaskFailed(task, e.getMessage());
        }
    }

    /**
     * 异步执行文本/文件提取任务
     */
    @Async
    public void executeTextOrFileTaskAsync(JobFetchTaskEntity task,
                                           LlmCaller.UserConversationInfo userConversationInfo,
                                           String text,
                                           String path,
                                           ChannelReceiveMessage msg) {
        String taskId = task.getTaskId();
        try {
            // 更新状态为运行中
            updateTaskStatus(task, JobFetchTaskStatus.RUNNING, null);

            log.info("开始执行文本/文件提取任务: {}", taskId);

            // 选择合适的提取器
            JobExtractor extractor = selectExtractor(msg);
            if (extractor == null) {
                throw new IllegalStateException("未找到合适的提取器");
            }

            // 执行提取
            List<JobInfo> jobs = extractor.extractFromInput(userConversationInfo, msg);

            // 更新任务成功
            int jobCount = jobs != null ? jobs.size() : 0;
            updateTaskSuccess(task, jobCount);

            log.info("文本/文件提取任务完成: taskId={}, jobCount={}", taskId, jobCount);

            // TODO: 推送结果给用户(SSE或WebSocket)
            pushTaskResult(task, jobs);

        } catch (Exception e) {
            log.error("文本/文件提取任务失败: taskId={}", taskId, e);
            updateTaskFailed(task, e.getMessage());
        }
    }

    /**
     * 选择提取器
     */
    private JobExtractor selectExtractor(ChannelReceiveMessage msg) {
        if (!CollectionUtils.isEmpty(msg.getFiles())) {
            var file = msg.getFiles().get(0);
            for (JobExtractor extractor : jobExtractorList) {
                if (extractor.supports(file.getMimeType())) {
                    return extractor;
                }
            }
        } else if (!CollectionUtils.isEmpty(msg.getMedias())) {
            var media = msg.getMedias().get(0);
            for (JobExtractor extractor : jobExtractorList) {
                if (extractor.supports(media.getMimeType())) {
                    return extractor;
                }
            }
        }

        // 默认使用文本提取器
        for (JobExtractor extractor : jobExtractorList) {
            if (extractor instanceof TextJobExtractor) {
                return extractor;
            }
        }
        return null;
    }

    /**
     * 更新任务状态
     */
    private void updateTaskStatus(JobFetchTaskEntity task, JobFetchTaskStatus status, String errorMessage) {
        task.setStatus(status.name());
        task.setErrorMessage(errorMessage);
        if (status == JobFetchTaskStatus.RUNNING) {
            task.setStartTime(LocalDateTime.now());
        }
        taskRepository.save(task);
    }

    /**
     * 更新任务成功
     */
    private void updateTaskSuccess(JobFetchTaskEntity task, int jobCount) {
        task.setStatus(JobFetchTaskStatus.SUCCESS.name());
        task.setJobCount(jobCount);
        task.setFinishTime(LocalDateTime.now());
        taskRepository.save(task);
    }

    /**
     * 更新任务失败
     */
    private void updateTaskFailed(JobFetchTaskEntity task, String errorMessage) {
        task.setStatus(JobFetchTaskStatus.FAILED.name());
        task.setErrorMessage(errorMessage);
        task.setFinishTime(LocalDateTime.now());
        taskRepository.save(task);
    }

    /**
     * 构建任务响应
     */
    private JobFetchTaskResponse buildTaskResponse(JobFetchTaskEntity task) {
        return JobFetchTaskResponse.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus())
                .jobCount(task.getJobCount())
                .errorMessage(task.getErrorMessage())
                .createTime(task.getCreateTime())
                .finishTime(task.getFinishTime())
                .build();
    }

    /**
     * 生成任务ID
     * 基于时间戳转换为36进制(a-z0-9)字符串
     */
    private String generateTaskId() {
        long timestamp = System.currentTimeMillis() / 1000;
        return "job_" + encodeBase36(timestamp);
    }

    /**
     * 将长整数转换为36进制字符串(a-z0-9)
     *
     * @param number 长整数
     * @return 36进制字符串
     */
    private String encodeBase36(long number) {
        if (number == 0) {
            return "0";
        }

        StringBuilder sb = new StringBuilder();
        String chars = "0123456789abcdefghijklmnopqrstuvwxyz";
        boolean negative = number < 0;

        if (negative) {
            number = -number;
        }

        while (number > 0) {
            int remainder = (int) (number % 36);
            sb.append(chars.charAt(remainder));
            number /= 36;
        }

        if (negative) {
            sb.append('-');
        }

        return sb.reverse().toString();
    }

    /**
     * 推送任务结果
     * TODO: 实现SSE或WebSocket推送
     */
    private void pushTaskResult(JobFetchTaskEntity task, List<JobInfo> jobs) {
        // 暂时只记录日志,后续实现SSE推送
        log.info("任务结果就绪,待推送: taskId={}, jobCount={}", task.getTaskId(),
                jobs != null ? jobs.size() : 0);

        // 可以将结果序列化后存储,供前端查询
        if (jobs != null && !jobs.isEmpty()) {
            String resultJson = JsonUtil.toStr(jobs);
            log.debug("任务结果JSON长度: {}", resultJson.length());

            channelEventPublisher.publishProactiveMessage("JOB_TASK_" + task.getTaskId(),
                    task.getJobClawUserId(),
                    task.getChannel(),
                    resultJson
            );
        }
    }
}
