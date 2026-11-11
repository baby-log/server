# babylog-plugin-biz

BabyLog 育儿记录的业务插件（V2.0 云基座，会话 M4 落地骨架与建表）。

- 契约模块：`babylog-plugin-biz-api`（`com.babylog.biz.api`）——错误码 `BabyLogErrorCode`（**8300-8399**，8xxx 段内空闲子段，migration/search/datainspect 已占 8000-8221）
- 实现模块：`babylog-plugin-biz`（`com.babylog.biz`）——表前缀 **`bl_`**（ArchUnit A8 白名单已登记）
- 路由规划：App 端 `/app/babylog/**`（StpMobileUtil 校验）、管理端 `/babylog/**`（@SaCheckPermission）——controller/service 属后续会话

## 表清单（24 张，Flyway V2.2.4）

**18 张同步表**（与 App 端 Room 18 表一一对应，Room schema v7 逐表 diff 通过 18/18）：

`bl_children` / `bl_feedings` / `bl_diapers` / `bl_sleeps` / `bl_record_type_settings` /
`bl_family_members` / `bl_member_permissions` / `bl_products` / `bl_measurements` / `bl_medications` /
`bl_baths` / `bl_spits` / `bl_pump_sessions` / `bl_vaccinations` / `bl_follow_ups` / `bl_visits` /
`bl_attachments` / `bl_reminders`

**6 张服务端专属/运营表**：

`bl_family`（家庭，V2.1 点亮）/ `bl_device`（设备绑定/踢出）/ `bl_feedback`（意见反馈）/
`bl_app_version`（版本与公告）/ `bl_vaccine_schedule`（疫苗时间表运营数据，区别于 bl_vaccinations 用户实例）/ `bl_ai_quota`（V3 启用）

## 对齐口径（2026-11-11 拍板，App 端 V2.0 改造依据）

| 维度 | App 端 Room（1.1.0） | 服务端 bl_ | 说明 |
|---|---|---|---|
| 主键 | UUID 字符串 | 雪花 BIGINT | App 端 V2.0 改造时迁移（Room v8） |
| 业务时间 | Long epoch millis | BIGINT 同口径 | started_at/created_at/updated_at 等直接对齐 |
| 枚举 | String 存 name | VARCHAR 同口径 | 绝不存序号 |
| 软删除 | deleted Boolean | deleted TINYINT + @TableLogic | 同名同义，同步软删除信号 |
| 审计 | — | create_by/create_time/update_by/update_time | 服务端管理口径，两层并存 |
| 同步两件套 | —（App 端待加） | version（乐观锁）+ server_updated_at DATETIME(3) | 拉取游标用服务端钟，避客户端时钟漂移 |
| 哨兵 | "all-children"/"daily-supplement" | child_id=0 / source_id 字符串保留 | 雪花化后的哨兵口径 |
| 内置产品 | 可读串 id（builtin-*） | id 取 1~99 预留段 | 与雪花大数天然不撞，device_id='builtin' |

**已文档化的三处结构性差异**（逐表 diff 白名单）：

1. `bl_family_members`：Room 的 `family_key`（V1 本地占位 "local-family"）→ 服务端 `family_id` + `user_id` + `user_type`
2. `bl_children`：服务端补 `family_id`（V2.1）+ `owner_user_id`（V2.0 单用户归属）
3. `bl_attachments`：服务端补 `remote_key`（App 端 V1 注释里规划的远端文件 key 在此落地）

## 登记点（本插件已登记，后续新增插件照此办理）

1. `babylog-plugins/pom.xml` modules（biz-api + biz 两行）
2. `babylog-dependencies/pom.xml` dependencyManagement（两条）
3. `babylog-admin-server/pom.xml` 依赖（biz 一条）
4. `BabyLogAdminApplication`：`scanBasePackages` + `@AutoConfigurationPackage` 各加 `com.babylog.biz`
5. `P0ArchitectureTest`：OTHER_PLUGIN_IMPL、A8 白名单（bl_）、新增 biz 对称规则、A3、PLUGIN_ROOTS、CONTRACT_PACKAGES

## 验证记录（M4）

- `mvn -B clean package -DskipTests -pl babylog-plugins/babylog-plugin-biz -am` 通过
- ArchUnit P0ArchitectureTest **22/22 绿**（含新增 biz 对称规则与 bl_ 前缀校验）
- Room schema v7 ↔ V2.2.4 建表 SQL 逐表 diff **18/18 通过**

## 后续（M5+）

- 登录：邮箱验证码（首发，自建补充接口，不动 system 插件）+ 账密注册兜底；App token 时长策略（StpAppUtil.STP 独立 StpLogic 可单独调 timeout）
- 同步接口：`GET /app/babylog/sync/changes?since=`（按 server_updated_at 游标）+ `POST /app/babylog/sync/push`（version 乐观锁 + 逐条权限校验）
- 基础数据：products/vaccines 增量下发；管理端 controller + 菜单 SQL
- 注意：本插件全部表**不加 tenant_id**（多租户不启用；BabyLog 部署配置不得开 tenant）
