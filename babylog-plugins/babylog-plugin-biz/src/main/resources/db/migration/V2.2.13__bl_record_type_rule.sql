-- ============================================================
-- M41 · 记录类型云端规则（bl_record_type_rule + 管理端菜单 5050 树）
--
-- 边界（10 文档第八节，写死）：云端只能调「已有记录类型」的默认开关、起止月龄、
-- 记录页排序、分组、显示文案；不能云端新增记录类型（防止膨胀成动态表单引擎）。
-- 因此本表只有 seed + 管理端编辑，没有新增入口；管理端 controller 也不暴露 add。
--
-- 1. 表结构：一类型一行（uk record_type）。enabled/start_age_days/end_age_days
--    允许 NULL = 「该项不覆盖，回落客户端代码默认」——运营只想改文案时不用被迫
--    填齐所有字段。sort_order 默认 0 = 按注册表顺序；group_key NULL = 默认分组。
-- 2. 游标列 server_updated_at 列级 ON UPDATE 自动维护；实体刻意不声明该字段
--    （M10/M18 教训，与 V2.2.12 三张运营表同款取舍）。
-- 3. seed 21 行（id 1~21）逐字对齐 App 端 RecordType 注册表（01B §3）：
--    类型名/分类/默认开关/起止月龄/注册表顺序。display_label 用客户端现行文案，
--    seed 的 NULL（start/end/enabled）语义 = 「与代码默认一致」，管理端改后再落具体值。
--    bl_ 表惯例不加 tenant_id；版本号落笔前已 grep 无占用。
-- 4. 菜单：5000 树新增 5050「记录类型规则」（C 页 + 查询/编辑两个 F 按钮）。
--    管理端前端页面随本批（M41）一并落地，菜单直接置 visible='0'（可见），
--    无需再走 V2.2.9 → V2.2.10 那种「先藏后翻」的两步节奏。
-- ============================================================

CREATE TABLE IF NOT EXISTS `bl_record_type_rule` (
    `id`                BIGINT(20)     NOT NULL                COMMENT '主键（雪花 ID；seed 段 1~99）',
    `record_type`       VARCHAR(32)    NOT NULL                COMMENT '记录类型标识（与客户端 RecordType 枚举 name 逐字一致；uk）',
    `category`          VARCHAR(16)    NOT NULL                COMMENT '分类（FEEDING/CARE/HEALTH/MEDICAL，只读展示用，客户端以注册表为准）',
    `enabled`           TINYINT(4)     DEFAULT NULL            COMMENT '默认开关：NULL=回落代码默认，0=默认关，1=默认开',
    `start_age_days`    INT(11)        DEFAULT NULL            COMMENT '起始月龄（天）：NULL=回落代码默认',
    `end_age_days`      INT(11)        DEFAULT NULL            COMMENT '截止月龄（天）：NULL=回落代码默认；NULL 且代码也 NULL=永不自动收起',
    `sort_order`        INT(11)        NOT NULL DEFAULT 0      COMMENT '记录页类内排序（小的在前；0=按注册表默认顺序）',
    `group_key`         VARCHAR(32)    DEFAULT NULL            COMMENT '类内分组键（客户端按组聚合排序；NULL=默认不分组）',
    `display_label`     VARCHAR(32)    DEFAULT NULL            COMMENT '显示文案：NULL=回落代码默认文案',
    `server_updated_at` DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '基础数据通道游标（since 基准，自动维护）',
    `create_by`         BIGINT(20)     DEFAULT NULL            COMMENT '创建人（审计）',
    `create_time`       DATETIME       DEFAULT NULL            COMMENT '创建时间（审计）',
    `update_by`         BIGINT(20)     DEFAULT NULL            COMMENT '更新人（审计）',
    `update_time`       DATETIME       DEFAULT NULL            COMMENT '更新时间（审计）',
    `deleted`           TINYINT(4)     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 1 删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_type` (`record_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记录类型云端规则（运营表，M41 起走 base 通道下发；云端只能调已有类型参数，不能新增类型）';

-- ---------- seed：21 行，逐字对齐 App 端 RecordType 注册表（顺序即注册表顺序） ----------
-- enabled/start/end 全 NULL = 与代码默认完全一致（改哪项填哪项）；
-- sort_order 从 10 起步进（给将来插入留出整数位），同类内即注册表顺序。
INSERT INTO `bl_record_type_rule`
    (`id`, `record_type`, `category`, `enabled`, `start_age_days`, `end_age_days`, `sort_order`, `group_key`, `display_label`)
VALUES
    (1,  'BOTTLE',       'FEEDING', NULL, NULL, NULL, 10,  NULL, '奶瓶'),
    (2,  'BREAST_LEFT',  'FEEDING', NULL, NULL, NULL, 20,  NULL, '母乳左'),
    (3,  'BREAST_RIGHT', 'FEEDING', NULL, NULL, NULL, 30,  NULL, '母乳右'),
    (4,  'SOLID',        'FEEDING', NULL, 180,  NULL, 40,  NULL, '辅食'),
    (5,  'WATER',        'FEEDING', NULL, 180,  NULL, 50,  NULL, '喝水'),
    (6,  'PUMP',         'FEEDING', 0,    NULL, 730,  60,  NULL, '泵奶'),
    (7,  'DIAPER',       'CARE',    NULL, NULL, 1095, 10,  NULL, '尿布'),
    (8,  'SLEEP',        'CARE',    NULL, NULL, NULL, 20,  NULL, '睡眠'),
    (9,  'BATH',         'CARE',    NULL, NULL, NULL, 30,  NULL, '洗澡'),
    (10, 'CORD',         'CARE',    NULL, NULL, 60,   40,  NULL, '脐带护理'),
    (11, 'SPIT_UP',      'CARE',    NULL, NULL, 365,  50,  NULL, '吐奶'),
    (12, 'TEMP',         'HEALTH',  NULL, NULL, NULL, 10,  NULL, '体温'),
    (13, 'WEIGHT',       'HEALTH',  NULL, NULL, NULL, 20,  NULL, '体重'),
    (14, 'HEIGHT',       'HEALTH',  NULL, NULL, NULL, 30,  NULL, '身高'),
    (15, 'HEAD',         'HEALTH',  NULL, NULL, NULL, 40,  NULL, '头围'),
    (16, 'JAUNDICE',     'HEALTH',  NULL, NULL, 60,   50,  NULL, '黄疸'),
    (17, 'SUPPLEMENT',   'MEDICAL', NULL, NULL, NULL, 10,  NULL, '补剂'),
    (18, 'MEDICINE',     'MEDICAL', 0,    NULL, NULL, 20,  NULL, '用药'),
    (19, 'VACCINE',      'MEDICAL', NULL, NULL, 2190, 30,  NULL, '疫苗'),
    (20, 'FOLLOW_UP',    'MEDICAL', NULL, NULL, 90,   40,  NULL, '随访'),
    (21, 'VISIT',        'MEDICAL', NULL, NULL, NULL, 50,  NULL, '就医');

-- ---------- 管理端菜单：5000 树新增 5050「记录类型规则」（页面本批落地后翻可见，同 M11 节奏） ----------
INSERT INTO `sys_menu` (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_by, create_time, deleted) VALUES
    (5050, 5000, '记录类型规则', 'C', 'record-type-rule', 'babylog/recordTypeRule/index', 'babylog:recordTypeRule:list',  'list', 5, 0, 0, 1, NOW(), 0),
    (5051, 5050, '规则查询',     'F', '',                 '',                             'babylog:recordTypeRule:query', '',     1, 0, 0, 1, NOW(), 0),
    (5052, 5050, '规则编辑',     'F', '',                 '',                             'babylog:recordTypeRule:edit',  '',     2, 0, 0, 1, NOW(), 0);
