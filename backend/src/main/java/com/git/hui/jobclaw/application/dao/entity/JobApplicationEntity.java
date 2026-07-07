package com.git.hui.jobclaw.application.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicUpdate;

import java.util.Date;

@Data
@Accessors(chain = true)
@DynamicUpdate
@Entity(name = "job_application")
public class JobApplicationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "position")
    private String position;

    @Column(name = "apply_url")
    private String applyUrl;

    @Column(name = "company_type")
    private String companyType;

    @Column(name = "current_status")
    private String currentStatus;

    @Column(name = "source")
    private String source;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "deadline")
    private String deadline;

    @Column(name = "submitted_at")
    private Date submittedAt;

    @Column(name = "next_follow_up_at")
    private Date nextFollowUpAt;

    @Column(name = "remark")
    private String remark;

    @Column(name = "state")
    private Integer state;

    @Column(name = "create_time")
    private Date createTime;

    @Column(name = "update_time")
    private Date updateTime;
}
