-- =====================================================================
-- M73 / K5 · bl_ai_quota 计次口径：用户 × 功能 → 家庭 × 功能
-- =====================================================================
-- 背景（龙哥 2026-11-24 K5 拍板）：额度按「家庭 × 功能」共享，而不是按个人。
--   原唯一键 uk_user_feature(user_id, feature) 表达「每账号一份额度」，家庭三人都用完
--   各自的 N 次 ⇒ 家庭总消耗 = 人数 × N，与「家庭共享一份免费额度」的产品口径不符。
--
-- 为什么不直接加 family_id 并改唯一键：
--   无家庭的单用户 family_id = 0 ⇒ 所有单用户会撞到同一行 (0, 'PARSE_RECORD')，
--   额度互相吃掉。MySQL 唯一索引对 NULL 又是「各自独立」，同样拦不住并发首建。
--   ⇒ 用一列带命名空间的 scope_key 表达归属，两种形态走同一个唯一键：
--       F:<family_id>  家庭共享（有 ACTIVE 家庭成员行）
--       U:<user_id>    个人额度（无家庭，行为与 V2.x 单用户一致）
--
-- 本表仍是服务端运营表：不进 SyncTable 枚举、不参与 /sync/changes、端点不占 /sync/*。
-- =====================================================================

ALTER TABLE `bl_ai_quota`
    ADD COLUMN `scope_key` VARCHAR(64) NOT NULL DEFAULT ''
        COMMENT '额度归属：F:<family_id> 家庭共享 / U:<user_id> 个人（无家庭）' AFTER `user_id`;

-- 回填 1：有 ACTIVE 家庭的按家庭（与 FamilyAccessScopeResolver 同口径：status=ACTIVE 且未软删）
UPDATE `bl_ai_quota` q
    JOIN `bl_family_members` m
      ON m.`user_id` = q.`user_id`
     AND m.`deleted` = 0
     AND m.`status` = 'ACTIVE'
   SET q.`scope_key` = CONCAT('F:', m.`family_id`);

-- 回填 2：其余按个人（V3 启用前该表无业务数据，这里是「万一 dev/测试库有行」的兜底）
UPDATE `bl_ai_quota` SET `scope_key` = CONCAT('U:', `user_id`) WHERE `scope_key` = '';

-- 同一 scope + feature 的重复行（家庭合并后原本两人各一行 ⇒ 撞唯一键）：
-- 保留 id 最大的一条，其余物理删。这张表是计次计数器、无审计价值，
-- 且逻辑删的行仍占唯一键，所以这里必须物理删（不是 deleted=1）。
DELETE q1 FROM `bl_ai_quota` q1
    JOIN `bl_ai_quota` q2
      ON q1.`scope_key` = q2.`scope_key`
     AND q1.`feature` = q2.`feature`
     AND q1.`id` < q2.`id`;

ALTER TABLE `bl_ai_quota`
    DROP INDEX `uk_user_feature`,
    ADD UNIQUE KEY `uk_scope_feature` (`scope_key`, `feature`);

-- user_id 保留为「最后操作人 + 审计」，不再参与唯一性判定。
ALTER TABLE `bl_ai_quota`
    MODIFY COLUMN `user_id` BIGINT(20) NOT NULL
        COMMENT '最后操作人 user_id（审计；唯一性改由 scope_key 表达）';
