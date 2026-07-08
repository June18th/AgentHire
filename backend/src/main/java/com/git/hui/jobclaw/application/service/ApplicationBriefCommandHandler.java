package com.git.hui.jobclaw.application.service;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.cli.SystemCommandHandler;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.web.model.res.JobApplicationBriefVo;
import com.git.hui.jobclaw.web.model.res.JobApplicationEventVo;
import com.git.hui.jobclaw.web.model.res.JobApplicationVo;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

@Component
public class ApplicationBriefCommandHandler implements SystemCommandHandler {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("M月d日 HH:mm");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final JobApplicationService jobApplicationService;

    public ApplicationBriefCommandHandler(JobApplicationService jobApplicationService) {
        this.jobApplicationService = jobApplicationService;
    }

    @Override
    public boolean handle(ChannelReceiveMessage msg, UserConversationInfo conversationInfo, String command,
                          Function<String, Boolean> process) {
        Long userId = parseUserId(conversationInfo.jobClawUserId());
        if (userId == null) {
            return process.apply("""
                    还没有识别到你的 JobClaw 用户身份。

                    请先在网页端登录并绑定当前 IM 账号，然后再发送 /brief 查看投递行动简报。
                    """);
        }

        JobApplicationBriefVo brief = jobApplicationService.brief(userId, 5);
        return process.apply(formatBrief(brief));
    }

    @Override
    public boolean supports(String command) {
        return command != null && (command.startsWith("/brief") || command.startsWith("/today"));
    }

    @Override
    public String getCommand() {
        return "/brief";
    }

    @Override
    public PresetAgentIntro getIntentType() {
        return PresetAgentIntro.QUERY;
    }

    @Override
    public String getDescription() {
        return "查看今日投递行动简报，也可使用 /today";
    }

    private String formatBrief(JobApplicationBriefVo brief) {
        // AIDEV-NOTE: AI-GENERATED brief command
        if (brief == null || value(brief.getTotal()) == 0) {
            return """
                    今日投递简报

                    还没有投递记录。可以先在岗位列表里把感兴趣的岗位加入“我的投递”。
                    """;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("今日投递简报\n\n");
        if (StringUtils.hasText(brief.getSummary())) {
            sb.append(brief.getSummary()).append("\n\n");
        }
        sb.append("概览：")
                .append("行动 ").append(value(brief.getActionCount()))
                .append(" 项，A 级 ").append(value(brief.getPriorityA()))
                .append("，逾期跟进 ").append(value(brief.getOverdueFollowUps()))
                .append("，今日截止 ").append(value(brief.getDueToday()))
                .append("，临近截止 ").append(value(brief.getDueSoon()))
                .append("，静默投递 ").append(value(brief.getStaleSubmitted()))
                .append("，今日日程 ").append(value(brief.getTodayEvents()))
                .append("，未来 7 天关键日程 ").append(value(brief.getNext7DayEvents()))
                .append("。\n");

        appendActions(sb, brief.getTopActions());
        appendEvents(sb, brief.getUpcomingEvents());
        return sb.toString();
    }

    private void appendActions(StringBuilder sb, List<JobApplicationVo> actions) {
        if (actions == null || actions.isEmpty()) {
            sb.append("\n当前没有需要优先处理的投递事项。\n");
            return;
        }
        sb.append("\n优先行动：\n");
        for (int i = 0; i < Math.min(actions.size(), 3); i++) {
            JobApplicationVo item = actions.get(i);
            sb.append(i + 1).append(". [").append(text(item.getActionPriority(), "-")).append("] ")
                    .append(text(item.getCompanyName(), "未知公司"))
                    .append(" / ")
                    .append(text(item.getPosition(), "未知岗位"))
                    .append("：")
                    .append(text(item.getSuggestedNextAction(), "检查当前状态并补充下一步计划"))
                    .append("\n");
        }
    }

    private void appendEvents(StringBuilder sb, List<JobApplicationEventVo> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        sb.append("\n未来 7 天关键日程：\n");
        for (int i = 0; i < Math.min(events.size(), 3); i++) {
            JobApplicationEventVo event = events.get(i);
            sb.append(i + 1).append(". ")
                    .append(formatTime(event.getEventTime()))
                    .append(" ")
                    .append(text(event.getEventType(), "EVENT"))
                    .append(" - ")
                    .append(text(event.getCompanyName(), "未知公司"))
                    .append(" / ")
                    .append(text(event.getPosition(), "未知岗位"));
            if (StringUtils.hasText(event.getSuggestedPreparation())) {
                sb.append("；").append(event.getSuggestedPreparation());
            }
            sb.append("\n");
        }
    }

    private static Long parseUserId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String formatTime(Long timestamp) {
        if (timestamp == null) {
            return "时间待定";
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZONE).format(TIME_FORMATTER);
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static String text(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
