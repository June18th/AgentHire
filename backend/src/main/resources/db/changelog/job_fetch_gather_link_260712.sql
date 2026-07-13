ALTER TABLE job_fetch_task ADD COLUMN gather_task_id BIGINT NULL COMMENT '关联 gather_task.id';
ALTER TABLE job_fetch_task ADD COLUMN gather_source_id BIGINT NULL COMMENT '关联 gather_source.id';
