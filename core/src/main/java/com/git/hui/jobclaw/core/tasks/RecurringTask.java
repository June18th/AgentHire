package com.git.hui.jobclaw.core.tasks;

import lombok.Getter;

@Getter
public class RecurringTask {

    private final String id;
    private final String name;
    private final String description;
    private final String jobClawUserId;

    public RecurringTask(String id, String name, String description, String jobClawUserId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.jobClawUserId = jobClawUserId;
    }

    public static RecurringTask newRecurringTask(String name, String description, String jobClawUserId) {
        if (jobClawUserId == null || jobClawUserId.isEmpty()) {
            throw new IllegalArgumentException("jobClawUserId is required");
        }
        return new RecurringTask(null, name, description, jobClawUserId);
    }

    @Override
    public String toString() {
        return "Recurring Task '" + name + "'";
    }
}
