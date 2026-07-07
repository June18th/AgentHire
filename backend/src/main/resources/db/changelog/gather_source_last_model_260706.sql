ALTER TABLE `gather_source`
    ADD COLUMN `last_model` varchar(256) NOT NULL DEFAULT '' COMMENT 'last model used by this source' AFTER `runner_type`;
