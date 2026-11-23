-- ============================================================
-- M67 · 产品偏好表（第 19 张同步表）+ 产品行展示排序列
--
-- 背景（13 项优化需求 #1/#7/#8/#9/#10，2026-11-23 龙哥拍板）：
-- 1. 偏好（启停/默认值/上次使用）从产品行拆出为独立同步表——内置产品
--    （id 1~99，base 单向下发、push 8345 拒）的行不可写，只有独立偏好表
--    才能承载「内置产品也可设默认/启停」；
-- 2. 偏好语义 = 家庭共享：行随 visibleUserIds 下发，家庭内成员可见可改
--    （M16 家庭维度归属放行）；客户端写前查同 product_id 已有行收敛，
--    竞态产生的跨账号重复行由读侧「按 version 取最新」防御；
-- 3. 排序（sort_order）存产品行：产品级共享，后台可维护（含内置行）；
-- 4. bl_products.enabled/last_used_at 旧列保留不删（老版本 App 兼容），
--    新逻辑以本表为准。
--
-- MySQL DDL 隐式提交，不进事务。
-- ============================================================

-- ---------- 产品偏好（同步表，第 19 张） ----------
CREATE TABLE IF NOT EXISTS `bl_product_prefs` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `user_id`           BIGINT(20)     NOT NULL                COMMENT '同步归属（服务端按 token 写入，禁客户端传值）',
    `product_id`        BIGINT(20)     NOT NULL                COMMENT '产品 id（内置 1~99 或自定义雪花）',
    `category`          VARCHAR(32)    NOT NULL                COMMENT '冗余产品分类：FORMULA/SUPPLEMENT/MEDICINE/DIAPER（默认唯一性按它判定，免 join）',
    `enabled`           TINYINT(4)     NOT NULL DEFAULT 1      COMMENT '启用开关：0/1（覆盖产品行展示；内置产品启停只存在这里）',
    `is_default`        TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '默认产品：0/1（家庭内每分类至多一个，客户端收敛）',
    `last_used_at`      BIGINT(20)     DEFAULT NULL            COMMENT '最近被记录使用时刻（epoch millis）',
    `created_at`        BIGINT(20)     DEFAULT NULL            COMMENT 'App 端创建时刻（epoch millis）',
    `updated_at`        BIGINT(20)     DEFAULT NULL            COMMENT 'App 端最后更新时刻（epoch millis）',
    `device_id`         VARCHAR(64)    DEFAULT NULL            COMMENT '来源设备标识',
    `version`           BIGINT(20)     NOT NULL DEFAULT 0      COMMENT '同步版本号（乐观锁）',
    `server_updated_at` DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '服务端同步游标',
    `create_by`         BIGINT(20)     DEFAULT NULL            COMMENT '创建人（审计）',
    `create_time`       DATETIME       DEFAULT NULL            COMMENT '创建时间（审计）',
    `update_by`         BIGINT(20)     DEFAULT NULL            COMMENT '更新人（审计）',
    `update_time`       DATETIME       DEFAULT NULL            COMMENT '更新时间（审计）',
    `deleted`           TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '逻辑删除（同步软删除信号）',
    PRIMARY KEY (`id`),
    -- 单账号维度唯一：同账号多设备收敛到一行；家庭共享下成员改同一行（M16 放行），
    -- 竞态产生的跨账号重复行由客户端读侧「按 version 取最新」防御收敛
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
    KEY `idx_category_default` (`category`, `is_default`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品偏好（同步表，第 19 张；启停/默认/上次使用，家庭共享）';

-- ---------- 产品行展示排序（需求 #7：数字越大越靠前） ----------
ALTER TABLE `bl_products`
    ADD COLUMN `sort_order` INT(11) NOT NULL DEFAULT 0
    COMMENT '展示排序：数字越大越靠前（产品级共享，后台可维护含内置行）'
    AFTER `default_dose`;
