-- M57 · 宝宝档案新增「喂养方式」（四端宝宝管理可改，跨设备同步）
--
-- 值域与客户端 Room/SwiftData/小程序枚举对齐（只存 name，绝不存序号）：
--   BREAST_ONLY  纯母乳
--   FORMULA_ONLY 纯配方/奶粉
--   MIXED        混合
-- NULL = 老数据未设置（不回落、不臆测，UI 显示「未设置」）。
--
-- 归属 children 同步表：列清单由 SyncEntityMeta 反射 BlChild 字段推导，
-- 本列自动进入 payloadProperties（可推）与 pullProperties（可下发），
-- 不需要新增端点或改 SyncTable.CHILDREN 的 serverOwnedProperties。
--
-- MySQL DDL 隐式提交，不进事务；本文件只放这一条 ALTER。
ALTER TABLE `bl_children`
    ADD COLUMN `feeding_method` VARCHAR(16) NULL
    COMMENT '喂养方式：BREAST_ONLY/FORMULA_ONLY/MIXED；NULL=老数据未设置'
    AFTER `note`;
