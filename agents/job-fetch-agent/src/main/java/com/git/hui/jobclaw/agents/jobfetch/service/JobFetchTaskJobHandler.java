package com.git.hui.jobclaw.agents.jobfetch.service;

import org.springframework.stereotype.Component;

/** Durable JobRunr entry point for job-fetch tasks. */
@Component
public class JobFetchTaskJobHandler {
    private final JobFetchTaskService taskService;

    public JobFetchTaskJobHandler(JobFetchTaskService taskService) {
        this.taskService = taskService;
    }

    public void execute(String taskId) {
        taskService.executePersistedTask(taskId);
    }
}
