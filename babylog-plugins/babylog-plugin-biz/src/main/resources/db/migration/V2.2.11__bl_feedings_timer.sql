-- M37 喂养计时器：bl_feedings 加 5 列（与 App 端 Room v9 feedings 逐列对齐）。
-- 全部可空/带默认值，存量行零改动；bl_ 表不加 tenant_id（多租户不启用）。
--
-- 同步红线：in_progress=1 的半成品行客户端永不推送（App 端 outbox 拦截），
-- 这里在拉取侧再兜一道——SyncTableSql 给 feedings 的增量拉取追加 in_progress = 0，
-- 保证「服务端只吃已完成记录」对下发同样成立。
ALTER TABLE `bl_feedings`
    ADD COLUMN `in_progress`    TINYINT     NOT NULL DEFAULT 0 COMMENT '计时进行中（0/1）',
    ADD COLUMN `ended_at`       BIGINT      NULL COMMENT '计时结束时刻（epoch millis）',
    ADD COLUMN `total_seconds`  INT         NULL COMMENT '总用时（秒，含暂停）',
    ADD COLUMN `active_seconds` INT         NULL COMMENT '纯喂养用时（秒，减全部暂停段）',
    ADD COLUMN `pause_events`   TEXT        NULL COMMENT '计时事件流 JSON：[{t:START|PAUSE|RESUME|END,at:epoch}]';
