-- =============================================================
-- BabyLog 管理端菜单（M10，固定 5000 号段字面 ID）
--
-- 号段依据：sys_menu 字面 ID 已占用 1000~1199（system/docsync）/ 2000 / 3000~3062 /
-- 3500~3561 / 4000（file），5000 段全仓无占用（提交前已全仓 grep 复核）。
-- super_admin 走通配权限，无需 sys_role_menu 授权数据。
--
-- visible 说明：M/C 行置 '1'（隐藏）——pivotos-ui 的 babylog 页面在 M11 才落地，
-- 现在置 '0' 会在管理端导航点出 404 组件。M11 前端就绪时翻回 '0'（一条 UPDATE）。
-- 权限字符已生效（接口层 @SaCheckPermission 不依赖 visible），可先用 curl/Swagger 验证。
-- =============================================================

-- 一级目录：BabyLog 运营
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_by, create_time, deleted)
VALUES (5000, 0, 'BabyLog 运营', 'M', '/babylog', '', '', 'folder', 60, 1, 0, 1, NOW(), 0);

-- 内置产品库（M8 seed 的 32 行在此维护；改动经 base 通道增量下发 App）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_by, create_time, deleted) VALUES
    (5010, 5000, '内置产品库', 'C', 'product',   'babylog/product/index',   'babylog:product:list',   'shopping', 1, 1, 0, 1, NOW(), 0),
    (5011, 5010, '产品查询',   'F', '',           '',                        'babylog:product:query',  '',          1, 0, 0, 1, NOW(), 0),
    (5012, 5010, '产品编辑',   'F', '',           '',                        'babylog:product:edit',   '',          2, 0, 0, 1, NOW(), 0),
    (5013, 5010, '产品移除',   'F', '',           '',                        'babylog:product:remove', '',          3, 0, 0, 1, NOW(), 0);

-- 设备查看（按账号查设备、踢出；复用 M8 App 端 DeviceService 语义）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_by, create_time, deleted) VALUES
    (5020, 5000, '设备查看',   'C', 'device',    'babylog/device/index',    'babylog:device:list',    'monitor',   2, 1, 0, 1, NOW(), 0),
    (5021, 5020, '设备查询',   'F', '',           '',                        'babylog:device:query',   '',          1, 0, 0, 1, NOW(), 0),
    (5022, 5020, '设备踢出',   'F', '',           '',                        'babylog:device:kick',    '',          2, 0, 0, 1, NOW(), 0);

-- 版本公告（bl_app_version，V3 升级提醒用；PUBLISHED 后锁定不可改）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_by, create_time, deleted) VALUES
    (5030, 5000, '版本公告',   'C', 'version',   'babylog/version/index',   'babylog:version:list',   'upload',    3, 1, 0, 1, NOW(), 0),
    (5031, 5030, '版本查询',   'F', '',           '',                        'babylog:version:query',  '',          1, 0, 0, 1, NOW(), 0),
    (5032, 5030, '版本新增',   'F', '',           '',                        'babylog:version:add',    '',          2, 0, 0, 1, NOW(), 0),
    (5033, 5030, '版本编辑',   'F', '',           '',                        'babylog:version:edit',   '',          3, 0, 0, 1, NOW(), 0),
    (5034, 5030, '版本发布',   'F', '',           '',                        'babylog:version:publish','',          4, 0, 0, 1, NOW(), 0),
    (5035, 5030, '版本删除',   'F', '',           '',                        'babylog:version:remove', '',          5, 0, 0, 1, NOW(), 0);

-- 意见反馈（App 端匿名提交，管理端查看/回复；App 提交端点不挂权限字符）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_by, create_time, deleted) VALUES
    (5040, 5000, '意见反馈',   'C', 'feedback',  'babylog/feedback/index',  'babylog:feedback:list',  'message',   4, 1, 0, 1, NOW(), 0),
    (5041, 5040, '反馈查询',   'F', '',           '',                        'babylog:feedback:query', '',          1, 0, 0, 1, NOW(), 0),
    (5042, 5040, '反馈回复',   'F', '',           '',                        'babylog:feedback:reply', '',          2, 0, 0, 1, NOW(), 0);
