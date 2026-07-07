package com.git.hui.jobclaw.application.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@Accessors(chain = true)
@Entity(name = "job_application_event")
public class JobApplicationEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "event_title")
    private String eventTitle;

    @Column(name = "event_time")
    private Date eventTime;

    @Column(name = "event_result")
    private String eventResult;

    @Column(name = "note")
    private String note;

    @Column(name = "create_time")
    private Date createTime;
}
