CREATE TABLE IF NOT EXISTS `job_application`
(
    `id`                bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'primary id',
    `user_id`           bigint unsigned NOT NULL DEFAULT '0' COMMENT 'user id',
    `job_id`            bigint unsigned NULL DEFAULT NULL COMMENT 'job id from oc_info',
    `company_name`      varchar(128)    NOT NULL DEFAULT '' COMMENT 'company name snapshot',
    `position`          varchar(512)    NOT NULL DEFAULT '' COMMENT 'position snapshot',
    `apply_url`         varchar(1024)   NOT NULL DEFAULT '' COMMENT 'application url',
    `current_status`    varchar(32)     NOT NULL DEFAULT 'INTERESTED' COMMENT 'current application status',
    `source`            varchar(64)     NOT NULL DEFAULT '' COMMENT 'record source',
    `priority`          tinyint         NOT NULL DEFAULT '0' COMMENT 'priority: 0 normal, 1 high, 2 important',
    `deadline`          varchar(128)    NOT NULL DEFAULT '' COMMENT 'application deadline',
    `submitted_at`      timestamp NULL DEFAULT NULL COMMENT 'submitted time',
    `next_follow_up_at` timestamp NULL DEFAULT NULL COMMENT 'next follow-up time',
    `remark`            varchar(1024)   NOT NULL DEFAULT '' COMMENT 'remark',
    `state`             tinyint         NOT NULL DEFAULT '1' COMMENT 'state: -1 deleted, 1 normal',
    `create_time`       timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    `update_time`       timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`id`),
    KEY `ix_user_status_time` (`user_id`, `current_status`, `update_time`),
    KEY `ix_user_job` (`user_id`, `job_id`),
    KEY `ix_next_follow_up` (`user_id`, `next_follow_up_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='job application record';

CREATE TABLE IF NOT EXISTS `job_application_status_log`
(
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'primary id',
    `application_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT 'application id',
    `user_id`        bigint unsigned NOT NULL DEFAULT '0' COMMENT 'user id',
    `from_status`    varchar(32) NULL DEFAULT NULL COMMENT 'from status',
    `to_status`      varchar(32)     NOT NULL DEFAULT '' COMMENT 'to status',
    `operator_type`  varchar(32)     NOT NULL DEFAULT 'USER' COMMENT 'operator type',
    `operator_id`    bigint unsigned NOT NULL DEFAULT '0' COMMENT 'operator id',
    `reason`         varchar(512)    NOT NULL DEFAULT '' COMMENT 'change reason',
    `event_time`     timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'event time',
    PRIMARY KEY (`id`),
    KEY `ix_application_time` (`application_id`, `event_time`),
    KEY `ix_user_time` (`user_id`, `event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='job application status log';

CREATE TABLE IF NOT EXISTS `job_application_event`
(
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'primary id',
    `application_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT 'application id',
    `user_id`        bigint unsigned NOT NULL DEFAULT '0' COMMENT 'user id',
    `event_type`     varchar(32)     NOT NULL DEFAULT '' COMMENT 'event type',
    `event_title`    varchar(128)    NOT NULL DEFAULT '' COMMENT 'event title',
    `event_time`     timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'event time',
    `event_result`   varchar(64)     NOT NULL DEFAULT '' COMMENT 'event result',
    `note`           text NULL DEFAULT NULL COMMENT 'event note',
    `create_time`    timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    PRIMARY KEY (`id`),
    KEY `ix_application_time` (`application_id`, `event_time`),
    KEY `ix_user_time` (`user_id`, `event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='job application event';
