CREATE TABLE IF NOT EXISTS `rbac_role`
(
    `id`          bigint unsigned NOT NULL COMMENT '主键ID',
    `role_code`   varchar(64)     NOT NULL DEFAULT '' COMMENT '角色编码',
    `role_name`   varchar(64)     NOT NULL DEFAULT '' COMMENT '角色名称',
    `intro`       varchar(256)    NOT NULL DEFAULT '' COMMENT '角色说明',
    `state`       tinyint         NOT NULL DEFAULT '1' COMMENT '状态: -1 删除 0 禁用 1 正常',
    `create_time` timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC角色表';

CREATE TABLE IF NOT EXISTS `rbac_permission`
(
    `id`              bigint unsigned NOT NULL COMMENT '主键ID',
    `permission_code` varchar(128)    NOT NULL DEFAULT '' COMMENT '权限编码',
    `permission_name` varchar(64)     NOT NULL DEFAULT '' COMMENT '权限名称',
    `resource`        varchar(64)     NOT NULL DEFAULT '' COMMENT '资源',
    `action`          varchar(64)     NOT NULL DEFAULT '' COMMENT '动作',
    `intro`           varchar(256)    NOT NULL DEFAULT '' COMMENT '权限说明',
    `state`           tinyint         NOT NULL DEFAULT '1' COMMENT '状态: -1 删除 0 禁用 1 正常',
    `create_time`     timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC权限表';

CREATE TABLE IF NOT EXISTS `rbac_user_role`
(
    `id`          bigint unsigned NOT NULL COMMENT '主键ID',
    `user_id`     bigint unsigned NOT NULL DEFAULT '0' COMMENT '用户ID',
    `role_code`   varchar(64)     NOT NULL DEFAULT '' COMMENT '角色编码',
    `state`       tinyint         NOT NULL DEFAULT '1' COMMENT '状态: -1 删除 0 禁用 1 正常',
    `create_time` timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_code`),
    KEY `ix_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC用户角色关系表';

CREATE TABLE IF NOT EXISTS `rbac_role_permission`
(
    `id`              bigint unsigned NOT NULL COMMENT '主键ID',
    `role_code`       varchar(64)     NOT NULL DEFAULT '' COMMENT '角色编码',
    `permission_code` varchar(128)    NOT NULL DEFAULT '' COMMENT '权限编码',
    `state`           tinyint         NOT NULL DEFAULT '1' COMMENT '状态: -1 删除 0 禁用 1 正常',
    `create_time`     timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_code`, `permission_code`),
    KEY `ix_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC角色权限关系表';

INSERT IGNORE INTO `rbac_role` (`id`, `role_code`, `role_name`, `intro`, `state`)
VALUES (260705000001, 'JOB_SEEKER', '求职者', '管理简历、查看岗位、投递岗位', 1),
       (260705000002, 'VIP_USER', '会员求职者', '拥有更高额度和高级AI能力的求职者', 1),
       (260705000003, 'RECRUITER', '企业HR', '发布岗位、管理候选人和投递流程', 1),
       (260705000004, 'PLATFORM_ADMIN', '平台管理员', '管理用户、岗位、配置和审核流程', 1),
       (260705000005, 'SUPER_ADMIN', '超级管理员', '拥有平台最高管理权限', 1);

INSERT IGNORE INTO `rbac_permission` (`id`, `permission_code`, `permission_name`, `resource`, `action`, `intro`, `state`)
VALUES (260705010001, 'user:profile:read', '查看个人资料', 'user_profile', 'read', '查看自己的用户资料', 1),
       (260705010002, 'user:profile:update', '更新个人资料', 'user_profile', 'update', '更新自己的用户资料', 1),
       (260705010003, 'resume:manage', '管理简历', 'resume', 'manage', '创建、编辑、上传和优化简历', 1),
       (260705010004, 'job:read', '查看岗位', 'job', 'read', '查看公开岗位', 1),
       (260705010005, 'job:recommend', '岗位推荐', 'job', 'recommend', '获取个性化岗位推荐', 1),
       (260705010006, 'company:manage', '管理企业', 'company', 'manage', '维护企业资料', 1),
       (260705010007, 'job:publish', '发布岗位', 'job', 'publish', '发布和维护企业岗位', 1),
       (260705010008, 'candidate:manage', '管理候选人', 'candidate', 'manage', '查看简历和处理投递状态', 1),
       (260705010009, 'admin:user:manage', '管理用户', 'admin_user', 'manage', '平台用户管理', 1),
       (260705010010, 'admin:content:manage', '管理内容', 'admin_content', 'manage', '岗位和采集内容审核管理', 1),
       (260705010011, 'admin:system:manage', '管理系统', 'admin_system', 'manage', '系统配置和字典管理', 1),
       (260705010012, 'ai:audit:read', '查看AI审计', 'ai_audit', 'read', '查看大模型调用和Agent执行审计', 1);

INSERT IGNORE INTO `rbac_role_permission` (`id`, `role_code`, `permission_code`, `state`)
VALUES (260705020001, 'JOB_SEEKER', 'user:profile:read', 1),
       (260705020002, 'JOB_SEEKER', 'user:profile:update', 1),
       (260705020003, 'JOB_SEEKER', 'resume:manage', 1),
       (260705020004, 'JOB_SEEKER', 'job:read', 1),
       (260705020005, 'JOB_SEEKER', 'job:recommend', 1),
       (260705020006, 'VIP_USER', 'job:recommend', 1),
       (260705020007, 'VIP_USER', 'ai:audit:read', 1),
       (260705020008, 'RECRUITER', 'company:manage', 1),
       (260705020009, 'RECRUITER', 'job:publish', 1),
       (260705020010, 'RECRUITER', 'candidate:manage', 1),
       (260705020011, 'PLATFORM_ADMIN', 'admin:user:manage', 1),
       (260705020012, 'PLATFORM_ADMIN', 'admin:content:manage', 1),
       (260705020013, 'PLATFORM_ADMIN', 'admin:system:manage', 1),
       (260705020014, 'PLATFORM_ADMIN', 'ai:audit:read', 1),
       (260705020015, 'SUPER_ADMIN', 'admin:user:manage', 1),
       (260705020016, 'SUPER_ADMIN', 'admin:content:manage', 1),
       (260705020017, 'SUPER_ADMIN', 'admin:system:manage', 1),
       (260705020018, 'SUPER_ADMIN', 'ai:audit:read', 1);

INSERT IGNORE INTO `rbac_user_role` (`id`, `user_id`, `role_code`, `state`)
VALUES (260705030001, 1, 'JOB_SEEKER', 1),
       (260705030002, 2, 'PLATFORM_ADMIN', 1);
