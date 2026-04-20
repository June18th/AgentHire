package com.git.hui.jobclaw.agents.jobfetch.controller;

import com.git.hui.jobclaw.agents.jobfetch.task.model.JobFetchTaskResponse;
import com.git.hui.jobclaw.agents.jobfetch.task.service.JobFetchTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 职位抓取任务Controller
 *
 * @author YiHui
 * @date 2026/4/20
 */
@Slf4j
@RestController
@RequestMapping("/api/job-fetch/task")
public class JobFetchTaskController {

    @Autowired
    private JobFetchTaskService taskService;

    /**
     * 查询任务状态
     *
     * @param jobClawUserId 用户ID
     * @param taskId        任务ID
     * @return 任务状态信息
     */
    @GetMapping("/{taskId}")
    public JobFetchTaskResponse queryTask(@RequestHeader("X-JobClaw-User-Id") String jobClawUserId,
                                           @PathVariable String taskId) {
        log.info("查询任务状态: userId={}, taskId={}", jobClawUserId, taskId);
        return taskService.queryTask(jobClawUserId, taskId);
    }
}
