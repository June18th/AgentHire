-- 新增优惠券
CREATE TABLE `recharge_coupon`
(
    `id`           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `coupon_code`  varchar(64)   NOT NULL DEFAULT '' COMMENT '优惠券CODE',
    `coupon_count` int           NOT NULL DEFAULT '1' COMMENT '优惠券数量',
    `coupon_value` int           NOT NULL DEFAULT '0' COMMENT '优惠金额/折扣',
    `coupon_type`  tinyint       NOT NULL DEFAULT '0' COMMENT '0-减免金额 1-折扣',
    `scope`        tinyint       NOT NULL DEFAULT '999' COMMENT '使用范围：999-全场可用 666-指定用户 0-月卡 1-季卡 2-年卡 3-终身会员',
    `start_time`   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    `end_time`     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '结束时间',
    `extra`        varchar(1024) NOT NULL DEFAULT '{}' COMMENT '扩展信息，json格式',
    `state`        tinyint       NOT NULL DEFAULT '0' COMMENT '状态：-1 删除 0 禁用 1 正常',
    `create_time`  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `ix_coupon_code` (`coupon_code`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4  COMMENT='充值优惠券';

-- 用户充值记录中，需要新增优惠券字段
alter table user_recharge
    add column coupon_code varchar(64) COMMENT '优惠券CODE';


-- 优惠券相关字典
INSERT INTO common_dict
(app, `scope`, dict_key, dict_value, dict_intro, remark, state)
VALUES ('recharge', 0, 'CouponTypeEnum', '0', '金额优惠', '优惠券类型', 1),
       ('recharge', 0, 'CouponTypeEnum', '1', '折扣优惠', '优惠券类型', 1);

INSERT INTO common_dict
(app, `scope`, dict_key, dict_value, dict_intro, remark, state)
VALUES ('recharge', 0, 'CouponScopeEnum', '999', '全场通用', '优惠券适用范围', 1),
    ('recharge', 0, 'CouponScopeEnum', '666', '特定用户', '优惠券适用范围', 1)
;

