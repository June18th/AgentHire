package com.git.hui.jobclaw.gather.service;

import com.git.hui.jobclaw.agents.jobfetch.service.JobFetchGatherRegistrar;
import com.git.hui.jobclaw.constants.gather.GatherTargetTypeEnum;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.gather.dao.entity.GatherTaskEntity;
import com.git.hui.jobclaw.gather.model.GatherTaskResultBo;
import com.git.hui.jobclaw.gather.model.GatherTaskSaveBo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 将 IM JobFetch 任务注册到 gather_source / gather_task，便于后台统一回看采集链路。
 */
@Slf4j
@Service
public class JobFetchGatherBridgeService implements JobFetchGatherRegistrar {

    private final GatherTaskService gatherTaskService;

    @Autowired
    public JobFetchGatherBridgeService(GatherTaskService gatherTaskService) {
        this.gatherTaskService = gatherTaskService;
    }

    @Override
    public GatherLink register(UserConversationInfo userConversationInfo, String taskType, String content) {
        GatherTargetTypeEnum type = mapTaskType(taskType, content);
        String normalizedContent = StringUtils.defaultString(content);
        GatherTaskSaveBo saveBo = new GatherTaskSaveBo(type, resolveModel(userConversationInfo), normalizedContent, null);
        try {
            GatherTaskEntity task = gatherTaskService.directAddTask(
                    saveBo,
                    GatherSourceService.OWNER_IM,
                    GatherSourceService.RUNNER_IM_FETCH
            );
            return new GatherLink(task.getId(), task.getSourceId());
        } catch (Exception ex) {
            log.warn("注册 IM 采集任务到 gather 失败: userId={}, taskType={}",
                    userConversationInfo == null ? null : userConversationInfo.jobClawUserId(), taskType, ex);
            return null;
        }
    }

    @Override
    public void markRunning(Long gatherTaskId) {
        if (gatherTaskId == null) {
            return;
        }
        runSafely(gatherTaskId, "mark running", () -> gatherTaskService.markExternalTaskProcessing(gatherTaskId));
    }

    @Override
    public void markSuccess(Long gatherTaskId, List<Long> insertDraftIds, List<Long> updateDraftIds) {
        if (gatherTaskId == null) {
            return;
        }
        runSafely(gatherTaskId, "mark success", () -> gatherTaskService.saveTaskResult(
                gatherTaskId,
                new GatherTaskResultBo(
                        GatherTaskResultBo.SUCCESS,
                        safeIds(insertDraftIds),
                        safeIds(updateDraftIds),
                        List.of(),
                        List.of(),
                        List.of()
                )
        ));
    }

    private List<Long> safeIds(List<Long> ids) {
        return ids == null ? List.of() : ids;
    }

    @Override
    public void markFailed(Long gatherTaskId, String message) {
        if (gatherTaskId == null) {
            return;
        }
        runSafely(gatherTaskId, "mark failed", () -> gatherTaskService.saveTaskResult(
                gatherTaskId,
                new GatherTaskResultBo(
                        StringUtils.defaultIfBlank(message, "failed"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )
        ));
    }

    // AIDEV-NOTE: 台账故障不得影响主抓取任务
    private void runSafely(Long gatherTaskId, String action, Runnable operation) {
        try {
            operation.run();
        } catch (Exception ex) {
            log.warn("Failed to {} for gather task: gatherTaskId={}", action, gatherTaskId, ex);
        }
    }

    private String resolveModel(UserConversationInfo userConversationInfo) {
        return userConversationInfo == null ? "" : StringUtils.defaultString(userConversationInfo.jobClawUserId());
    }

    private GatherTargetTypeEnum mapTaskType(String taskType, String content) {
        if ("URL".equalsIgnoreCase(taskType)) {
            return GatherTargetTypeEnum.HTTP_URL;
        }
        if ("FILE".equalsIgnoreCase(taskType)) {
            String lower = StringUtils.defaultString(content).toLowerCase();
            if (lower.endsWith(".csv")) {
                return GatherTargetTypeEnum.CSV_FILE;
            }
            if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) {
                return GatherTargetTypeEnum.EXCEL_FILE;
            }
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")) {
                return GatherTargetTypeEnum.IMAGE;
            }
            return GatherTargetTypeEnum.EXCEL_FILE;
        }
        if (looksLikeHtml(content)) {
            return GatherTargetTypeEnum.HTML_TEXT;
        }
        return GatherTargetTypeEnum.TEXT;
    }

    private boolean looksLikeHtml(String content) {
        if (StringUtils.isBlank(content)) {
            return false;
        }
        String trimmed = content.trim().toLowerCase();
        return trimmed.startsWith("<!doctype html") || trimmed.startsWith("<html") || trimmed.contains("<body");
    }
}
