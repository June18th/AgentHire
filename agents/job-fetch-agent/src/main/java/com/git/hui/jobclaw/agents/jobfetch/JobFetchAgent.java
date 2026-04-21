package com.git.hui.jobclaw.agents.jobfetch;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.git.hui.jobclaw.agents.jobfetch.service.JobFetchService;
import com.git.hui.jobclaw.agents.jobfetch.service.model.JobFetchTaskResponse;
import com.git.hui.jobclaw.core.agent.LlmCaller;
import com.git.hui.jobclaw.core.agent.impl.AbsBizAgent;
import com.git.hui.jobclaw.core.agent.llm.ClientSelector;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 职位抓取Agent
 * 支持从网络爬取和从文本/文件中提取职位信息
 *
 * @author YiHui
 * @date 2026/4/17
 */
@Slf4j
@Component
public class JobFetchAgent extends AbsBizAgent {

    private final JobFetchService jobFetchService;

    public JobFetchAgent(ClientSelector clientSelector, JobFetchService jobFetchService) {
        super(clientSelector);
        this.jobFetchService = jobFetchService;
    }

    private static final String SYSTEM_PROMPT = """
            你是专业的职位信息获取助手。
                            
            🎯 核心职责: 从用户的输入中识别职位信息获取需求,并**必须使用工具**来执行
            
            ## 工作流程 (严格遵守)
            1. **分析用户意图**: 判断用户想要从哪里获取职位信息
            2. **选择对应工具**: 根据输入类型调用合适的工具
            3. **返回任务信息**: 工具会自动返回任务ID和状态,直接展示给用户
            
            ## 工具使用规则 (必须遵守)
            
            ### 场景1: 用户提供URL链接
            → **必须调用** `fetchJobsFromUrl(url)` 工具
            → 示例: "帮我爬取这个链接的职位: https://xxx.com/jobs"
            → 示例: "从这个网址提取招聘信息: https://example.com"
            
            ### 场景2: 用户上传文件/图片
            → **必须调用** `extractJobsFromTextOrLocalFile(text=null, path=文件路径)` 工具
            → 系统会自动检测附件,你只需要调用工具即可
            → 示例: 用户发送PDF/Word/Excel文件或招聘海报图片
            
            ### 场景3: 用户粘贴文本内容
            → **必须调用** `extractJobsFromTextOrLocalFile(text=文本内容, path=null)` 工具
            → 示例: "从这段文字中提取职位: [粘贴的文本]"
            → 示例: "分析以下招聘信息: [文本内容]"
            
            ## 重要提醒
            ⚠️ **绝对不要**自己尝试解析URL或处理文件
            ⚠️ **绝对不要**直接回复用户说"我会帮你...",而是**立即调用工具**
            ⚠️ **绝对不要**忽略用户的URL、文件或文本内容
            ⚠️ 工具调用后会自动创建异步任务,并返回友好的提示信息
            
            ## 其他情况
            - 如果用户询问任务进度,引导他们使用 `/task <任务ID>` 命令
            - 如果用户的问题与职位获取无关,可以正常对话
            """;

    private static final AgentIntro INTRO = new AgentIntro() {
        @Override
        public String getAgentId() {
            return "jobFetchAgent";
        }

        @Override
        public String getIntro() {
            return "职位信息获取专家";
        }

        @Override
        public String getDescription() {
            return """
                    JobFetchAgent是专门用于获取职业信息的Agent，支持两种获取方式：
                                        
                    🕷️ 网络爬虫模式
                    - 从招聘网站自动爬取职位信息
                    - 支持智联招聘、前程无忧、拉勾网、BOSS直聘等主流平台
                    - 自动解析网页结构，提取结构化数据
                                        
                    📄 内容提取模式
                    - 从文本内容中提取职位信息
                    - 支持文件上传（PDF、Word、Excel、CSV等）
                    - 支持图片OCR识别（招聘海报、截图等）
                    - 支持HTML网页内容解析
                                        
                    💡 使用示例
                    - "帮我从这个链接爬取职位：https://xxx.com/jobs"
                    - "从这段文字中提取职位信息：[粘贴文本]"
                    - "分析这个PDF文件中的招聘信息"
                    """;
        }
    };

    @Override
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }
    @Override
    public ToolCallback[] getTools() {
        return ToolCallbacks.from(this);
    }
    @Override
    public AgentIntro getAgentIntro() {
        return INTRO;
    }

    @Override
    public List<AgentIntro> getSupportedIntents() {
        return List.of(INTRO, PresetAgentIntro.CHAT);
    }

    @Override
    public String process(LlmCaller.UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        if (CollectionUtils.isEmpty(message.getFiles()) && CollectionUtils.isEmpty(message.getMedias())) {
            // 需要判断从网络爬去，还是从文本中提取
            ChatClient chatClient = getChatClient(message.getJobClawUserId());
            return chatClient.prompt(message.getMessage())
                    .toolContext(Map.of("msg", message))
                    .call().content();
        } else {
            // 如果包含附件
            var path = CollectionUtils.isEmpty(message.getFiles()) ? message.getMedias().get(0).getFilePath() : message.getFiles().get(0).getFilePath();
            JobFetchTaskResponse taskResponse = jobFetchService.fetchFromTextOrLocalFile(userConversationInfo, message.getMessage(), path.toString(), message);
            return buildTaskCreatedMessage(taskResponse, "文件/图片");
        }
    }

    @Override
    public Flux<LlmRspCell> stream(LlmCaller.UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        log.info("JobFetchAgent process!");
        if (CollectionUtils.isEmpty(message.getFiles()) && CollectionUtils.isEmpty(message.getMedias())) {
            // 需要判断从网络爬去，还是从文本中提取
            ChatClient chatClient = getChatClient(message.getJobClawUserId());
            return chatClient.prompt(message.getMessage())
                    .toolContext(Map.of("msg", message, "conversation", userConversationInfo))
                    .stream()
                    .chatResponse()
                    .map(LlmRspCell::of);
        } else {
            // 如果包含附件
            var path = CollectionUtils.isEmpty(message.getFiles()) ? message.getMedias().get(0).getFilePath() : message.getFiles().get(0).getFilePath();
            JobFetchTaskResponse taskResponse = jobFetchService.fetchFromTextOrLocalFile(userConversationInfo, message.getMessage(), path.toString(), message);
            String friendlyMessage = buildTaskCreatedMessage(taskResponse, "文件/图片");
            return Flux.just(new LlmRspCell(null, friendlyMessage, null));
        }
    }



    // ==================== 工具方法 ====================

    @Tool(description = "从URL爬取职位信息", returnDirect = true)
    public String fetchJobsFromUrl(
            @JsonPropertyDescription("招聘网站的URL地址")
            String url,
            ToolContext toolContext) {
        log.info("工具调用：从 URL爬取职位信息 - {}", url);
        ChannelReceiveMessage msg = (ChannelReceiveMessage) toolContext.getContext().get("msg");
        LlmCaller.UserConversationInfo userConversationInfo = (LlmCaller.UserConversationInfo) toolContext.getContext().get("conversation");
        JobFetchTaskResponse taskResponse = jobFetchService.fetchFromUrl(userConversationInfo, url, msg);
        return buildTaskCreatedMessage(taskResponse, "网页");
    }

    @Tool(description = "从给入的文本内容、或者文件/图片中提取职位信息", returnDirect = true)
    public String extractJobsFromTextOrLocalFile(
            @JsonPropertyDescription("包含职位信息的文本内容，当从文件中解析职位信息时，text应该为null或空字符串")
            String text,
            @JsonPropertyDescription("包含职位信息的本地文件路径")
            String path,
            ToolContext toolContext) {
        log.info("工具调用：从文本提取职位信息，文本: {}, 文件: {}", text == null ? "-" : text.length(), path);
        ChannelReceiveMessage msg = (ChannelReceiveMessage) toolContext.getContext().get("msg");
        LlmCaller.UserConversationInfo userConversationInfo = (LlmCaller.UserConversationInfo) toolContext.getContext().get("conversation");
        JobFetchTaskResponse taskResponse = jobFetchService.fetchFromTextOrLocalFile(userConversationInfo, text, path, msg);
        String sourceType = (text != null && !text.isBlank()) ? "文本" : "文件/图片";
        var ans = buildTaskCreatedMessage(taskResponse, sourceType);
        log.info("返回结果: {}", ans);
        return ans;
    }

    /**
     * 构建任务创建的友好提示信息
     *
     * @param taskResponse 任务响应
     * @param sourceType   来源类型(网页/文本/文件/图片)
     * @return 友好的提示消息
     */
    private String buildTaskCreatedMessage(JobFetchTaskResponse taskResponse, String sourceType) {
        return "\n\n✅ 已创建" + sourceType + "职位提取任务\n\n" +
                "📋 任务ID: `" + taskResponse.getTaskId() + "`\n\n" +
                "📊 当前状态: " + getStatusEmoji(taskResponse.getStatus()) + " " + getStatusText(taskResponse.getStatus()) + "\n\n" +
                "💡 提示:\n\n" +
                "• 任务正在后台执行中,请稍候...\n\n" +
                "• 您可以通过任务ID查询进度\n\n" +
                "• 提取完成后会主动通知您\n\n" +
                "🔍 查询命令: `/task " + taskResponse.getTaskId() + "`\n\n";
    }

    /**
     * 获取状态对应的Emoji
     */
    private String getStatusEmoji(String status) {
        return switch (status) {
            case "PENDING" -> "⏳";
            case "RUNNING" -> "🔄";
            case "SUCCESS" -> "✅";
            case "FAILED" -> "❌";
            default -> "❓";
        };
    }

    /**
     * 获取状态的中文描述
     */
    private String getStatusText(String status) {
        return switch (status) {
            case "PENDING" -> "等待执行";
            case "RUNNING" -> "执行中";
            case "SUCCESS" -> "已完成";
            case "FAILED" -> "失败";
            default -> "未知";
        };
    }
}
