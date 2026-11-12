-- =============================================================
-- BabyLog 管理端菜单解封（M11，管理端前端页面落地）
--
-- 背景：V2.2.9 建菜单时 pivotos-ui 的 babylog 页面尚未落地，
-- M/C 行置 visible='1' 隐藏防止导航点出 404。M11 已交付四页：
--   apps/admin/src/views/babylog/{product,device,version,feedback}/index.vue
-- 对应菜单 component 路径逐字对齐，本迁移翻回 '0'（可见）。
--
-- 顺带归一：5010 的 icon 'shopping' 不在前端图标注册表
-- （pivotos-ui apps/admin/src/layout/icons.ts，未登记名兜底成通用菜单图标），
-- 换成已注册的 'shop'。权限字符零改动（契约测试 AdminControllerPermissionContractTest 锁死）。
--
-- 迁移号说明：Flyway 版本号是全库全局序列（2.2.4~2.2.9 已占），
-- V2.2.9 在 dev 库已应用（checksum 锁死不能原地改），故翻牌走新号 V2.2.10；
-- test/prod 未部署过 M10 jar，届时 V2.2.9 + V2.2.10 按序一次到位。
-- =============================================================

UPDATE sys_menu SET visible = '0' WHERE id IN (5000, 5010, 5020, 5030, 5040) AND visible = '1' AND deleted = 0;

UPDATE sys_menu SET icon = 'shop' WHERE id = 5010 AND icon = 'shopping' AND deleted = 0;
