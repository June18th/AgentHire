package com.git.hui.jobclaw.agents.jobfetch.service;

import com.git.hui.jobclaw.agents.jobfetch.crawler.JobCrawler;
import com.git.hui.jobclaw.agents.jobfetch.extract.JobExtractor;
import com.git.hui.jobclaw.agents.jobfetch.extract.impl.TextJobExtractor;
import com.git.hui.jobclaw.agents.jobfetch.search.JobSearchCandidate;
import com.git.hui.jobclaw.agents.jobfetch.search.JobSearchProperties;
import com.git.hui.jobclaw.agents.jobfetch.search.JobSearchProvider;
import com.git.hui.jobclaw.agents.jobfetch.service.model.FetchedJobInfo;
import com.git.hui.jobclaw.agents.jobfetch.service.model.JobFetchTaskEntity;
import com.git.hui.jobclaw.agents.jobfetch.service.model.JobFetchTaskResponse;
import com.git.hui.jobclaw.agents.jobfetch.service.model.JobFetchTaskStatus;
import com.git.hui.jobclaw.agents.jobfetch.service.repository.JobFetchTaskRepository;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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

    @Autowired
    private JobInfoPersistService jobInfoSaveService;

    @Autowired
    private JobScheduler jobScheduler;

    @Autowired
    private List<JobSearchProvider> jobSearchProviders;

    @Autowired
    private JobSearchProperties jobSearchProperties;

    @Autowired(required = false)
    private JobFetchGatherRegistrar jobFetchGatherRegistrar;

    /**
     * 创建URL抓取任务
     */
    public JobFetchTaskResponse createUrlTask(UserConversationInfo userConversationInfo,
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
        linkGatherTask(task, userConversationInfo);
        log.info("创建URL抓取任务: taskId={}, url={}", taskId, url);

        // 异步执行任务
        enqueue(task);
        return buildTaskResponse(task);
    }

    /**
     * 创建文本/文件提取任务
     */
    public JobFetchTaskResponse createTextOrFileTask(UserConversationInfo userConversationInfo,
                                                     String text,
                                                     String path,
                                                     ChannelReceiveMessage msg) {
        String taskId = generateTaskId();
        boolean hasFiles = !CollectionUtils.isEmpty(msg.getFiles());
        boolean hasMedias = !CollectionUtils.isEmpty(msg.getMedias());
        String taskType = hasFiles ? "FILE" : hasMedias ? "MEDIA" : "TEXT";

        // 创建任务记录
        JobFetchTaskEntity task = new JobFetchTaskEntity()
                .setTaskId(taskId)
                .setJobClawUserId(userConversationInfo.jobClawUserId())
                .setChannel(userConversationInfo.channel())
                .setConversionId(userConversationInfo.conversationId())
                .setTaskType(taskType)
                .setInputContent(hasFiles || hasMedias ? path : text)
                .setOriginMessage(msg.getMessage())
                .setStatus(JobFetchTaskStatus.PENDING.name())
                .setJobCount(0);

        taskRepository.save(task);
        linkGatherTask(task, userConversationInfo);
        log.info("创建文本/文件提取任务: taskId={}, type={}", taskId, task.getTaskType());

        // 异步执行任务
        enqueue(task);
        return buildTaskResponse(task);
    }

    /**
     * 创建搜索发现任务。搜索只发现候选页面，职位仍需抓取解析后进入草稿库。
     */
    public JobFetchTaskResponse createSearchTask(UserConversationInfo userConversationInfo,
                                                 String query,
                                                 ChannelReceiveMessage msg) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("岗位搜索条件不能为空");
        }
        String taskId = generateTaskId();
        JobFetchTaskEntity task = new JobFetchTaskEntity()
                .setTaskId(taskId)
                .setJobClawUserId(userConversationInfo.jobClawUserId())
                .setChannel(userConversationInfo.channel())
                .setConversionId(userConversationInfo.conversationId())
                .setTaskType("SEARCH")
                .setInputContent(query.trim())
                .setOriginMessage(msg.getMessage())
                .setStatus(JobFetchTaskStatus.PENDING.name())
                .setJobCount(0);

        taskRepository.save(task);
        linkGatherTask(task, userConversationInfo);
        log.info("创建岗位搜索任务: taskId={}, queryLength={}", taskId, query.trim().length());
        enqueue(task);
        return buildTaskResponse(task);
    }

    /**
     * 查询任务状态
     */
    private void enqueue(JobFetchTaskEntity task) {
        try {
            jobScheduler.<JobFetchTaskJobHandler>enqueue(handler -> handler.execute(task.getTaskId()));
        } catch (RuntimeException e) {
            updateTaskFailed(task, "Failed to enqueue durable task: " + e.getMessage());
            throw e;
        }
    }

    /** AIDEV-NOTE: JobRunr reloads execution input by taskId. */
    public void executePersistedTask(String taskId) {
        JobFetchTaskEntity task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task does not exist: " + taskId));
        if (JobFetchTaskStatus.SUCCESS.name().equals(task.getStatus())) {
            log.info("Skip completed job fetch task: {}", taskId);
            return;
        }

        UserConversationInfo conversationInfo = new UserConversationInfo(
                task.getJobClawUserId(), task.getChannel(), task.getConversionId(), false);
        ChannelReceiveMessage message = restoreMessage(task);
        switch (task.getTaskType()) {
            case "URL" -> executeUrlTaskAsync(task, conversationInfo, task.getInputContent(), message);
            case "SEARCH" -> executeSearchTaskAsync(task, conversationInfo, task.getInputContent(), message);
            case "TEXT", "FILE", "MEDIA" -> executeTextOrFileTaskAsync(task, conversationInfo,
                    "TEXT".equals(task.getTaskType()) ? task.getInputContent() : null,
                    !"TEXT".equals(task.getTaskType()) ? task.getInputContent() : null,
                    message);
            default -> throw new IllegalStateException("不支持的职位抓取任务类型: " + task.getTaskType());
        }
    }

    private ChannelReceiveMessage restoreMessage(JobFetchTaskEntity task) {
        ChannelReceiveMessage message = ChannelReceiveMessage.builder()
                .msgId("JOB_FETCH_" + task.getTaskId())
                .jobClawUserId(task.getJobClawUserId())
                .fromUserId(task.getConversionId())
                .channel(task.getChannel())
                .message("TEXT".equals(task.getTaskType()) ? task.getInputContent() : task.getOriginMessage())
                .build();
        if (("FILE".equals(task.getTaskType()) || "MEDIA".equals(task.getTaskType()))
                && task.getInputContent() != null) {
            Path filePath = Path.of(task.getInputContent());
            String mimeType = null;
            try {
                mimeType = Files.probeContentType(filePath);
            } catch (Exception e) {
                log.debug("Unable to detect file type for {}", filePath, e);
            }
            if ("MEDIA".equals(task.getTaskType())) {
                message.setMedias(List.of(ChannelReceiveMessage.MediaMsg.builder()
                        .filePath(filePath)
                        .fileType(extension(filePath))
                        .mimeType(mimeType)
                        .build()));
            } else {
                message.setFiles(List.of(ChannelReceiveMessage.FileMsg.builder()
                        .filePath(filePath)
                        .fileName(filePath.getFileName().toString())
                        .fileType(extension(filePath))
                        .mimeType(mimeType)
                        .build()));
            }
        }
        return message;
    }

    private String extension(Path path) {
        String name = path.getFileName().toString();
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1);
    }

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
    public void executeUrlTaskAsync(JobFetchTaskEntity task,
                                    UserConversationInfo userConversationInfo,
                                    String url,
                                    ChannelReceiveMessage msg) {
        String taskId = task.getTaskId();
        try {
            // 更新状态为运行中
            updateTaskStatus(task, JobFetchTaskStatus.RUNNING, null);

            log.info("开始执行URL抓取任务: {}", taskId);

            // 执行抓取
            List<FetchedJobInfo> jobs = jobCrawler.crawl(userConversationInfo, url, msg.getMessage());

            // 更新任务成功
            int jobCount = jobs != null ? jobs.size() : 0;
            updateTaskSuccess(task, jobCount);

            log.info("URL抓取任务完成: taskId={}, jobCount={}", taskId, jobCount);

            pushTaskResult(task, jobs);

        } catch (Exception e) {
            log.error("URL抓取任务失败: taskId={}", taskId, e);
            updateTaskFailed(task, e.getMessage());
            pushTaskFailure(task, e.getMessage());
            throw new IllegalStateException("Job fetch task failed: " + taskId, e);
        }
    }

    /**
     * AIDEV-NOTE: 搜索仅发现公网候选页，逐页抓取后统一写入草稿
     */
    public void executeSearchTaskAsync(JobFetchTaskEntity task,
                                       UserConversationInfo userConversationInfo,
                                       String query,
                                       ChannelReceiveMessage msg) {
        String taskId = task.getTaskId();
        try {
            updateTaskStatus(task, JobFetchTaskStatus.RUNNING, null);
            JobSearchProvider searchProvider = selectSearchProvider();
            int maxResults = Math.max(1, jobSearchProperties.getMaxResults());
            List<JobSearchCandidate> candidates = searchProvider.search(query, maxResults);
            if (candidates.isEmpty()) {
                updateTaskSuccess(task, 0);
                pushTaskResult(task, List.of());
                return;
            }

            int maxPages = Math.max(1, Math.min(jobSearchProperties.getMaxPages(), candidates.size()));
            List<FetchedJobInfo> fetchedJobs = new ArrayList<>();
            int attemptedPages = 0;
            int failedPages = 0;
            for (JobSearchCandidate candidate : candidates) {
                if (attemptedPages >= maxPages) {
                    break;
                }
                if (!isJobRelated(candidate)) {
                    log.info("跳过招聘相关性不足的搜索结果: {}", candidate.url());
                    continue;
                }
                attemptedPages++;
                try {
                    List<FetchedJobInfo> pageJobs = jobCrawler.crawl(
                            userConversationInfo, candidate.url(), buildSearchOriginMessage(msg, candidate));
                    if (pageJobs == null || pageJobs.isEmpty()) {
                        failedPages++;
                        continue;
                    }
                    pageJobs.stream()
                            .filter(job -> job != null && job.isValid())
                            .peek(job -> enrichSearchSource(job, candidate))
                            .forEach(fetchedJobs::add);
                } catch (RuntimeException e) {
                    failedPages++;
                    log.warn("搜索候选页抓取失败: taskId={}, url={}, reason={}",
                            taskId, candidate.url(), e.getMessage());
                }
            }

            List<FetchedJobInfo> jobs = deduplicateSearchJobs(fetchedJobs);
            if (attemptedPages > 0 && jobs.isEmpty()) {
                throw new IllegalStateException(String.format(
                        "搜索返回 %d 个候选页面，但尝试抓取的 %d 个页面均未解析出职位",
                        candidates.size(), attemptedPages));
            }
            if (failedPages > 0) {
                task.setErrorMessage(String.format("部分页面未解析成功: %d/%d", failedPages, attemptedPages));
            }
            updateTaskSuccess(task, jobs.size());
            log.info("岗位搜索任务完成: taskId={}, candidates={}, attempted={}, failed={}, jobs={}",
                    taskId, candidates.size(), attemptedPages, failedPages, jobs.size());
            pushTaskResult(task, jobs);
        } catch (Exception e) {
            log.error("岗位搜索任务失败: taskId={}", taskId, e);
            updateTaskFailed(task, e.getMessage());
            pushTaskFailure(task, e.getMessage());
            throw new IllegalStateException("Job search task failed: " + taskId, e);
        }
    }

    private JobSearchProvider selectSearchProvider() {
        if (!jobSearchProperties.isEnabled()) {
            throw new IllegalStateException("岗位联网搜索未启用");
        }
        return jobSearchProviders.stream()
                .filter(provider -> provider.provider().equalsIgnoreCase(jobSearchProperties.getProvider()))
                .filter(JobSearchProvider::isAvailable)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "岗位搜索供应商不可用: " + jobSearchProperties.getProvider()));
    }

    private String buildSearchOriginMessage(ChannelReceiveMessage msg, JobSearchCandidate candidate) {
        String origin = msg == null || msg.getMessage() == null ? "" : msg.getMessage();
        return origin + "\n搜索结果标题: " + nullToEmpty(candidate.title())
                + "\n搜索摘要: " + nullToEmpty(candidate.snippet());
    }

    private boolean isJobRelated(JobSearchCandidate candidate) {
        String text = (nullToEmpty(candidate.title()) + " "
                + nullToEmpty(candidate.snippet()) + " "
                + nullToEmpty(candidate.url())).toLowerCase(Locale.ROOT);
        return List.of("招聘", "职位", "岗位", "校招", "实习", "应届", "求职",
                        "career", "careers", "job", "jobs", "hiring", "join-us", "joinus")
                .stream().anyMatch(text::contains);
    }

    private void enrichSearchSource(FetchedJobInfo job, JobSearchCandidate candidate) {
        if (job.getRelatedLink() == null || job.getRelatedLink().isBlank()) {
            job.setRelatedLink(candidate.url());
        }
        if (job.getSource() == null || job.getSource().isBlank()) {
            String source = candidate.source() == null || candidate.source().isBlank()
                    ? candidate.url()
                    : candidate.source() + " - " + candidate.url();
            job.setSource(source);
        }
        if ((job.getLastUpdatedTime() == null || job.getLastUpdatedTime().isBlank())
                && candidate.publishDate() != null && !candidate.publishDate().isBlank()) {
            job.setLastUpdatedTime(candidate.publishDate());
        }
    }

    private List<FetchedJobInfo> deduplicateSearchJobs(List<FetchedJobInfo> jobs) {
        Map<String, FetchedJobInfo> unique = new LinkedHashMap<>();
        for (FetchedJobInfo job : jobs) {
            String key = normalizeKey(job.getRelatedLink()) + "|"
                    + normalizeKey(job.getCompanyName()) + "|"
                    + normalizeKey(job.getPosition());
            unique.putIfAbsent(key, job);
        }
        return List.copyOf(unique.values());
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 异步执行文本/文件提取任务
     */
    public void executeTextOrFileTaskAsync(JobFetchTaskEntity task,
                                           UserConversationInfo userConversationInfo,
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
            List<FetchedJobInfo> jobs = extractor.extractFromInput(userConversationInfo, msg);

            // 更新任务成功
            int jobCount = jobs != null ? jobs.size() : 0;
            updateTaskSuccess(task, jobCount);

            log.info("文本/文件提取任务完成: taskId={}, jobCount={}", taskId, jobCount);

            pushTaskResult(task, jobs);

        } catch (Exception e) {
            log.error("文本/文件提取任务失败: taskId={}", taskId, e);
            updateTaskFailed(task, e.getMessage());
            pushTaskFailure(task, e.getMessage());
            throw new IllegalStateException("Job fetch task failed: " + taskId, e);
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
            syncGatherRunning(task);
        }
        taskRepository.save(task);
    }

    private void syncGatherRunning(JobFetchTaskEntity task) {
        if (jobFetchGatherRegistrar == null || task == null || task.getGatherTaskId() == null) {
            return;
        }
        jobFetchGatherRegistrar.markRunning(task.getGatherTaskId());
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
        syncGatherFailure(task, errorMessage);
    }

    private void linkGatherTask(JobFetchTaskEntity task, UserConversationInfo userConversationInfo) {
        if (jobFetchGatherRegistrar == null || task == null) {
            return;
        }
        JobFetchGatherRegistrar.GatherLink link = jobFetchGatherRegistrar.register(
                userConversationInfo,
                task.getTaskType(),
                task.getInputContent()
        );
        if (link == null) {
            return;
        }
        task.setGatherTaskId(link.gatherTaskId());
        task.setGatherSourceId(link.gatherSourceId());
        taskRepository.save(task);
    }

    private void syncGatherSuccess(JobFetchTaskEntity task, JobInfoPersistService.SaveRes res) {
        if (jobFetchGatherRegistrar == null || task == null || task.getGatherTaskId() == null || res == null) {
            return;
        }
        jobFetchGatherRegistrar.markSuccess(task.getGatherTaskId(), res.insertDraftIds(), res.updateDraftIds());
    }

    private void syncGatherFailure(JobFetchTaskEntity task, String errorMessage) {
        if (jobFetchGatherRegistrar == null || task == null || task.getGatherTaskId() == null) {
            return;
        }
        jobFetchGatherRegistrar.markFailed(task.getGatherTaskId(), errorMessage);
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
     * 使用随机 UUID，避免并发创建时发生唯一键冲突。
     */
    static String generateTaskId() {
        return "job_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 推送任务结果
     */
    private void pushTaskResult(JobFetchTaskEntity task, List<FetchedJobInfo> jobs) {
        log.info("任务结果就绪,待推送: taskId={}, jobCount={}", task.getTaskId(),
                jobs != null ? jobs.size() : 0);
    
        // 保存职位信息并获取统计结果
        if (jobs != null && !jobs.isEmpty()) {
            JobInfoPersistService.SaveRes res = jobInfoSaveService.save(
                    jobs,
                    task.getGatherSourceId(),
                    task.getGatherTaskId()
            );
            syncGatherSuccess(task, res);
                
            // 构建友好的提示消息
            String resultMessage = buildTaskCompletionMessage(task, res);
                
            log.info("推送任务完成通知: taskId={}, message={}", task.getTaskId(), resultMessage);
                
            channelEventPublisher.publishProactiveMessage("JOB_TASK_" + task.getTaskId(),
                    task.getJobClawUserId(),
                    task.getChannel(),
                    resultMessage
            );
        } else {
            syncGatherSuccess(task, new JobInfoPersistService.SaveRes(0, 0));
            // 没有提取到职位信息
            String emptyMessage = buildEmptyResultMessage(task);
            channelEventPublisher.publishProactiveMessage("JOB_TASK_" + task.getTaskId(),
                    task.getJobClawUserId(),
                    task.getChannel(),
                    emptyMessage
            );
        }
    }
    
    /**
     * 构建任务完成的友好提示消息
     */
    private String buildTaskCompletionMessage(JobFetchTaskEntity task, JobInfoPersistService.SaveRes res) {
        StringBuilder sb = new StringBuilder();
            
        sb.append("✅ 任务执行完成\n\n");
        sb.append(String.format("📋 任务ID: `%s`\n\n", task.getTaskId()));
        sb.append(String.format("📊 提取职位数: %d 个\n\n", res.insertCnt() + res.updateCnt()));
            
        // 详细统计
        sb.append("📈 数据处理结果:\n\n");
        sb.append(String.format("  • 新增: %d 条\n\n", res.insertCnt()));
        sb.append(String.format("  • 更新: %d 条\n\n", res.updateCnt()));
        if (task.getErrorMessage() != null && !task.getErrorMessage().isBlank()) {
            sb.append(String.format("  • 提醒: %s\n\n", task.getErrorMessage()));
        }
            
        sb.append("\n💡 提示:\n\n");
        sb.append("• 职位信息已保存到数据库\n\n");
        sb.append("• 您可以在管理后台查看和管理这些职位\n\n");
        sb.append("• 如需继续抓取，请发送新的URL或文件\n\n");
            
        return sb.toString();
    }
    
    /**
     * 构建空结果的提示消息
     */
    private String buildEmptyResultMessage(JobFetchTaskEntity task) {
        StringBuilder sb = new StringBuilder();
            
        sb.append("⚠️ 任务执行完成，但未提取到职位信息\n\n");
        sb.append(String.format("📋 任务ID: `%s`\n\n", task.getTaskId()));
        sb.append("可能的原因:\n\n");
        sb.append("• 网页中没有找到符合格式的职位信息\n\n");
        sb.append("• 文本内容中不包含有效的招聘数据\n\n");
        sb.append("• 文件格式不支持或内容为空\n\n");
        sb.append("💡 建议:\n\n");
        sb.append("• 检查URL是否正确指向招聘页面\n\n");
        sb.append("• 确认文本/文件中包含完整的招聘信息\n\n");
        sb.append("• 尝试其他来源或格式\n\n");
            
        return sb.toString();
    }

    private void pushTaskFailure(JobFetchTaskEntity task, String errorMessage) {
        String message = buildFailureMessage(task, errorMessage);
        channelEventPublisher.publishProactiveMessage(
                "JOB_TASK_FAIL_" + task.getTaskId(),
                task.getJobClawUserId(),
                task.getChannel(),
                message
        );
    }

    private String buildFailureMessage(JobFetchTaskEntity task, String errorMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("❌ 任务执行失败\n\n");
        sb.append(String.format("📋 任务ID: `%s`\n\n", task.getTaskId()));
        sb.append(String.format("原因: %s\n\n", errorMessage == null || errorMessage.isBlank() ? "未知错误" : errorMessage));
        sb.append("💡 建议:\n\n");
        sb.append("• 检查链接或文件内容是否有效\n\n");
        sb.append("• 稍后重试，或更换输入来源\n\n");
        return sb.toString();
    }
}
