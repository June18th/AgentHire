ALTER TABLE `job_application`
    ADD COLUMN `company_type` varchar(128) NOT NULL DEFAULT '' COMMENT 'company type' AFTER `apply_url`;
