package com.git.hui.jobclaw.constants.application;

import java.util.Map;
import java.util.Set;

public enum JobApplicationStatusEnum {
    INTERESTED("INTERESTED", "感兴趣", false),
    PREPARING("PREPARING", "准备投递", false),
    SUBMITTED("SUBMITTED", "已投递", false),
    WRITTEN_TEST("WRITTEN_TEST", "笔试", false),
    INTERVIEW_1("INTERVIEW_1", "一面", false),
    INTERVIEW_2("INTERVIEW_2", "二面", false),
    HR_INTERVIEW("HR_INTERVIEW", "HR 面", false),
    OFFER("OFFER", "Offer", false),
    ACCEPTED("ACCEPTED", "已接受", true),
    REJECTED("REJECTED", "已拒绝", true),
    GAVE_UP("GAVE_UP", "已放弃", true),
    EXPIRED("EXPIRED", "已过期", true),
    CLOSED("CLOSED", "已结束", true);

    private static final Map<JobApplicationStatusEnum, Set<JobApplicationStatusEnum>> TRANSITIONS = Map.of(
            INTERESTED, Set.of(PREPARING, SUBMITTED, GAVE_UP, EXPIRED, CLOSED),
            PREPARING, Set.of(SUBMITTED, GAVE_UP, EXPIRED, CLOSED),
            SUBMITTED, Set.of(WRITTEN_TEST, INTERVIEW_1, REJECTED, GAVE_UP, EXPIRED, CLOSED),
            WRITTEN_TEST, Set.of(INTERVIEW_1, REJECTED, GAVE_UP, EXPIRED, CLOSED),
            INTERVIEW_1, Set.of(INTERVIEW_2, HR_INTERVIEW, OFFER, REJECTED, GAVE_UP, CLOSED),
            INTERVIEW_2, Set.of(HR_INTERVIEW, OFFER, REJECTED, GAVE_UP, CLOSED),
            HR_INTERVIEW, Set.of(OFFER, REJECTED, GAVE_UP, CLOSED),
            OFFER, Set.of(ACCEPTED, REJECTED, GAVE_UP, CLOSED)
    );

    private final String code;
    private final String desc;
    private final boolean terminal;

    JobApplicationStatusEnum(String code, String desc, boolean terminal) {
        this.code = code;
        this.desc = desc;
        this.terminal = terminal;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public static JobApplicationStatusEnum of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (JobApplicationStatusEnum status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unsupported application status: " + code);
    }

    public boolean canTransitTo(JobApplicationStatusEnum target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        if (terminal) {
            return false;
        }
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
