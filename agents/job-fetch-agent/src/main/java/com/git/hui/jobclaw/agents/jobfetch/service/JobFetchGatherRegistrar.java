package com.git.hui.jobclaw.agents.jobfetch.service;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;

import java.util.List;

/**
 * IM 岗位抓取任务与 gather_task / gather_source 的注册桥接。
 * 由 backend 模块提供实现，避免 job-fetch-agent 依赖 gather 域。
 */
public interface JobFetchGatherRegistrar {

    record GatherLink(Long gatherTaskId, Long gatherSourceId) {
    }

    GatherLink register(UserConversationInfo userConversationInfo, String taskType, String content);

    void markRunning(Long gatherTaskId);

    void markSuccess(Long gatherTaskId, List<Long> insertDraftIds, List<Long> updateDraftIds);

    void markFailed(Long gatherTaskId, String message);
}
