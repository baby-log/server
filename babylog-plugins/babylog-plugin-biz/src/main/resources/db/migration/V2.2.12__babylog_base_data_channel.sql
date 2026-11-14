-- ============================================================
-- M40 · 基础数据通道范围扩展（疫苗时间表 / 异常提醒阈值 / 版本与公告）
--
-- 1. bl_vaccine_schedule / bl_app_version 补 server_updated_at 游标列：
--    两表是基础数据通道（/app/babylog/base/**）的新成员，游标口径与 products 一致
--    （双段 server_updated_at + id，列级 ON UPDATE 自动前进）。
--    实体刻意**不声明** serverUpdatedAt 字段（与 18 张同步表同款取舍）：
--    MP updateById 不会显式写回旧值，列级 ON UPDATE 必然触发；
--    将来给这两表做管理端维护时，不得把该字段加回实体（M10/M18 的教训：
--    SELECT 出来的实体带着非空游标列，NOT_NULL 策略会把它显式写回，
--    值不变 → ON UPDATE 不触发 → 游标不前进 → App 永远拉不到）。
-- 2. 新建 bl_alert_threshold：异常提醒阈值（键值对，与 App 端 AlertThresholds
--    数据类字段一一对应；客户端按键解析类型，替换本地默认值）。
-- 3. seed：疫苗时间表 28 行（逐字对齐 App 端 NationalImmunizationSchedule.kt，
--    跨仓库契约唯一事实来源）；异常提醒阈值 18 行（逐字对齐 AlertThresholds 默认值）。
--    bl_ 表惯例不加 tenant_id；版本号落笔前已 grep 无占用。
-- ============================================================

-- ---------- 疫苗时间表补游标列 ----------
ALTER TABLE `bl_vaccine_schedule`
    ADD COLUMN `server_updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '基础数据通道游标（since 基准，自动维护）' AFTER `sort_order`,
    ADD KEY `idx_sync_cursor` (`server_updated_at`);

-- ---------- 版本与公告补游标列 ----------
ALTER TABLE `bl_app_version`
    ADD COLUMN `server_updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '基础数据通道游标（since 基准，自动维护）' AFTER `status`,
    ADD KEY `idx_sync_cursor` (`server_updated_at`);

-- ---------- 异常提醒阈值（运营表） ----------
CREATE TABLE IF NOT EXISTS `bl_alert_threshold` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID；seed 段 1~99）',
    `threshold_key`     VARCHAR(64)    NOT NULL                COMMENT '阈值键（与客户端 AlertThresholds 字段一一对应）',
    `threshold_value`   VARCHAR(64)    NOT NULL                COMMENT '阈值值（数字存文本，客户端按键解析类型）',
    `description`       VARCHAR(255)   DEFAULT NULL            COMMENT '说明（管理端展示用）',
    `server_updated_at` DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '基础数据通道游标（since 基准，自动维护）',
    `create_by`         BIGINT(20)     DEFAULT NULL            COMMENT '创建人（审计）',
    `create_time`       DATETIME       DEFAULT NULL            COMMENT '创建时间（审计）',
    `update_by`         BIGINT(20)     DEFAULT NULL            COMMENT '更新人（审计）',
    `update_time`       DATETIME       DEFAULT NULL            COMMENT '更新时间（审计）',
    `deleted`           TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 1 删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_threshold_key` (`threshold_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异常提醒阈值（运营表，键值对；M40 起走 base 通道下发，客户端替换 AlertThresholds 本地默认值）';

-- ---------- seed：疫苗时间表 28 行（id 1~28，对齐 App 端静态时间表顺序） ----------
-- start_age_days 为近似天龄（年×365 + 月×30 + 附加天），运营口径以月历为准；App 端 M40 暂不消费本表。
INSERT INTO `bl_vaccine_schedule`
    (`id`, `vaccine_name`, `dose_no`, `start_age_days`, `end_age_days`, `charge_type`, `category`, `note`, `sort_order`)
VALUES
    (1,  '乙肝疫苗',       1, 0,    NULL, 'FREE', '免疫规划', NULL, 1),
    (2,  '乙肝疫苗',       2, 30,   NULL, 'FREE', '免疫规划', NULL, 2),
    (3,  '乙肝疫苗',       3, 180,  NULL, 'FREE', '免疫规划', NULL, 3),
    (4,  '卡介苗',         1, 0,    90,   'FREE', '免疫规划', '小于 3 月龄完成', 4),
    (5,  '脊灰灭活疫苗',   1, 60,   NULL, 'FREE', '免疫规划', NULL, 5),
    (6,  '脊灰灭活疫苗',   2, 90,   NULL, 'FREE', '免疫规划', NULL, 6),
    (7,  '脊灰减毒活疫苗', 3, 120,  NULL, 'FREE', '免疫规划', '口服', 7),
    (8,  '脊灰减毒活疫苗', 4, 1460, NULL, 'FREE', '免疫规划', '口服；4 岁', 8),
    (9,  '百白破疫苗',     1, 90,   NULL, 'FREE', '免疫规划', NULL, 9),
    (10, '百白破疫苗',     2, 120,  NULL, 'FREE', '免疫规划', NULL, 10),
    (11, '百白破疫苗',     3, 150,  NULL, 'FREE', '免疫规划', NULL, 11),
    (12, '百白破疫苗',     4, 540,  NULL, 'FREE', '免疫规划', '18 月龄', 12),
    (13, '白破疫苗',       5, 2190, NULL, 'FREE', '免疫规划', '6 岁', 13),
    (14, '麻腮风疫苗',     1, 240,  NULL, 'FREE', '免疫规划', NULL, 14),
    (15, '麻腮风疫苗',     2, 540,  NULL, 'FREE', '免疫规划', '18 月龄', 15),
    (16, '乙脑减毒活疫苗', 1, 240,  NULL, 'FREE', '免疫规划', '乙脑双方案默认线（与灭活二选一）', 16),
    (17, '乙脑减毒活疫苗', 2, 730,  NULL, 'FREE', '免疫规划', '乙脑双方案默认线；2 岁', 17),
    (18, '乙脑灭活疫苗',   1, 240,  NULL, 'FREE', '免疫规划', '与乙脑减毒活疫苗二选一', 18),
    (19, '乙脑灭活疫苗',   2, 247,  NULL, 'FREE', '免疫规划', '与第 1 剂间隔 7~10 天', 19),
    (20, '乙脑灭活疫苗',   3, 730,  NULL, 'FREE', '免疫规划', '2 岁', 20),
    (21, '乙脑灭活疫苗',   4, 2190, NULL, 'FREE', '免疫规划', '6 岁', 21),
    (22, 'A群流脑多糖疫苗', 1, 180,  NULL, 'FREE', '免疫规划', NULL, 22),
    (23, 'A群流脑多糖疫苗', 2, 270,  NULL, 'FREE', '免疫规划', NULL, 23),
    (24, 'A群C群流脑多糖疫苗', 3, 1095, NULL, 'FREE', '免疫规划', '3 岁', 24),
    (25, 'A群C群流脑多糖疫苗', 4, 2190, NULL, 'FREE', '免疫规划', '6 岁', 25),
    (26, '甲肝减毒活疫苗', 1, 540,  NULL, 'FREE', '免疫规划', '甲肝双方案默认线（与灭活二选一）', 26),
    (27, '甲肝灭活疫苗',   1, 540,  NULL, 'FREE', '免疫规划', '与甲肝减毒活疫苗二选一', 27),
    (28, '甲肝灭活疫苗',   2, 730,  NULL, 'FREE', '免疫规划', '2 岁', 28);

-- ---------- seed：异常提醒阈值 18 行（id 1~18，逐字对齐 App 端 AlertThresholds 默认值） ----------
-- 客户端按键解析：int 键（天数/次数/小时）、double 键（比例/温度/z 值）、黄疸 6 档日龄阶梯。
-- 黄疸单位为 μmol/L（用户录入同单位；将来管理端维护不得混用 mg/dL，App 端 M39 红线）。
INSERT INTO `bl_alert_threshold`
    (`id`, `threshold_key`, `threshold_value`, `description`)
VALUES
    (1,  'milk_lookback_days',              '7',    '奶量骤降：回看天数（今日之前的前 N 天）'),
    (2,  'milk_drop_ratio',                 '0.7',  '奶量骤降：今日 < 历史日均值 × 该比例（严格小于）'),
    (3,  'milk_min_history_days',           '3',    '奶量骤降：至少多少个有数据的历史日才评估'),
    (4,  'low_fever_c',                     '37.5', '发热：≥ 该值为低热（含恰好等于，不看部位）'),
    (5,  'fever_c',                         '38.0', '发热：≥ 该值为发热（含恰好等于）'),
    (6,  'fever_window_hours',              '72',   '发热：只看最近 N 小时内的量值，过期不翻旧账'),
    (7,  'diaper_min_per_24h',              '4',    '尿布脱水警戒：最近 24h 总数 < 该值触发'),
    (8,  'watery_stool_max_per_24h',        '4',    '尿布腹泻警戒：最近 24h 水样便 ≥ 该值触发'),
    (9,  'weight_stagnation_days',          '14',   '体重停滞：偏离需持续的最短天数'),
    (10, 'weight_channel_z',                '2.0',  '体重停滞：WHO 通道宽度 = z ± 该值'),
    (11, 'jaundice_day0',                   '100',  '黄疸超阈：出生 0~23 小时参考值（μmol/L）'),
    (12, 'jaundice_day1',                   '170',  '黄疸超阈：出生 1 日参考值（μmol/L）'),
    (13, 'jaundice_day2',                   '240',  '黄疸超阈：出生 2 日参考值（μmol/L）'),
    (14, 'jaundice_day3',                   '270',  '黄疸超阈：出生 3 日参考值（μmol/L）'),
    (15, 'jaundice_day4',                   '290',  '黄疸超阈：出生 4 日参考值（μmol/L）'),
    (16, 'jaundice_day5_plus',              '300',  '黄疸超阈：出生第 5 天起参考值（μmol/L）'),
    (17, 'supplement_check_hour',           '21',   '补剂漏打卡：当天该小时（本地时间）之后才评估'),
    (18, 'supplement_active_lookback_days', '7',    '补剂漏打卡：「在服」= 最近 N 天内有同名打卡');
