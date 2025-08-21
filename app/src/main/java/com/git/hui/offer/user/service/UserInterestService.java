package com.git.hui.offer.user.service;

import com.git.hui.offer.components.context.ReqInfoContext;
import com.git.hui.offer.gather.service.ai.OcAiModelContext;
import com.git.hui.offer.oc.helper.OcInfoTransfer;
import com.git.hui.offer.user.convert.UserConvert;
import com.git.hui.offer.user.dao.entity.UserInterestEntity;
import com.git.hui.offer.user.dao.repository.UserInterestRepository;
import com.git.hui.offer.user.model.UserInterestBo;
import com.git.hui.offer.web.model.res.UserInterestVo;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 用户订阅偏好服务
 *
 * @author YiHui
 * @date 2025/8/21
 */
@Service
public class UserInterestService {

    private final OcAiModelContext aiModelFacade;

    private final UserInterestRepository userInterestRepository;

    private final OcInfoTransfer ocInfoTransfer;

    public UserInterestService(OcAiModelContext aiModelFacade, UserInterestRepository userInterestRepository, OcInfoTransfer ocInfoTransfer) {
        this.aiModelFacade = aiModelFacade;
        this.userInterestRepository = userInterestRepository;
        this.ocInfoTransfer = ocInfoTransfer;
    }

    /**
     * 提交用户的偏好信息
     *
     * @param text
     */
    public void submitInterest(String text) {
        ChatClient chatClient = this.aiModelFacade.getMainChatClient();
        // 根据用户传入的信息，提取需要关注的内容
        UserInterestBo userInterestBo = chatClient.prompt()
                .user("我现在是一个准备找工作的人，下面是我的个人信息，我希望从中提取出用于筛选招聘岗位的关键信息: \n" + text)
                .call().entity(UserInterestBo.class);
        // 查询之前的记录
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        UserInterestEntity entity = userInterestRepository.findByUserId(userId);
        if (entity == null) {
            entity = new UserInterestEntity();
            entity.setUserId(userId);
            entity.setCreateTime(new Date());
        }
        entity.setInterest(text)
                .setCompanyType(userInterestBo.companyType())
                .setCompanyIndustry(userInterestBo.companyIndustry())
                .setJobLocation(userInterestBo.jobLocation())
                .setRecruitmentType(userInterestBo.recruitmentType())
                .setRecruitmentTarget(userInterestBo.recruitmentTarget())
                .setPosition(userInterestBo.position())
                .setUpdateTime(new Date());

        ocInfoTransfer.autoFormatDraftInfo(entity);
        userInterestRepository.saveAndFlush(entity);
    }

    public UserInterestVo getUserInterest(Long userId) {
        UserInterestEntity entity = userInterestRepository.findByUserId(userId);
        return UserConvert.toInterestVo(entity);
    }
}
