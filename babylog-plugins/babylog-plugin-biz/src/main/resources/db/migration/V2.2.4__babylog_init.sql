-- ============================================================
-- BabyLog 业务插件初始建表（V2.2.4，M4 会话）
--
-- 口径（2026-11-11 拍板）：
-- 1. 18 张同步表与 App 端 Room 18 表一一对应（children/feedings/.../reminders），
--    业务时间列一律 BIGINT epoch millis（与 Room 直接对齐）；
--    审计四件套（create_by/create_time/update_by/update_time）为服务端管理口径，两层并存。
-- 2. 主键统一雪花 BIGINT（App 端 V2.0 改造时迁离 UUID 字符串主键）。
-- 3. 同步表额外两列：version（乐观锁/同步版本号）+ server_updated_at（服务端时钟拉取游标，
--    数据库自动维护）；deleted 与 App 端软删除标记同名同义。
-- 4. 引用列（child_id/member_id/product_id/source_id/owner_id）BIGINT；
--    哨兵口径：bl_reminders.child_id=0 表示聚合行（App 端原 "all-children" 字符串）；
--    bl_products 内置行 id 取 1~99 预留小数值段（雪花大数天然不撞），device_id='builtin'。
-- 5. 服务端专属运营表：bl_family/bl_device/bl_feedback/bl_app_version/bl_vaccine_schedule/bl_ai_quota。
-- 6. 不加 tenant_id（多租户不启用；若误在租户上下文访问会报 Unknown column，属预期防护）。
-- ============================================================

-- ---------- 宝宝（同步） ----------
CREATE TABLE IF NOT EXISTS `bl_children` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `family_id`         BIGINT(20)     DEFAULT NULL            COMMENT '所属家庭（V2.0 为 null，V2.1 点亮）',
    `owner_user_id`     BIGINT(20)     NOT NULL                COMMENT '归属用户（V2.0 单用户模型）',
    `name`              VARCHAR(50)    NOT NULL                COMMENT '昵称',
    `gender`            VARCHAR(32)    NOT NULL                COMMENT '性别：MALE/FEMALE/UNKNOWN',
    `birthday`          BIGINT(20)     NOT NULL                COMMENT '生日（epoch millis）',
    `is_premature`      TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '是否早产：0 否 1 是',
    `gestational_weeks` INT(11)        DEFAULT NULL            COMMENT '早产孕周',
    `birth_weight_g`    INT(11)        DEFAULT NULL            COMMENT '出生体重（克）',
    `birth_height_cm`   DOUBLE         DEFAULT NULL            COMMENT '出生身高（cm）',
    `avatar_path`       VARCHAR(255)   DEFAULT NULL            COMMENT '头像文件 key',
    `note`              VARCHAR(500)   DEFAULT NULL            COMMENT '备注',
    `archived`          TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '归档（≠删除）：0 否 1 是',
    `created_at`        BIGINT(20)     DEFAULT NULL            COMMENT 'App 端创建时刻（epoch millis）',
    `updated_at`        BIGINT(20)     DEFAULT NULL            COMMENT 'App 端最后更新时刻（epoch millis）',
    `device_id`         VARCHAR(64)    DEFAULT NULL            COMMENT '来源设备标识',
    `version`           BIGINT(20)     NOT NULL DEFAULT 0      COMMENT '同步版本号（乐观锁，每次写+1）',
    `server_updated_at` DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '服务端同步游标（since 基准，自动维护）',
    `create_by`         BIGINT(20)     DEFAULT NULL            COMMENT '创建人（审计）',
    `create_time`       DATETIME       DEFAULT NULL            COMMENT '创建时间（审计）',
    `update_by`         BIGINT(20)     DEFAULT NULL            COMMENT '更新人（审计）',
    `update_time`       DATETIME       DEFAULT NULL            COMMENT '更新时间（审计）',
    `deleted`           TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 1 删除（同步软删除信号）',
    PRIMARY KEY (`id`),
    KEY `idx_birthday` (`birthday`),
    KEY `idx_family` (`family_id`),
    KEY `idx_owner_user` (`owner_user_id`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宝宝（同步表，Room children 对齐）';

-- ---------- 喂养（同步） ----------
CREATE TABLE IF NOT EXISTS `bl_feedings` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `child_id`          BIGINT(20)     NOT NULL                COMMENT '宝宝 id',
    `type`              VARCHAR(32)    NOT NULL                COMMENT '类型：BOTTLE/BREAST_LEFT/BREAST_RIGHT/SOLID/WATER',
    `amount_ml`         INT(11)        DEFAULT NULL            COMMENT '数量（奶瓶=毫升、辅食=份/克/毫升）',
    `amount_unit`       VARCHAR(32)    DEFAULT NULL            COMMENT '数量单位：ML/GRAM/PORTION',
    `duration_s`        INT(11)        DEFAULT NULL            COMMENT '母乳单侧时长（秒）',
    `product_id`        BIGINT(20)     DEFAULT NULL            COMMENT '产品档案 id（bl_products.id）',
    `brand_snapshot`    VARCHAR(100)   DEFAULT NULL            COMMENT '品牌快照',
    `spec_snapshot`     VARCHAR(100)   DEFAULT NULL            COMMENT '规格快照',
    `note`              VARCHAR(500)   DEFAULT NULL            COMMENT '备注',
    `started_at`        BIGINT(20)     NOT NULL                COMMENT '发生时刻（epoch millis）',
    `created_by`        VARCHAR(50)    DEFAULT NULL            COMMENT '谁记的（成员显示名，业务口径）',
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
    KEY `idx_child_started` (`child_id`, `started_at`),
    KEY `idx_started` (`started_at`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='喂养记录（同步表，Room feedings 对齐）';

-- ---------- 尿布（同步） ----------
CREATE TABLE IF NOT EXISTS `bl_diapers` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `child_id`          BIGINT(20)     NOT NULL                COMMENT '宝宝 id',
    `type`              VARCHAR(32)    NOT NULL                COMMENT '类型：WET/SOILED/BOTH',
    `consistency`       VARCHAR(32)    DEFAULT NULL            COMMENT '大便性状（枚举 name）',
    `color`             VARCHAR(32)    DEFAULT NULL            COMMENT '大便颜色（枚举 name）',
    `started_at`        BIGINT(20)     NOT NULL                COMMENT '发生时刻（epoch millis）',
    `created_by`        VARCHAR(50)    DEFAULT NULL            COMMENT '谁记的（成员显示名）',
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
    KEY `idx_child_started` (`child_id`, `started_at`),
    KEY `idx_started` (`started_at`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='尿布记录（同步表，Room diapers 对齐）';

-- ---------- 睡眠（同步） ----------
CREATE TABLE IF NOT EXISTS `bl_sleeps` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `child_id`          BIGINT(20)     NOT NULL                COMMENT '宝宝 id',
    `started_at`        BIGINT(20)     NOT NULL                COMMENT '入睡时刻（epoch millis）',
    `ended_at`          BIGINT(20)     DEFAULT NULL            COMMENT '醒来时刻（epoch millis），null=进行中',
    `place`             VARCHAR(32)    DEFAULT NULL            COMMENT '睡觉地点（枚举 name）',
    `created_by`        VARCHAR(50)    DEFAULT NULL            COMMENT '谁记的（成员显示名）',
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
    KEY `idx_child_started` (`child_id`, `started_at`),
    KEY `idx_started` (`started_at`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='睡眠记录（同步表，Room sleeps 对齐）';

-- ---------- 记录类型开关覆盖（同步） ----------
CREATE TABLE IF NOT EXISTS `bl_record_type_settings` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `child_id`          BIGINT(20)     NOT NULL                COMMENT '宝宝 id',
    `record_type`       VARCHAR(32)    NOT NULL                COMMENT '记录类型（App 端 RecordType 枚举 name）',
    `enabled`           TINYINT(4)     NOT NULL DEFAULT 1      COMMENT '启用开关：0 用户显式关闭 1 启用',
    `start_age_days`    INT(11)        DEFAULT NULL            COMMENT '自定义起始月龄（天），null=用默认',
    `end_age_days`      INT(11)        DEFAULT NULL            COMMENT '自定义截止月龄（天），null=用默认',
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
    UNIQUE KEY `uk_child_record` (`child_id`, `record_type`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记录类型开关覆盖（同步表，Room record_type_settings 对齐）';

-- ---------- 家庭成员（同步） ----------
CREATE TABLE IF NOT EXISTS `bl_family_members` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `family_id`         BIGINT(20)     NOT NULL                COMMENT '家庭 id（bl_family.id）',
    `user_id`           BIGINT(20)     NOT NULL                COMMENT '用户 id（sys_user.id）',
    `user_type`         VARCHAR(16)    NOT NULL                COMMENT '用户来源：APP/WX_MINI',
    `display_name`      VARCHAR(50)    NOT NULL                COMMENT '成员显示名',
    `role`              VARCHAR(32)    NOT NULL                COMMENT '角色：OWNER/CAREGIVER/VIEWER',
    `status`            VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INVITED/REMOVED',
    `joined_at`         BIGINT(20)     NOT NULL                COMMENT '加入时刻（epoch millis）',
    `created_at`        BIGINT(20)     DEFAULT NULL            COMMENT 'App 端创建时刻（epoch millis）',
    `updated_at`        BIGINT(20)     DEFAULT NULL            COMMENT 'App 端最后更新时刻（epoch millis）',
    `device_id`         VARCHAR(64)    DEFAULT NULL            COMMENT '设备标识（一列两用，沿用 App 端语义）',
    `version`           BIGINT(20)     NOT NULL DEFAULT 0      COMMENT '同步版本号（乐观锁）',
    `server_updated_at` DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '服务端同步游标',
    `create_by`         BIGINT(20)     DEFAULT NULL            COMMENT '创建人（审计）',
    `create_time`       DATETIME       DEFAULT NULL            COMMENT '创建时间（审计）',
    `update_by`         BIGINT(20)     DEFAULT NULL            COMMENT '更新人（审计）',
    `update_time`       DATETIME       DEFAULT NULL            COMMENT '更新时间（审计）',
    `deleted`           TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '逻辑删除（同步软删除信号）',
    PRIMARY KEY (`id`),
    KEY `idx_family` (`family_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭成员（同步表，Room family_members 对齐）';

-- ---------- 成员分类权限（同步） ----------
CREATE TABLE IF NOT EXISTS `bl_member_permissions` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `member_id`         BIGINT(20)     NOT NULL                COMMENT '成员 id（bl_family_members.id）',
    `child_id`          BIGINT(20)     NOT NULL                COMMENT '宝宝 id',
    `category`          VARCHAR(32)    NOT NULL                COMMENT '分类：FEEDING/CARE/HEALTH/MEDICAL',
    `can_read`          TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '可读：0/1',
    `can_write`         TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '可写：0/1',
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
    UNIQUE KEY `uk_member_child_category` (`member_id`, `child_id`, `category`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成员分类权限（同步表，Room member_permissions 对齐）';

-- ---------- 产品库（同步，内置+自定义混合） ----------
CREATE TABLE IF NOT EXISTS `bl_products` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（内置行取 1~99 预留段，其余雪花 ID）',
    `category`          VARCHAR(32)    NOT NULL                COMMENT '分类：FORMULA/SUPPLEMENT/MEDICINE/DIAPER',
    `brand`             VARCHAR(100)   NOT NULL                COMMENT '品牌',
    `name`              VARCHAR(50)    NOT NULL                COMMENT '品名',
    `spec`              VARCHAR(100)   DEFAULT NULL            COMMENT '规格',
    `default_dose`      VARCHAR(100)   DEFAULT NULL            COMMENT '默认剂量（文本）',
    `last_used_at`      BIGINT(20)     DEFAULT NULL            COMMENT '最近被记录使用时刻（epoch millis）',
    `enabled`           TINYINT(4)     NOT NULL DEFAULT 1      COMMENT '启用开关：0/1',
    `is_builtin`        TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '内置产品标记：0/1',
    `note`              VARCHAR(500)   DEFAULT NULL            COMMENT '备注',
    `icon_path`         VARCHAR(255)   DEFAULT NULL            COMMENT '图标（内置行=file_id，自定义行=App 本地相对路径）',
    `created_at`        BIGINT(20)     DEFAULT NULL            COMMENT 'App 端创建时刻（epoch millis）',
    `updated_at`        BIGINT(20)     DEFAULT NULL            COMMENT 'App 端最后更新时刻（epoch millis）',
    `device_id`         VARCHAR(64)    DEFAULT NULL            COMMENT '来源设备标识（内置行固定 builtin）',
    `version`           BIGINT(20)     NOT NULL DEFAULT 0      COMMENT '同步版本号（乐观锁）',
    `server_updated_at` DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '服务端同步游标',
    `create_by`         BIGINT(20)     DEFAULT NULL            COMMENT '创建人（审计）',
    `create_time`       DATETIME       DEFAULT NULL            COMMENT '创建时间（审计）',
    `update_by`         BIGINT(20)     DEFAULT NULL            COMMENT '更新人（审计）',
    `update_time`       DATETIME       DEFAULT NULL            COMMENT '更新时间（审计）',
    `deleted`           TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '逻辑删除（同步软删除信号）',
    PRIMARY KEY (`id`),
    KEY `idx_category_last_used` (`category`, `last_used_at`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品库（同步表，Room products 对齐；内置行后台维护下发）';

-- ---------- 测量（同步） ----------
CREATE TABLE IF NOT EXISTS `bl_measurements` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `child_id`          BIGINT(20)     NOT NULL                COMMENT '宝宝 id',
    `type`              VARCHAR(32)    NOT NULL                COMMENT '类型：WEIGHT/HEIGHT/HEAD/TEMP/JAUNDICE',
    `value`             DOUBLE         DEFAULT NULL            COMMENT '数值（黄疸可只记范围故可空）',
    `method`            VARCHAR(32)    DEFAULT NULL            COMMENT '测量方式（体温部位/黄疸范围 name）',
    `measured_at`       BIGINT(20)     NOT NULL                COMMENT '测量时刻（epoch millis）',
    `note`              VARCHAR(500)   DEFAULT NULL            COMMENT '备注',
    `created_by`        VARCHAR(50)    DEFAULT NULL            COMMENT '谁记的（成员显示名）',
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
    KEY `idx_child_measured` (`child_id`, `measured_at`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测量记录（同步表，Room measurements 对齐）';

-- ---------- 用药/补剂（同步） ----------
CREATE TABLE IF NOT EXISTS `bl_medications` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `child_id`          BIGINT(20)     NOT NULL                COMMENT '宝宝 id',
    `category`          VARCHAR(32)    NOT NULL                COMMENT '类别：SUPPLEMENT/MEDICINE',
    `name`              VARCHAR(100)   NOT NULL                COMMENT '药名/补剂名',
    `product_id`        BIGINT(20)     DEFAULT NULL            COMMENT '产品档案 id（bl_products.id）',
    `brand_snapshot`    VARCHAR(100)   DEFAULT NULL            COMMENT '品牌快照',
    `spec_snapshot`     VARCHAR(100)   DEFAULT NULL            COMMENT '规格快照',
    `dose`              VARCHAR(100)   DEFAULT NULL            COMMENT '剂量（文本）',
    `unit`              VARCHAR(32)    DEFAULT NULL            COMMENT '单位',
    `route`             VARCHAR(32)    DEFAULT NULL            COMMENT '途径（口服/外用/滴入）',
    `started_at`        BIGINT(20)     NOT NULL                COMMENT '开始时刻（epoch millis）',
    `ended_at`          BIGINT(20)     DEFAULT NULL            COMMENT '疗程结束时刻（epoch millis），null=还在用',
    `note`              VARCHAR(500)   DEFAULT NULL            COMMENT '备注',
    `created_by`        VARCHAR(50)    DEFAULT NULL            COMMENT '谁记的（成员显示名）',
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
    KEY `idx_child_started` (`child_id`, `started_at`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用药/补剂记录（同步表，Room medications 对齐）';

-- ---------- 洗澡（同步） ----------
CREATE TABLE IF NOT EXISTS `bl_baths` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `child_id`          BIGINT(20)     NOT NULL                COMMENT '宝宝 id',
    `started_at`        BIGINT(20)     NOT NULL                COMMENT '发生时刻（epoch millis）',
    `water_temp_c`      DOUBLE         DEFAULT NULL            COMMENT '水温（℃）',
    `hair_washed`       TINYINT(4)     DEFAULT NULL            COMMENT '是否洗头（可空）',
    `cord_status`       VARCHAR(32)    DEFAULT NULL            COMMENT '脐带状态：INTACT/CLEANED/FALLEN_OFF',
    `note`              VARCHAR(500)   DEFAULT NULL            COMMENT '备注',
    `created_by`        VARCHAR(50)    DEFAULT NULL            COMMENT '谁记的（成员显示名）',
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
    KEY `idx_child_started` (`child_id`, `started_at`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='洗澡记录（同步表，Room baths 对齐）';

-- ---------- 吐奶（同步） ----------
CREATE TABLE IF NOT EXISTS `bl_spits` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `child_id`          BIGINT(20)     NOT NULL                COMMENT '宝宝 id',
    `started_at`        BIGINT(20)     NOT NULL                COMMENT '发生时刻（epoch millis）',
    `amount`            VARCHAR(32)    DEFAULT NULL            COMMENT '量：SMALL/MEDIUM/LARGE/PROJECTILE',
    `timing`            VARCHAR(32)    DEFAULT NULL            COMMENT '时机：RIGHT_AFTER/AFTER_BURP/WITHIN_HOUR/NIGHT',
    `note`              VARCHAR(500)   DEFAULT NULL            COMMENT '备注',
    `created_by`        VARCHAR(50)    DEFAULT NULL            COMMENT '谁记的（成员显示名）',
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
    KEY `idx_child_started` (`child_id`, `started_at`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='吐奶记录（同步表，Room spits 对齐）';

-- ---------- 泵奶（同步） ----------
CREATE TABLE IF NOT EXISTS `bl_pump_sessions` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `child_id`          BIGINT(20)     NOT NULL                COMMENT '宝宝 id',
    `started_at`        BIGINT(20)     NOT NULL                COMMENT '发生时刻（epoch millis）',
    `amount_ml`         INT(11)        DEFAULT NULL            COMMENT '泵出奶量（毫升）',
    `side`              VARCHAR(32)    DEFAULT NULL            COMMENT '侧别：LEFT/RIGHT/BOTH',
    `storage`           VARCHAR(32)    DEFAULT NULL            COMMENT '储存方式：ROOM/FRIDGE/FREEZER',
    `expires_at`        BIGINT(20)     DEFAULT NULL            COMMENT '过期时刻（epoch millis，记录时按储存方式算好）',
    `note`              VARCHAR(500)   DEFAULT NULL            COMMENT '备注',
    `created_by`        VARCHAR(50)    DEFAULT NULL            COMMENT '谁记的（成员显示名）',
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
    KEY `idx_child_started` (`child_id`, `started_at`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='泵奶记录（同步表，Room pump_sessions 对齐）';

-- ---------- 疫苗接种（同步，用户实例） ----------
CREATE TABLE IF NOT EXISTS `bl_vaccinations` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `child_id`          BIGINT(20)     NOT NULL                COMMENT '宝宝 id',
    `vaccine_name`      VARCHAR(100)   NOT NULL                COMMENT '疫苗名称',
    `dose_no`           INT(11)        DEFAULT NULL            COMMENT '第几剂',
    `scheduled_date`    BIGINT(20)     NOT NULL                COMMENT '计划日期（epoch millis）',
    `actual_date`       BIGINT(20)     DEFAULT NULL            COMMENT '实际日期（epoch millis）',
    `status`            VARCHAR(32)    NOT NULL DEFAULT 'PLANNED' COMMENT '状态：PLANNED/DONE/SKIPPED',
    `institution`       VARCHAR(100)   DEFAULT NULL            COMMENT '接种机构',
    `batch_no`          VARCHAR(50)    DEFAULT NULL            COMMENT '批号',
    `reaction`          VARCHAR(1000)  DEFAULT NULL            COMMENT '接种后反应',
    `next_date`         BIGINT(20)     DEFAULT NULL            COMMENT '下次日期（epoch millis）',
    `note`              VARCHAR(500)   DEFAULT NULL            COMMENT '备注',
    `created_by`        VARCHAR(50)    DEFAULT NULL            COMMENT '谁记的（成员显示名）',
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
    KEY `idx_child_scheduled` (`child_id`, `scheduled_date`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='疫苗接种记录（同步表，Room vaccinations 对齐；区别于 bl_vaccine_schedule 运营表）';

-- ---------- 随访/复查（同步） ----------
CREATE TABLE IF NOT EXISTS `bl_follow_ups` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `child_id`          BIGINT(20)     NOT NULL                COMMENT '宝宝 id',
    `type`              VARCHAR(32)    NOT NULL                COMMENT '类型：COMMUNITY_7D/R42/HEARING/HEEL_BLOOD/CUSTOM',
    `scheduled_date`    BIGINT(20)     NOT NULL                COMMENT '计划日期（epoch millis）',
    `actual_date`       BIGINT(20)     DEFAULT NULL            COMMENT '实际日期（epoch millis）',
    `institution`       VARCHAR(100)   DEFAULT NULL            COMMENT '机构',
    `result`            VARCHAR(1000)  DEFAULT NULL            COMMENT '结果',
    `next_date`         BIGINT(20)     DEFAULT NULL            COMMENT '下次日期（epoch millis）',
    `note`              VARCHAR(500)   DEFAULT NULL            COMMENT '备注',
    `created_by`        VARCHAR(50)    DEFAULT NULL            COMMENT '谁记的（成员显示名）',
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
    KEY `idx_child_scheduled` (`child_id`, `scheduled_date`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='随访/复查记录（同步表，Room follow_ups 对齐）';

-- ---------- 就医（同步） ----------
CREATE TABLE IF NOT EXISTS `bl_visits` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `child_id`          BIGINT(20)     NOT NULL                COMMENT '宝宝 id',
    `visited_at`        BIGINT(20)     NOT NULL                COMMENT '就诊时刻（epoch millis）',
    `hospital`          VARCHAR(100)   DEFAULT NULL            COMMENT '医院',
    `department`        VARCHAR(50)    DEFAULT NULL            COMMENT '科室',
    `doctor`            VARCHAR(50)    DEFAULT NULL            COMMENT '医生',
    `chief_complaint`   VARCHAR(1000)  DEFAULT NULL            COMMENT '主诉',
    `diagnosis`         VARCHAR(1000)  DEFAULT NULL            COMMENT '诊断',
    `prescription`      VARCHAR(1000)  DEFAULT NULL            COMMENT '处方',
    `cost`              DOUBLE         DEFAULT NULL            COMMENT '费用（元）',
    `note`              VARCHAR(500)   DEFAULT NULL            COMMENT '备注',
    `created_by`        VARCHAR(50)    DEFAULT NULL            COMMENT '谁记的（成员显示名）',
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
    KEY `idx_child_visited` (`child_id`, `visited_at`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='就医记录（同步表，Room visits 对齐）';

-- ---------- 附件（同步，App 端无 updated_at/created_by） ----------
CREATE TABLE IF NOT EXISTS `bl_attachments` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `owner_type`        VARCHAR(32)    NOT NULL                COMMENT '宿主类型：VISIT/VACCINATION/MEASUREMENT',
    `owner_id`          BIGINT(20)     NOT NULL                COMMENT '宿主记录行 id',
    `local_path`        VARCHAR(255)   DEFAULT NULL            COMMENT 'App 端本地路径（V1 不上传时保留）',
    `remote_key`        VARCHAR(255)   DEFAULT NULL            COMMENT '远端文件 key（plugin-file 上传后回填）',
    `mime`              VARCHAR(64)    DEFAULT NULL            COMMENT 'MIME 类型',
    `created_at`        BIGINT(20)     DEFAULT NULL            COMMENT 'App 端创建时刻（epoch millis）',
    `device_id`         VARCHAR(64)    DEFAULT NULL            COMMENT '来源设备标识',
    `version`           BIGINT(20)     NOT NULL DEFAULT 0      COMMENT '同步版本号（乐观锁）',
    `server_updated_at` DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '服务端同步游标',
    `create_by`         BIGINT(20)     DEFAULT NULL            COMMENT '创建人（审计）',
    `create_time`       DATETIME       DEFAULT NULL            COMMENT '创建时间（审计）',
    `update_by`         BIGINT(20)     DEFAULT NULL            COMMENT '更新人（审计）',
    `update_time`       DATETIME       DEFAULT NULL            COMMENT '更新时间（审计）',
    `deleted`           TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '逻辑删除（同步软删除信号）',
    PRIMARY KEY (`id`),
    KEY `idx_owner` (`owner_type`, `owner_id`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件（同步表，Room attachments 对齐）';

-- ---------- 提醒（同步，含哨兵口径） ----------
CREATE TABLE IF NOT EXISTS `bl_reminders` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `child_id`          BIGINT(20)     NOT NULL DEFAULT 0      COMMENT '宝宝 id（0=聚合全部宝宝哨兵，App 端原 all-children）',
    `source_type`       VARCHAR(32)    NOT NULL                COMMENT '来源类型：VACCINATION/FOLLOW_UP/VISIT/MEDICATION/PUMP_STORAGE/CUSTOM',
    `source_id`         VARCHAR(128)   NOT NULL                COMMENT '来源标识（编码字符串，如 plan:{childId}:{疫苗名}:{剂次}、daily-supplement）',
    `title`             VARCHAR(100)   NOT NULL                COMMENT '标题',
    `body`              VARCHAR(500)   DEFAULT NULL            COMMENT '内容',
    `trigger_at`        BIGINT(20)     NOT NULL                COMMENT '触发时刻（epoch millis，DAILY 行存下一次）',
    `repeat_rule`       VARCHAR(32)    NOT NULL DEFAULT 'NONE' COMMENT '重复规则：NONE/DAILY',
    `enabled`           TINYINT(4)     NOT NULL DEFAULT 1      COMMENT '用户总开关：0/1',
    `last_fired_at`     BIGINT(20)     DEFAULT NULL            COMMENT '上次实际送达时刻（epoch millis）',
    `note`              VARCHAR(500)   DEFAULT NULL            COMMENT '备注',
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
    KEY `idx_child_trigger` (`child_id`, `trigger_at`),
    UNIQUE KEY `uk_source_trigger` (`source_type`, `source_id`, `trigger_at`),
    KEY `idx_sync_cursor` (`server_updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提醒（同步表，Room reminders 对齐）';

-- ---------- 家庭（服务端专属，App 端无对应表） ----------
CREATE TABLE IF NOT EXISTS `bl_family` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `name`              VARCHAR(50)    NOT NULL                COMMENT '家庭名称',
    `invite_code`       VARCHAR(16)    NOT NULL                COMMENT '邀请码（创建时生成，可重置）',
    `owner_user_id`     BIGINT(20)     NOT NULL                COMMENT '创建者用户 id（sys_user.id）',
    `status`            VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISSOLVED',
    `create_by`         BIGINT(20)     DEFAULT NULL            COMMENT '创建人（审计）',
    `create_time`       DATETIME       DEFAULT NULL            COMMENT '创建时间（审计）',
    `update_by`         BIGINT(20)     DEFAULT NULL            COMMENT '更新人（审计）',
    `update_time`       DATETIME       DEFAULT NULL            COMMENT '更新时间（审计）',
    `deleted`           TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 1 删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_invite_code` (`invite_code`),
    KEY `idx_owner` (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭（服务端专属，V2.1 点亮）';

-- ---------- 设备（服务端专属） ----------
CREATE TABLE IF NOT EXISTS `bl_device` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `user_id`           BIGINT(20)     NOT NULL                COMMENT '归属用户 id',
    `device_name`       VARCHAR(100)   DEFAULT NULL            COMMENT '设备名（App 端上报）',
    `platform`          VARCHAR(16)    NOT NULL                COMMENT '平台：ANDROID/IOS',
    `app_version`       VARCHAR(32)    DEFAULT NULL            COMMENT 'App 版本（versionName）',
    `push_token`        VARCHAR(255)   DEFAULT NULL            COMMENT '推送 token（V2.0 可空）',
    `status`            VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/KICKED',
    `last_active_at`    DATETIME       DEFAULT NULL            COMMENT '最后活跃时刻',
    `create_by`         BIGINT(20)     DEFAULT NULL            COMMENT '创建人（审计）',
    `create_time`       DATETIME       DEFAULT NULL            COMMENT '创建时间（审计）',
    `update_by`         BIGINT(20)     DEFAULT NULL            COMMENT '更新人（审计）',
    `update_time`       DATETIME       DEFAULT NULL            COMMENT '更新时间（审计）',
    `deleted`           TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 1 删除',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备（服务端专属：换机/踢出/推送）';

-- ---------- 意见反馈（服务端专属） ----------
CREATE TABLE IF NOT EXISTS `bl_feedback` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `user_id`           BIGINT(20)     DEFAULT NULL            COMMENT '提交用户 id（可空=匿名）',
    `contact`           VARCHAR(100)   DEFAULT NULL            COMMENT '联系方式（可空）',
    `content`           TEXT           NOT NULL                COMMENT '反馈内容',
    `status`            VARCHAR(32)    NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING/REPLIED/CLOSED',
    `reply`             VARCHAR(1000)  DEFAULT NULL            COMMENT '回复内容',
    `reply_time`        DATETIME       DEFAULT NULL            COMMENT '回复时刻',
    `create_by`         BIGINT(20)     DEFAULT NULL            COMMENT '创建人（审计）',
    `create_time`       DATETIME       DEFAULT NULL            COMMENT '创建时间（审计）',
    `update_by`         BIGINT(20)     DEFAULT NULL            COMMENT '更新人（审计）',
    `update_time`       DATETIME       DEFAULT NULL            COMMENT '更新时间（审计）',
    `deleted`           TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 1 删除',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意见反馈（服务端专属：App 提交，后台处理）';

-- ---------- App 版本与公告（服务端专属） ----------
CREATE TABLE IF NOT EXISTS `bl_app_version` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `platform`          VARCHAR(16)    NOT NULL                COMMENT '平台：ANDROID/IOS',
    `version_code`      INT(11)        NOT NULL                COMMENT '版本号（versionCode，数值比较）',
    `version_name`      VARCHAR(32)    NOT NULL                COMMENT '版本名（versionName，展示）',
    `force_update`      TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '强制更新：0/1',
    `download_url`      VARCHAR(500)   DEFAULT NULL            COMMENT '下载地址（直装 apk / 应用市场链接）',
    `notice`            VARCHAR(1000)  DEFAULT NULL            COMMENT '更新说明/公告',
    `status`            VARCHAR(32)    NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED',
    `create_by`         BIGINT(20)     DEFAULT NULL            COMMENT '创建人（审计）',
    `create_time`       DATETIME       DEFAULT NULL            COMMENT '创建时间（审计）',
    `update_by`         BIGINT(20)     DEFAULT NULL            COMMENT '更新人（审计）',
    `update_time`       DATETIME       DEFAULT NULL            COMMENT '更新时间（审计）',
    `deleted`           TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 1 删除',
    PRIMARY KEY (`id`),
    KEY `idx_platform` (`platform`, `version_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 版本与公告（服务端专属：强更/提示更新）';

-- ---------- 疫苗时间表（服务端运营表，后台维护） ----------
CREATE TABLE IF NOT EXISTS `bl_vaccine_schedule` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `vaccine_name`      VARCHAR(100)   NOT NULL                COMMENT '疫苗名称',
    `dose_no`           INT(11)        NOT NULL DEFAULT 1      COMMENT '第几剂',
    `start_age_days`    INT(11)        NOT NULL                COMMENT '建议起始月龄（天）',
    `end_age_days`      INT(11)        DEFAULT NULL            COMMENT '建议截止月龄（天）',
    `charge_type`       VARCHAR(32)    NOT NULL DEFAULT 'FREE' COMMENT '费用类别：FREE/SELF_PAID',
    `category`          VARCHAR(32)    DEFAULT NULL            COMMENT '类别（分组展示）',
    `note`              VARCHAR(500)   DEFAULT NULL            COMMENT '备注',
    `sort_order`        INT(11)        NOT NULL DEFAULT 0      COMMENT '排序号',
    `create_by`         BIGINT(20)     DEFAULT NULL            COMMENT '创建人（审计）',
    `create_time`       DATETIME       DEFAULT NULL            COMMENT '创建时间（审计）',
    `update_by`         BIGINT(20)     DEFAULT NULL            COMMENT '更新人（审计）',
    `update_time`       DATETIME       DEFAULT NULL            COMMENT '更新时间（审计）',
    `deleted`           TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 1 删除',
    PRIMARY KEY (`id`),
    KEY `idx_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='疫苗时间表（运营表，后台维护、App 增量拉取；区别于 bl_vaccinations 用户实例）';

-- ---------- AI 用量（服务端运营表，V3 启用） ----------
CREATE TABLE IF NOT EXISTS `bl_ai_quota` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID）',
    `user_id`           BIGINT(20)     NOT NULL                COMMENT '用户 id',
    `feature`           VARCHAR(32)    NOT NULL                COMMENT '功能标识（PARSE_RECORD/WEEKLY_REPORT/HANDWRITING_IMPORT）',
    `quota_limit`       INT(11)        NOT NULL DEFAULT 0      COMMENT '额度上限',
    `quota_used`        INT(11)        NOT NULL DEFAULT 0      COMMENT '已用额度',
    `reset_at`          DATETIME       DEFAULT NULL            COMMENT '重置时刻',
    `create_by`         BIGINT(20)     DEFAULT NULL            COMMENT '创建人（审计）',
    `create_time`       DATETIME       DEFAULT NULL            COMMENT '创建时间（审计）',
    `update_by`         BIGINT(20)     DEFAULT NULL            COMMENT '更新人（审计）',
    `update_time`       DATETIME       DEFAULT NULL            COMMENT '更新时间（审计）',
    `deleted`           TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 1 删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_feature` (`user_id`, `feature`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 用量（运营表，V3 启用：按用户×功能计次）';
