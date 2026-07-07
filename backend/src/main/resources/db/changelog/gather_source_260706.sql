CREATE TABLE IF NOT EXISTS `gather_source`
(
    `id`                  bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'primary id',
    `type`                tinyint         NOT NULL DEFAULT '0' COMMENT 'source type, same as gather task type',
    `title`               varchar(256)    NOT NULL DEFAULT '' COMMENT 'source title or summary',
    `content`             longtext NULL COMMENT 'source content or stored file path',
    `source_hash`         varchar(64)     NOT NULL DEFAULT '' COMMENT 'normalized source fingerprint',
    `content_hash`        varchar(64)     NOT NULL DEFAULT '' COMMENT 'current content fingerprint',
    `version`             int             NOT NULL DEFAULT '1' COMMENT 'source version',
    `owner_type`          varchar(32)     NOT NULL DEFAULT 'admin' COMMENT 'admin / agent / system',
    `runner_type`         varchar(32)     NOT NULL DEFAULT 'draft_only' COMMENT 'draft_only / agent',
    `last_model`          varchar(256)    NOT NULL DEFAULT '' COMMENT 'last model used by this source',
    `status`              varchar(32)     NOT NULL DEFAULT 'active' COMMENT 'active / paused / archived / invalid',
    `last_task_id`        bigint unsigned NULL DEFAULT NULL COMMENT 'last task id',
    `last_result_summary` longtext NULL COMMENT 'last task result summary',
    `last_run_time`       timestamp NULL DEFAULT NULL COMMENT 'last run time',
    `create_time`         timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    `update_time`         timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_source_hash` (`source_hash`),
    KEY `ix_status_time` (`status`, `update_time`),
    KEY `ix_owner_runner` (`owner_type`, `runner_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='gather source asset';
