package com.git.hui.jobclaw.gather.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicUpdate;

import java.util.Date;

/**
 * 长期采集来源资产。
 */
@Data
@Accessors(chain = true)
@DynamicUpdate
@Entity(name = "gather_source")
public class GatherSourceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type")
    private Integer type;

    @Column(name = "title")
    private String title;

    @Lob
    @Column(name = "content")
    private String content;

    @Column(name = "source_hash")
    private String sourceHash;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "version")
    private Integer version;

    @Column(name = "owner_type")
    private String ownerType;

    @Column(name = "runner_type")
    private String runnerType;

    @Column(name = "last_model")
    private String lastModel;

    @Column(name = "status")
    private String status;

    @Column(name = "last_task_id")
    private Long lastTaskId;

    @Lob
    @Column(name = "last_result_summary")
    private String lastResultSummary;

    @Column(name = "last_run_time")
    private Date lastRunTime;

    @Column(name = "create_time")
    private Date createTime;

    @Column(name = "update_time")
    private Date updateTime;
}
