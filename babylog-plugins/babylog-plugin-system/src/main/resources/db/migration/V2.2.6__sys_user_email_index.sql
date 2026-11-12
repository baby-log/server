-- 按邮箱查询账号索引（非唯一：未绑定邮箱的账号统一存空串，唯一键会互相冲突）
--
-- 版本号说明：上游 PivotOS 原用 V2.2.4，但本应用 fork 的 biz 插件已占用 V2.2.4
-- （V2.2.4__babylog_init.sql）与 V2.2.5（bl_device 设备标识）。Flyway 的版本号是全库
-- 全局序列、locations 跨 jar 合并成一份历史表，同号会启动失败（out-of-order 只放宽顺序、
-- 不放宽重号），故顺延为 V2.2.6。
CREATE INDEX idx_email ON sys_user (email);
