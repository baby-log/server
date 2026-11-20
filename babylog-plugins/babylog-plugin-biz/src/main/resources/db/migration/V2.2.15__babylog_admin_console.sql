-- =============================================================
-- M53 · BabyLog 运营数据只读查询菜单（V2.4.0 内修复批次）
--
-- 号段：5000 一级目录已存在（V2.2.9），5010~5050 已占用；本批用 5060~5069。
-- super_admin 走通配权限，无需 sys_role_menu 授权数据。
-- visible = '0'（显示）：本批前端页面在 apps/admin/src/views/babylog/ 一并落地，
--          component 字符串必须与目录名逐字一致，否则 permission.ts 的 glob 装配退化 404。
-- 只读边界：菜单只挂 list 权限字符，不提供任何新增/编辑/删除/导出按钮。
-- =============================================================

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_by, create_time, deleted) VALUES
    (5060, 5000, '运营概览',   'C', 'admin-overview', 'babylog/adminOverview/index', 'babylog:admin:list', 'dashboard', 6, 0, 0, 1, NOW(), 0),
    (5061, 5000, '全部账号',   'C', 'admin-user',     'babylog/adminUser/index',     'babylog:admin:list', 'user',      7, 0, 0, 1, NOW(), 0),
    (5062, 5000, '全部家庭',   'C', 'admin-family',   'babylog/adminFamily/index',   'babylog:admin:list', 'peoples',   8, 0, 0, 1, NOW(), 0),
    (5063, 5000, '全部宝宝',   'C', 'admin-child',    'babylog/adminChild/index',    'babylog:admin:list', 'tree-table',9, 0, 0, 1, NOW(), 0),
    (5064, 5000, '记录明细',   'C', 'admin-record',   'babylog/adminRecord/index',   'babylog:admin:list', 'list',     10, 0, 0, 1, NOW(), 0);
