package com.git.hui.jobclaw.agents.jobfetch.service;

import com.git.hui.jobclaw.agents.jobfetch.service.model.JobInfo;

import java.util.List;

/**
 * 将解析的职位信息，保存到数据库中
 *
 * @author YiHui
 * @date 2026/4/21
 */
public interface JobInfoSaveService {

    SaveRes save(List<JobInfo> jobInfos);

    record SaveRes(int insertCnt, int updateCnt) {
    }

}
