package com.git.hui.jobclaw.agents.jobfetch.service.model;

/**
 * ai获取的草稿数据，通常需要进一步进行处理
 *
 * @author YiHui
 * @date 2025/7/14
 */
public interface DraftEntity {
    Long getId();

    String getCompanyName();

    String getPosition();

    String getJobLocation();

    String getRecruitmentType();

    String getRecruitmentTarget();

    String getSalary();

    String getEducation();
}
