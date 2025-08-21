package com.git.hui.offer.oc.helper;

import com.git.hui.offer.configs.service.CommonDictService;
import com.git.hui.offer.constants.oc.OcConstants;
import com.git.hui.offer.oc.convert.OcConvert;
import com.git.hui.offer.oc.dao.entity.OcDraftEntity;
import com.git.hui.offer.oc.dao.entity.OcInfoEntity;
import com.git.hui.offer.user.dao.entity.UserInterestEntity;
import org.springframework.stereotype.Component;

/**
 * @author YiHui
 * @date 2025/8/21
 */
@Component
public class OcInfoTransfer {
    private final CommonDictService commonDictService;

    public OcInfoTransfer(CommonDictService commonDictService) {
        this.commonDictService = commonDictService;
    }

    /**
     * 根据字典配置中的值，对草稿中的相关字段进行格式化
     *
     * @param draft 草稿数据
     */
    public OcInfoEntity autoFormatDraftInfo(OcDraftEntity draft) {
        OcInfoEntity ocEntity = OcConvert.toOc(draft);
        // 1. 公司类型
        commonDictService.queryDictForOptional(OcConstants.APP, OcConstants.COMPANY_TYPE_KEY)
                .ifPresent(dict -> {
                    // 没有匹配的公司类型，则设置为空，让后续进行编辑
                    if (dict.items().stream().noneMatch(item ->
                            ocEntity.getCompanyType().equals(item.value())
                                    || ocEntity.getCompanyType().equals(item.intro())
                    )) {
                        ocEntity.setCompanyType("");
                    }
                });

        // 招聘类型
        commonDictService.queryDictForOptional(OcConstants.APP, OcConstants.RECRUITMENT_TYPE_KEY)
                .ifPresent(dict -> {
                    if (dict.items().stream().noneMatch(item ->
                            ocEntity.getRecruitmentType().equals(item.value())
                                    || ocEntity.getRecruitmentType().equals(item.intro())
                    )) {
                        ocEntity.setRecruitmentType("");
                    }
                });

        // 招聘对象
        commonDictService.queryDictForOptional(OcConstants.APP, OcConstants.RECRUITMENT_TARGET_KEY)
                .ifPresent(dict -> {
                    if (dict.items().stream().noneMatch(item ->
                            ocEntity.getRecruitmentTarget().equals(item.value())
                                    || ocEntity.getRecruitmentTarget().equals(item.intro())
                    )) {
                        ocEntity.setRecruitmentTarget("");
                    }
                });

        // 工作地点格式化
        ocEntity.setJobLocation(OcConstants.jobLocationFormat(ocEntity.getJobLocation()));
        // 职位格式化
        ocEntity.setPosition(OcConstants.positionFormat(ocEntity.getPosition()));

        // 链接校验
        ocEntity.setRelatedLink(OcConstants.urlCheck(ocEntity.getRelatedLink()));
        ocEntity.setJobAnnouncement(OcConstants.urlCheck(ocEntity.getJobAnnouncement()));
        return ocEntity;
    }

    public void autoFormatDraftInfo(UserInterestEntity entity) {
        // 1. 公司类型
        commonDictService.queryDictForOptional(OcConstants.APP, OcConstants.COMPANY_TYPE_KEY)
                .ifPresent(dict -> {
                    // 没有匹配的公司类型，则设置为空，让后续进行编辑
                    if (dict.items().stream().noneMatch(item ->
                            entity.getCompanyType().equals(item.value())
                                    || entity.getCompanyType().equals(item.intro())
                    )) {
                        entity.setCompanyType("");
                    }
                });

        // 招聘类型
        commonDictService.queryDictForOptional(OcConstants.APP, OcConstants.RECRUITMENT_TYPE_KEY)
                .ifPresent(dict -> {
                    if (dict.items().stream().noneMatch(item ->
                            entity.getRecruitmentType().equals(item.value())
                                    || entity.getRecruitmentType().equals(item.intro())
                    )) {
                        entity.setRecruitmentType("");
                    }
                });

        // 招聘对象
        commonDictService.queryDictForOptional(OcConstants.APP, OcConstants.RECRUITMENT_TARGET_KEY)
                .ifPresent(dict -> {
                    if (dict.items().stream().noneMatch(item ->
                            entity.getRecruitmentTarget().equals(item.value())
                                    || entity.getRecruitmentTarget().equals(item.intro())
                    )) {
                        entity.setRecruitmentTarget("");
                    }
                });

        // 工作地点格式化
        entity.setJobLocation(OcConstants.jobLocationFormat(entity.getJobLocation()));
        // 职位格式化
        entity.setPosition(OcConstants.positionFormat(entity.getPosition()));
    }
}
