package com.git.hui.jobclaw.agents.jobfetch.crawler.impl;

import com.git.hui.jobclaw.agents.jobfetch.crawler.JobCrawler;
import com.git.hui.jobclaw.agents.jobfetch.crawler.impl.tool.SmartWebFetchTool;
import com.git.hui.jobclaw.agents.jobfetch.llm.JobLlmCaller;
import com.git.hui.jobclaw.agents.jobfetch.model.JobInfo;
import com.git.hui.jobclaw.agents.jobfetch.util.GatherResFormat;
import com.git.hui.jobclaw.core.agent.LlmCaller;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用网页爬虫
 * 使用SmartWebFetchTool抓取网页内容,然后提取职位信息
 * 支持分页处理,当返回内容较多时会进行多轮对话获取完整数据
 *
 * @author YiHui
 * @date 2026/4/18
 */
@Slf4j
@Component
public class GenericWebCrawler implements JobCrawler {
    private static final int MAX_CHAT_CNT = 20;

    protected final JobLlmCaller jobLlmCaller;

    protected BeanOutputConverter<ArrayList<JobInfo>> gatherResConverter;
    protected final Resource promptResource;

    public GenericWebCrawler(JobLlmCaller jobLlmCaller,
                             @Value("classpath:prompts/job-info-crawler-prompt.md")
                             Resource promptResource) {
        this.jobLlmCaller = jobLlmCaller;
        this.promptResource = promptResource;
        gatherResConverter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });
    }

    @Override
    public String getName() {
        return "GenericWebCrawler";
    }

    @Override
    public boolean supports(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(url);
            String host = uri.getHost();

            // 支持常见的招聘网站
            return host != null && (
                    host.contains("zhaopin.com") ||      // 智联招聘
                            host.contains("51job.com") ||         // 前程无忧
                            host.contains("lagou.com") ||         // 拉勾网
                            host.contains("bosszhipin.com") ||    // BOSS直聘
                            host.contains("liepin.com") ||        // 猎聘
                            host.contains("shixiseng.com") ||     // 实习僧
                            host.endsWith(".com") ||              // 其他.com网站
                            host.endsWith(".cn")                  // 其他.cn网站
            );
        } catch (Exception e) {
            log.warn("无效的URL: {}", url);
            return false;
        }
    }

    @Override
    public List<JobInfo> crawl(LlmCaller.UserConversationInfo userConversationInfo, String url, String originMsg) {
        log.info("开始爬取URL! {} -> {}", originMsg, url);

        try {
            // 创建ChatClient并构建工具
            ChatModel model = getModel(userConversationInfo.jobClawUserId());
            ChatClient client = ChatClient.builder(model)
                    .defaultTools()
                    .build();
            SmartWebFetchTool webFetchTool = SmartWebFetchTool.builder(client)
                    // fixme 为了安全起见，不应该关闭这个安全校验
                    .domainSafetyCheck(false)
                    .summaryEnable(false)
                    .build();
            var tools = ToolCallbacks.from(webFetchTool);

            // 创建 memory 实例，保存上下文
            ChatMemory chatMemory = MessageWindowChatMemory
                    .builder()
                    .maxMessages(MAX_CHAT_CNT)
                    .build();
            chatMemory.add(userConversationInfo.conversationId(), new SystemMessage(promptResource));

            String userMessage = String.format("%s\n网页地址：%s\n%s", originMsg, url, gatherResConverter.getFormat());

            // 执行分页提取
            List<String> itemList = extractByPage(chatMemory, userMessage, model, tools, userConversationInfo.conversationId());

            if (itemList.isEmpty()) {
                log.info("未从网页中提取到职位信息: {}", url);
                return List.of();
            }

            // 转换为JobInfo对象
            List<JobInfo> jobInfos = itemList.stream()
                    .map(this::toJobInfo)
                    .filter(job -> job != null && job.isValid())
                    .toList();

            log.info("从网页{}中成功提取{}条职位信息", url, jobInfos.size());
            return jobInfos;

        } catch (Exception e) {
            log.error("爬取网页失败: {}", url, e);
            throw new RuntimeException("爬取网页失败: " + url, e);
        }
    }

    /**
     * 分页提取职位信息
     * 当大模型返回内容不完整时,进行多轮对话获取剩余内容
     *
     * @param chatMemory   系统提示词
     * @param userMessage    用户消息(包含网页内容)
     * @param model          聊天模型
     * @param conversationId 会话ID
     * @return 提取的原始JSON字符串列表
     */
    private List<String> extractByPage(ChatMemory chatMemory,
                                       String userMessage,
                                       ChatModel model,
                                       ToolCallback[] tools,
                                       String conversationId) {
        List<String> itemList = new ArrayList<>();
        StringBuilder remain = new StringBuilder();
        int cnt = 0;
        // 工具
        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(tools)
                .build();

        while (true) {
            log.info("{}#第{}次大模型数据解析", conversationId, cnt + 1);
            UserMessage msg;
            if (cnt == 0) {
                msg = new UserMessage(userMessage);
            } else {
                msg = new UserMessage("你之前返回的结果不完整，继续返回剩余的内容");
            }

            chatMemory.add(conversationId, msg);


            try {
                Prompt query = Prompt.builder().messages(chatMemory.get(conversationId)).chatOptions(chatOptions).build();
                if (log.isDebugEnabled()) {
                    // 一行显示日志
                    log.debug("{}#req: {}", conversationId, StringUtils.replaceChars(query.toString(), "\n", ""));
                }

                ChatResponse response = model.call(query);
                AssistantMessage assistantMessage = response.getResult().getOutput();

                if (log.isDebugEnabled()) {
                    log.debug("{}#res: {}", conversationId, StringUtils.replaceChars(assistantMessage.toString(), "\n", ""));
                }

                cnt += 1;

                String outText = assistantMessage.getText().trim();
                itemList.addAll(GatherResFormat.extract(remain, outText));

                // 判断是否完成提取
                if (outText.endsWith("```") || outText.endsWith("]") || cnt >= MAX_CHAT_CNT) {
                    log.info("{}#经过{}轮对话,完成大模型调用", conversationId, cnt);
                    break;
                }

                // 检测大模型是否重新开始返回完整数据
                if (cnt > 1 && outText.startsWith("```json")) {
                    int jsonBeginIndex = outText.indexOf("[");
                    if (jsonBeginIndex > 0 && jsonBeginIndex < 15) {
                        log.info("{}#大模型重复返回完整解析数据,主动退出多轮对话", conversationId);
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("{}#gather error", conversationId, e);
                break;
            }
        }

        return itemList;
    }


    /**
     * 将JSON字符串转换为JobInfo对象
     */
    private JobInfo toJobInfo(String jsonStr) {
        try {
            return JsonUtil.toObj(jsonStr, JobInfo.class);
        } catch (Exception e) {
            log.warn("解析职位信息失败: {}", jsonStr, e);
            return null;
        }
    }

    protected ChatModel getModel(String jobClawUserId) {
        return jobLlmCaller.getChatModel(jobClawUserId, false);
    }
}
