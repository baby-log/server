-- M46 记录增强链（18 号契约 §七）：bl_baths 加 ended_at、bl_sleeps 加 transitions。
-- 版本号落笔前已 grep：2.2.4~2.2.13 已占用，本号为 2.2.14。
-- 两列均 NULL 无默认、存量行零改动；bl_ 表不加 tenant_id（多租户不启用）。
-- MySQL DDL 不进事务（M5 教训）；无新拉取谓词、无 NOT NULL 拒收列。
ALTER TABLE `bl_baths`
    ADD COLUMN `ended_at` BIGINT NULL COMMENT '结束时刻（epoch millis），null=只记单点（M46 D1：无进行中状态机）';

ALTER TABLE `bl_sleeps`
    ADD COLUMN `transitions` TEXT NULL COMMENT '睡眠方式段事件流 JSON：[{place:CRIB|BIG_BED|HELD|STROLLER,at:epoch}]，首条=入睡方式；为空回落 place 列';
