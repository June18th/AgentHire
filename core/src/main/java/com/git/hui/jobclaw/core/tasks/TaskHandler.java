package com.git.hui.jobclaw.core.tasks;

import com.git.hui.jobclaw.core.agent.Agent;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TaskHandler {

    private static final Logger LOGGER = new JobRunrDashboardLogger(LoggerFactory.getLogger(TaskHandler.class));

    private final Agent agent;
    private final TaskRepository taskRepository;
    private final ChannelRegistry channelRegistry;

    private final AiUserPreferenceProperties aiUserPreferenceProperties;

    private final ChannelEventPublisher channelEventPublisher;

    public TaskHandler(Agent agent, TaskRepository taskRepository, ChannelRegistry channelRegistry, AiUserPreferenceProperties aiModelProperties, ChannelEventPublisher channelEventPublisher) {
        this.agent = agent;
        this.taskRepository = taskRepository;
        this.channelRegistry = channelRegistry;
        this.aiUserPreferenceProperties = aiModelProperties;
        this.channelEventPublisher = channelEventPublisher;
    }

    @Job(name = "%0", retries = 3)
    public void executeTask(String taskId) {
        Task task = taskRepository.getTaskById(taskId);

        if (!Task.Status.todo.equals(task.getStatus())) {
            throw new IllegalStateException("Cannot handle task '" + task.getName() + "' with status " + task.getStatus() + ". Only tasks that have status todo can be run");
        }

        Task inProgress = taskRepository.save(task.withStatus(Task.Status.in_progress));
        try {
            LOGGER.info("Starting task: {}", task);
            String agentInput = formatTaskForAgent(inProgress);
            TaskResult result = agent.prompt("2", taskId, agentInput, TaskResult.class);
            taskRepository.save(inProgress.withFeedback(result.feedback()).withStatus(result.newStatus()));
            notifyUser(task, result);
            LOGGER.info("Finished task: {} with status {}", task.getName(), result.newStatus());
        } catch (Exception e) {
            taskRepository.save(inProgress.withStatus(Task.Status.todo));
            throw e;
        }
    }

    private void notifyUser(Task task, TaskResult result) {
        try {
            String userId = task.getJobClawUserId();
            for (var pre : aiUserPreferenceProperties.getPreference()) {
                if (pre.getUserId().equals(userId)) {
                    for (var channel : pre.getChannels()) {
                        var pro = channelRegistry.getBackendReceivedChannelAdapter(userId, channel);
                        if (pro != null) {
                            // fixme 主动给用户发送消息，但是这里没有考虑通道具体的回调状态，如果通道发送失败了，是否需要重试、或者路由到下一个通道? 这些重试&自动路由的逻辑，后续可以统一在消息消费端进行处理
                            var response = pro.apply(result.feedback());
                            channelEventPublisher.publishProactiveMessage("TRSP_" + task.getId(), channel, response, 0);
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to notify user about task '{}': {}", task.getName(), e.getMessage());
        }
    }

    private String formatTaskForAgent(Task task) {
        return String.format("""
                Handle the following task and report the new status ('completed' or 'awaiting_human_input') with the feedback what was done
                Task '%s': %s
                """, task.getName(), task.getDescription());
    }

    public record TaskResult(Task.Status newStatus, String feedback) {
    }
}
