package com.git.hui.jobclaw.agents.jobfetch.service;

import com.git.hui.jobclaw.agents.jobfetch.service.model.DraftEntity;
import com.git.hui.jobclaw.agents.jobfetch.service.model.JobInfo;

import java.util.List;

/**
 * 将解析的职位信息，保存到数据库中
 *
 * @author YiHui
 * @date 2026/4/21
 */
public interface JobInfoPersistService {

    SaveRes save(List<JobInfo> jobInfos);

    List<DraftEntity> listToBePublished(int size);

    boolean updateDraft(long id, JobInfo jobInfo);

    /**
     * 将草稿发布到正式库
     *
     * @param draftIds 草稿ID列表
     * @return 发布的职位数量
     */
    int publishDrafts(List<Long> draftIds);

    record SaveRes(int insertCnt, int updateCnt) {
    }

}
