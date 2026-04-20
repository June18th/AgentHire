package com.git.hui.jobclaw.agents.jobfetch;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.git.hui.jobclaw.agents.jobfetch.model.JobInfo;
import com.git.hui.jobclaw.core.agent.LlmCaller;
import com.git.hui.jobclaw.core.agent.impl.AbsBizAgent;
import com.git.hui.jobclaw.core.agent.llm.ClientSelector;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
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
                            
            你擅长从用户的输入中，判断如何获取职位信息
            1. 🕷️ 从招聘网站爬取职位信息
            2. 📄 从文本/文件中提取职位信息

            可用的工具：
            - fetchJobsFromUrl: 从URL爬取职位
            - extractJobsFromTextOrLocalFile: 从文本或者本地文件中提取职位
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
    public AgentIntro getAgentIntro() {
        return INTRO;
    }

    @Override
    public List<AgentIntro> getSupportedIntents() {
        return List.of(INTRO, PresetAgentIntro.CHAT);
    }

    @Override
    public String process(LlmCaller.UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        // 如果包含附件
        if (CollectionUtils.isEmpty(message.getFiles()) && CollectionUtils.isEmpty(message.getMedias())) {
            // 需要判断从网络爬去，还是从文本中提取
            ChatClient chatClient = getChatClient(message.getJobClawUserId());
            return chatClient.prompt(message.getMessage())
                    .toolContext(Map.of("msg", message))
                    .call().content();
        } else {
            var path = CollectionUtils.isEmpty(message.getFiles()) ? message.getMedias().get(0).getFilePath() : message.getFiles().get(0).getFilePath();
            List<JobInfo> jobs = jobFetchService.fetchFromTextOrLocalFile(userConversationInfo, message.getMessage(), path.toString(), message);
            return JsonUtil.toStr(jobs);
        }
    }

    // todo 改为异步提取的策略，先直接响应用户，然后在后台创建一个爬去任务，等待执行完毕之后通知用户结果
    @Override
    public Flux<LlmRspCell> stream(LlmCaller.UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        // 如果包含附件
        if (CollectionUtils.isEmpty(message.getFiles()) && CollectionUtils.isEmpty(message.getMedias())) {
            // 需要判断从网络爬去，还是从文本中提取
            ChatClient chatClient = getChatClient(message.getJobClawUserId());
            return chatClient.prompt(message.getMessage())
                    .toolContext(Map.of("msg", message, "conversation", userConversationInfo))
                    .stream()
                    .chatResponse()
                    .map(LlmRspCell::of);
        } else {
            var path = CollectionUtils.isEmpty(message.getFiles()) ? message.getMedias().get(0).getFilePath() : message.getFiles().get(0).getFilePath();
            List<JobInfo> jobs = jobFetchService.fetchFromTextOrLocalFile(userConversationInfo, message.getMessage(), path.toString(), message);
            return Flux.just(new LlmRspCell(null, JsonUtil.toStr(jobs), null));
        }
    }

    // ==================== 工具方法 ====================

    @Tool(description = "从URL爬取职位信息", returnDirect = true)
    public List<JobInfo> fetchJobsFromUrl(
            @JsonPropertyDescription("招聘网站的URL地址")
            String url,
            ToolContext toolContext) {
        log.info("工具调用：从URL爬取职位信息 - {}", url);
        ChannelReceiveMessage msg = (ChannelReceiveMessage) toolContext.getContext().get("msg");
        LlmCaller.UserConversationInfo userConversationInfo = (LlmCaller.UserConversationInfo) toolContext.getContext().get("conversation");
        return jobFetchService.fetchFromUrl(userConversationInfo, url, msg);
    }

    @Tool(description = "从给入的文本内容、或者文件/图片中提取职位信息", returnDirect = true)
    public List<JobInfo> extractJobsFromTextOrLocalFile(
            @JsonPropertyDescription("包含职位信息的文本内容，当从文件中解析职位信息时，text应该为null或空字符串")
            String text,
            @JsonPropertyDescription("包含职位信息的本地文件路径")
            String path,
            ToolContext toolContext) {
        log.info("工具调用：从文本提取职位信息，文本: {}, 文件: {}", text == null ? "-" : text.length(), path);
        ChannelReceiveMessage msg = (ChannelReceiveMessage) toolContext.getContext().get("msg");
        LlmCaller.UserConversationInfo userConversationInfo = (LlmCaller.UserConversationInfo) toolContext.getContext().get("conversation");
        return jobFetchService.fetchFromTextOrLocalFile(userConversationInfo, text, path, msg);
    }
}
