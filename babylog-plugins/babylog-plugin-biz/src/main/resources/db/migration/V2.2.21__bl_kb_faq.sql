-- =====================================================================
-- K6-S / M76 · 育儿知识库：FAQ 条目表 + 收藏表 + KB_QA 额度参数
-- =====================================================================
-- 契约（与 bl_ai_quota / bl_notice 同口径）：
--   * 两张表都是**服务端专用表**：不进 SyncTable 枚举、不参与 /sync/changes、
--     不进 outbox、端点不占 /sync/ 开头的同步端点、不发 bl_notice。
--   * 只存「权威公开资料的条目化摘录」，每条必填来源（source）；禁整篇搬运
--     （content 由应用层卡 2000 字，DB 用 TEXT 不设硬限，避免迁移期数据截断）。
--   * simple 向量库（data/kb/simple/kb-store.json）属上游 ai-kb 插件，本迁移不动；
--     RAG 问答检索走 IKnowledgeBaseFacade，上游文档灌入走既有 /ai/kb/doc 端点。
--   * 无任何跨库 JOIN，SQL 只用 bl_ 与 sys_config 单表 DDL/DML。
-- =====================================================================

CREATE TABLE `bl_kb_faq` (
    `id`          BIGINT       NOT NULL COMMENT '主键（雪花，禁自增）',
    `category`    VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '分类：FEEDING/SLEEP/HEALTH/GROWTH/SAFETY/OTHER',
    `title`       VARCHAR(200) NOT NULL DEFAULT '' COMMENT '条目标题',
    `content`     TEXT         NOT NULL COMMENT '摘录正文（应用层限 2000 字，禁整篇搬运）',
    `source`      VARCHAR(500) NOT NULL DEFAULT '' COMMENT '资料来源（机构/书名/文件名，10 号 §4.1 红线一必填）',
    `source_url`  VARCHAR(500) DEFAULT NULL COMMENT '来源链接（可选）',
    `enabled`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否上架：1 上架 0 下架',
    `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '同分类内排序（小的在前）',
    `create_by`   BIGINT       DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_by`   BIGINT       DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 正常 1 删除',
    PRIMARY KEY (`id`),
    KEY `idx_category_enabled` (`category`, `enabled`, `deleted`),
    KEY `idx_sort` (`sort_order`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '育儿知识库 · 条目化 FAQ（服务端专用表，不进 SyncTable）';

-- 纯关联表：不带 deleted 列 ⇒ 取消收藏是物理 DELETE，重复收藏不会被「逻辑删行仍占唯一键」卡住
-- （同 V2.2.20 对计次表的判断）。唯一键兜底并发重复收藏，服务端吞 DuplicateKeyException 幂等返回。
CREATE TABLE `bl_kb_favorite` (
    `id`          BIGINT   NOT NULL COMMENT '主键（雪花，禁自增）',
    `user_id`     BIGINT   NOT NULL COMMENT '收藏人 user_id（取自 app-user 裸 token，客户端不传）',
    `faq_id`      BIGINT   NOT NULL COMMENT 'bl_kb_faq.id',
    `create_time` DATETIME DEFAULT NULL COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_faq` (`user_id`, `faq_id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '育儿知识库 · 收藏（纯关联表，取消收藏物理删，不带 deleted 列）';

-- KB_QA 免费额度参数（龙哥 2026-11-25 拍板 10 次/天 DAILY + 门禁 C 授权「后台可配」）。
-- id=5：V1.0.1 用 1/2、V2.2.1 用 3、V2.2.2 用 4 ⇒ 5 是首个未占用位。
-- config_key 有唯一键 uk_config_key，INSERT IGNORE 保证重复执行 / 已有环境不炸。
-- 服务端 AiQuotaConfigReader 实时查库（deleted = 0），运营在「系统工具 - 参数设置」改完
-- 下一次问答即时生效、无需重启；参数缺失 / 非法回落 application.yml 的 KB_QA.limit。
-- config_type = 'N'（自定义）：这是可运营参数，不是系统内置不可改项。
INSERT IGNORE INTO `sys_config`
    (`id`, `config_name`, `config_key`, `config_value`, `config_type`, `remark`, `create_by`, `create_time`, `deleted`)
VALUES
    (5, '育儿知识库问答每日免费次数', 'babylog.ai.quota.kb_qa.limit', '10', 'N',
     'KB_QA 每自然日免费问答次数（家庭×功能共用）；<=0 = 不限次（只计次）。改完实时生效、无需重启；参数缺失/非数字/负数回落 application.yml。V3 购买引导另议。',
     1, NOW(), 0);
