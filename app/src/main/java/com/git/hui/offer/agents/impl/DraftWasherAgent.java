package com.git.hui.offer.agents.impl;

import com.git.hui.offer.agents.OcAgentState;
import com.git.hui.offer.configs.service.CommonDictService;
import com.git.hui.offer.constants.oc.OcConstants;
import com.git.hui.offer.oc.dao.entity.OcDraftEntity;
import com.git.hui.offer.oc.dao.repository.OcDraftRepository;
import com.git.hui.offer.web.model.res.CommonDictVo;
import com.git.hui.offer.web.model.res.GatherVo;
import com.google.common.base.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 草稿数据清洗Agent
 *
 * @author YiHui
 * @date 2025/8/13
 */
@Slf4j
@Service
public class DraftWasherAgent extends BaseAgent {
    public static final String AGENT_NAME = "draft_washer";
    @Autowired
    private CommonDictService commonDictService;
    @Autowired
    private OcDraftRepository ocDraftRepository;

    @Override
    public String agentName() {
        return AGENT_NAME;
    }

    public Map<String, Object> apply(OcAgentState ocAgentState) {
        GatherVo vo = ocAgentState.getGather();
        printAgentStartLog(vo);
        // 清洗数据
        List<Long> ids = new ArrayList<>();
        if (!CollectionUtils.isEmpty(vo.getInsertList())) {
            vo.getInsertList().forEach(item -> {
                if (autoUpdateWashData(item)) {
                    ids.add(item.getId());
                }
            });
        }
        if (!CollectionUtils.isEmpty(vo.getUpdateList())) {
            vo.getUpdateList().forEach(item -> {
                if (autoUpdateWashData(item)) {
                    ids.add(item.getId());
                }
            });
        }

        // 根据规则，将大模型新增和更新的数据进行清洗，若清洗完毕之后，一个数据已非常符合发布的条件，则将其传递给下一个发布节点；否则不发布这个节点
        Map<String, Object> map = new HashMap<>();
        map.put(OcAgentState.WASHER, new WasherRecords(ids));
        printAgentEndLog(map);
        return map;
    }

    public record WasherRecords(List<Long> ids) {
    }

    /**
     * 自动清洗数据
     *
     * @param draft
     * @return
     */
    private boolean autoUpdateWashData(OcDraftEntity draft) {
        if (!formatCompanyType(draft)) {
            return false;
        }
        if (!formatRecruitmentType(draft)) {
            return false;
        }
        if (!formatRecruitmentTarget(draft)) {
            return false;
        }

        if (StringUtils.isBlank(OcConstants.urlCheck(draft.getRelatedLink()))) {
            return false;
        }

        ocDraftRepository.flush();
        return true;
    }

    private static final Map<String, String> COMPANY_TYPE = Map.of("私企", "民企", "民企", "私企");

    private boolean formatCompanyType(OcDraftEntity draft) {
        CommonDictVo dict = commonDictService.queryDict(OcConstants.APP, OcConstants.COMPANY_TYPE_KEY);
        if (dict == null) return false;
        // 公司类型匹配
        if (dict.items().stream()
                .anyMatch(item -> draft.getCompanyType().equals(item.value()) || draft.getCompanyType()
                        .equals(item.intro()))) {
            return true;
        }

        return autoTrans(dict, draft, COMPANY_TYPE, draft::getCompanyType, draft::setCompanyType, this::formatCompanyType);
    }

    private static final Map<String, String> RECRUITMEN_TYPE = Map.of("实习", "暑期实习", "暑假实习", "暑期实习");

    private boolean formatRecruitmentType(OcDraftEntity draft) {
        // 招聘类型
        CommonDictVo dict = commonDictService.queryDict(OcConstants.APP, OcConstants.RECRUITMENT_TYPE_KEY);
        if (dict == null) return false;
        if (dict.items().stream()
                .anyMatch(item -> draft.getRecruitmentType().equals(item.value()) || draft.getRecruitmentType()
                        .equals(item.intro()))) {
            return true;
        }

        return autoTrans(dict, draft, RECRUITMEN_TYPE, draft::getRecruitmentType, draft::setRecruitmentType, this::formatRecruitmentType);
    }


    private boolean formatRecruitmentTarget(OcDraftEntity draft) {
        CommonDictVo dict = commonDictService.queryDict(OcConstants.APP, OcConstants.RECRUITMENT_TARGET_KEY);
        if (dict == null) return false;
        if (dict.items().stream()
                .anyMatch(item -> draft.getRecruitmentTarget().equals(item.value()) || draft.getRecruitmentTarget()
                        .equals(item.intro()))) {
            return true;
        }

        if (draft.getRecruitmentTarget().contains("届")) {
            draft.setRecruitmentTarget(draft.getRecruitmentTarget().replace("届", "年"));
        }
        if (!draft.getRecruitmentTarget().contains("毕业生")) {
            draft.setRecruitmentTarget(draft.getRecruitmentTarget() + "毕业生");
        }
        boolean ans = formatRecruitmentTarget(draft);
        if (ans) {
            // 保存
            ocDraftRepository.save(draft);
        }
        return ans;
    }

    private boolean autoTrans(CommonDictVo companyTypes, OcDraftEntity draft, Map<String, String> map,
                              Supplier<String> field,
                              Consumer<String> setField,
                              Function<OcDraftEntity, Boolean> func) {
        boolean needToTrans = companyTypes.items().stream()
                .anyMatch(item -> map.containsKey(item.key()) || map.containsKey(item.intro()));
        if (!needToTrans) {
            return false;
        }

        // 将私企 进行互相映射 民企
        boolean ans = false;
        String trans = map.get(field.get());
        if (trans != null) {
            setField.accept(trans);
            ans = func.apply(draft);
        }
        if (ans) {
            // 可以进行转换
            ocDraftRepository.save(draft);
        }
        return ans;
    }
}
