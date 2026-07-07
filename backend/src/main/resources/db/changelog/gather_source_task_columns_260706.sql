ALTER TABLE `gather_task`
    ADD COLUMN `source_id` bigint unsigned NULL DEFAULT NULL COMMENT 'gather source id',
    ADD COLUMN `source_version` int NULL DEFAULT NULL COMMENT 'source version used by this task',
    ADD COLUMN `runner_type` varchar(32) NOT NULL DEFAULT '' COMMENT 'draft_only / agent';

CREATE INDEX `ix_gather_task_source` ON `gather_task` (`source_id`, `create_time`);
