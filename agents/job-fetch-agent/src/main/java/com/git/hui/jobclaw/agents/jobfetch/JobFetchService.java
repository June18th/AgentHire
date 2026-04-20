package com.git.hui.jobclaw.agents.jobfetch;

import com.git.hui.jobclaw.agents.jobfetch.crawler.JobCrawler;
import com.git.hui.jobclaw.agents.jobfetch.extract.JobExtractor;
import com.git.hui.jobclaw.agents.jobfetch.extract.impl.TextJobExtractor;
import com.git.hui.jobclaw.agents.jobfetch.model.JobInfo;
import com.git.hui.jobclaw.core.agent.LlmCaller;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 *
 * @author YiHui
 * @date 2026/4/20
 */
@Slf4j
@Service
public class JobFetchService {

    @Autowired
    private JobCrawler jobCrawler;
    @Autowired
    private List<JobExtractor> jobExtractorList;

    public List<JobInfo> fetchFromUrl(LlmCaller.UserConversationInfo userConversationInfo, String url, ChannelReceiveMessage msg) {
        return jobCrawler.crawl(userConversationInfo, url, msg.getMessage());
    }

    public List<JobInfo> fetchFromTextOrLocalFile(
            LlmCaller.UserConversationInfo userConversationInfo,
            String text, String path,
            ChannelReceiveMessage msg) {
        // 首先判断是否有附件
        var extractor = textExtractor(msg);
        if (extractor != null) {
            log.info("将使用 {} 是实现信息提取", extractor.getName());
            return extractor.extractFromInput(userConversationInfo, msg);
        }

        return List.of();
    }

    private JobExtractor textExtractor(ChannelReceiveMessage msg) {
        if (!CollectionUtils.isEmpty(msg.getFiles())) {
            var file = msg.getFiles().get(0);
            for (JobExtractor extractor : jobExtractorList) {
                if (extractor.supports(file.getMimeType())) {
                    return extractor;
                }
            }
        } else if (!CollectionUtils.isEmpty(msg.getMedias())) {
            var media = msg.getMedias().get(0);
            for (JobExtractor extractor : jobExtractorList) {
                if (extractor.supports(media.getMimeType())) {
                    return extractor;
                }
            }
        }


        // 没有附件，直接走文本
        for (JobExtractor extractor : jobExtractorList) {
            if (extractor instanceof TextJobExtractor) {
                return extractor;
            }
        }
        return null;
    }

}
