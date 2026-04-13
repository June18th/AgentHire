package com.git.hui.jobclaw.core.tools;

import com.git.hui.jobclaw.core.tasks.RecurringTask;
import com.git.hui.jobclaw.core.tasks.TaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.Optional.ofNullable;

/**
 * Creates and manages high-level tasks for AI assistants.
 * Each task is persisted as a Markdown file in the workspace directory.
 */
public class TaskTool {

    private static final Logger logger = LoggerFactory.getLogger(TaskTool.class);
    private final TaskManager taskManager;
    private final TaskEventHandler taskEventHandler;


    public TaskTool(TaskManager taskManager, TaskEventHandler taskEventHandler) {
        this.taskManager = taskManager;
        this.taskEventHandler = taskEventHandler;
    }

    public interface TaskEventHandler {

        void taskCreated(String name, String description, String jobClawUserId);

        void taskScheduled(LocalDateTime executionTime, String name, String description, String jobClawUserId);

        void recurringTaskCreated(String cronExpression, String name, String description, String jobClawUserId);

    }

    @Tool(name = "createTask",
            description = """
                    Use this tool to manage high-level tasks that represent major units of work.
                    Tasks are persistent, trackable entities that you can work on backed by JobRunr.
                                
                    ## When to Use:
                    - When a user provides a new goal or assignment.
                    - When a user provides multiple goals (create a separate task for EACH).
                    - To formalize a request into a trackable entity before starting work.
                                
                    ## Constraints:
                    - Name: Short, descriptive identifier (e.g., 'research-market', 'update-docs'). Spaces will be converted to underscores. Must always be in English
                    - Description: Detailed explanation of what needs to be achieved.
                    """)
    public String createTask(String name, String description, ToolContext toolContext) {
        try {
            String jobClawUserId = (String) toolContext.getContext().get("jobClawUserId");
            this.taskManager.create(name, description, jobClawUserId);
            ofNullable(taskEventHandler).ifPresent(x -> x.taskCreated(name, description, jobClawUserId));
            return String.format("Task '%s' has been created successfully.", name);
        } catch (Exception e) {
            logger.error("Failed to create task", e);
            return "Error: Could not create task. " + e.getMessage();
        }
    }

    @Tool(name = "scheduleTask",
            description = """
                    Schedules a task using JobRunr for a specific date and time in the future.
                    Use this when a user explicitly mentions a time or date (e.g., "Remind me next Monday at 9 AM" or "Schedule at 3pm").
                                
                    - executionTime: The specific local date and time (without timezone) when the task should run in this format YYYY-MM-ddTHH:mm:ss (example 2025-03-17T09:00:00).
                    - name: Short, descriptive identifier (e.g., 'monday-morning-sync').
                    - description: Detailed instructions on what the task entails.
                    """)
    public String scheduleTask(String executionTime, String name, String description, ToolContext toolContext) {
        try {
            // using string to work around tool call argument parsing exception
            String jobClawUserId = (String) toolContext.getContext().get("jobClawUserId");
            LocalDateTime executionTimeAsLocalDateTime = LocalDateTime.parse(executionTime);
            this.taskManager.schedule(executionTimeAsLocalDateTime, name, description, jobClawUserId);
            ofNullable(taskEventHandler).ifPresent(x -> x.taskScheduled(executionTimeAsLocalDateTime,
                    name,
                    description,
                    jobClawUserId));
            return String.format("Task '%s' has been scheduled for %s.", name, executionTime);
        } catch (Exception e) {
            logger.error("Failed to schedule task", e);
            return "Error: Could not schedule task. " + e.getMessage();
        }
    }

    @Tool(name = "scheduleRecurringTask",
            description = """
                    Schedules a task using JobRunr that repeats at regular intervals based on a cron expression.
                    Use this for recurring activities like daily reports, weekly checks, etc.
                                
                    - cronExpression: A standard quartz-style cron expression (e.g., '0 12 * * *' for daily at noon or '* * * * *' for every minute. Do not use ? in a cron expression).
                    - name: Short, descriptive identifier (e.g., 'weekly-log-cleanup').
                    - description: Detailed instructions on what the task entails.
                    """)
    public String scheduleRecurringTask(String cronExpression, String name, String description, ToolContext toolContext) {
        try {
            String jobClawUserId = (String) toolContext.getContext().get("jobClawUserId");
            this.taskManager.scheduleRecurrently(cronExpression, name, description, jobClawUserId);
            ofNullable(taskEventHandler).ifPresent(x -> x.recurringTaskCreated(cronExpression,
                    name,
                    description,
                    jobClawUserId));
            return String.format("Task '%s' has been scheduled recurrently with cron expression '%s'.",
                    name,
                    cronExpression);
        } catch (Exception e) {
            logger.error("Failed to schedule recurring task", e);
            return "Error: Could not schedule recurring task. " + e.getMessage();
        }
    }

    @Tool(name = "deleteRecurringTask",
            description = """
                    Deletes a recurring task by name, stopping it from running again.
                    Use this when a user wants to remove, cancel, or stop a recurring task.
                                
                    - name: The name of the recurring task to delete (e.g., 'weekly-log-cleanup').
                    """)
    public String deleteRecurringTask(String name, ToolContext toolContext) {
        try {
            String jobClawUserId = (String) toolContext.getContext().get("jobClawUserId");

            this.taskManager.deleteRecurringTask(name, jobClawUserId);
            return String.format("Recurring task '%s' has been deleted successfully.", name);
        } catch (Exception e) {
            logger.error("Failed to delete recurring task", e);
            return "Error: Could not delete recurring task. " + e.getMessage();
        }
    }

    @Tool(name = "listRecurringTasks",
            description = """
                    List all recurring tasks for a specific user.
                    Use this when a user wants to list their recurring tasks.
                    """)
    public String listRecurringTasks(ToolContext toolContext) {
        String jobClawUserId = (String) toolContext.getContext().get("jobClawUserId");

        List<RecurringTask> allRecurringTasks = taskManager.getRecurringTasksByUser(jobClawUserId);

        StringBuilder sb = new StringBuilder();
        sb.append("Recurring tasks:").append(System.lineSeparator());
        allRecurringTasks.forEach(rt -> {
            sb.append("- id: ").append(rt.getId()).append(System.lineSeparator());
            sb.append("  name: ").append(rt.getName()).append(System.lineSeparator());
            sb.append("  description: ").append(rt.getDescription(),
                    0,
                    Math.min(rt.getDescription().length(), 100)).append(System.lineSeparator());
            if (rt.getJobClawUserId() != null) {
                sb.append("  userId: ").append(rt.getJobClawUserId()).append(System.lineSeparator());
            }
        });
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private TaskManager taskManager;
        private TaskEventHandler taskEventHandler;


        public Builder taskManager(TaskManager taskManager) {
            this.taskManager = taskManager;
            return this;
        }

        public Builder agentTaskEventHandler(TaskEventHandler taskEventHandler) {
            this.taskEventHandler = taskEventHandler;
            return this;
        }

        public TaskTool build() {
            return new TaskTool(this.taskManager, this.taskEventHandler);
        }

    }
}