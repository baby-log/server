# babylog-plugin-biz

BabyLog 育儿记录的业务插件（V2.0 云基座，会话 M4 落地骨架与建表，会话 M5 落地 App 认证）。

- 契约模块：`babylog-plugin-biz-api`（`com.babylog.biz.api`）——错误码 `BabyLogErrorCode`（**8300-8399**，8xxx 段内空闲子段，migration/search/datainspect 已占 8000-8221）
- 实现模块：`babylog-plugin-biz`（`com.babylog.biz`）——表前缀 **`bl_`**（ArchUnit A8 白名单已登记）
- 路由规划：App 端 `/app/babylog/**`（app-user 体系，注解 + `StpAppUtil` 校验）、管理端 `/babylog/**`（`@SaCheckPermission`）——管理端 controller 属后续会话

## 表清单（24 张，Flyway V2.2.4）

**18 张同步表**（与 App 端 Room 18 表一一对应，Room schema v7 逐表 diff 通过 18/18）：

`bl_children` / `bl_feedings` / `bl_diapers` / `bl_sleeps` / `bl_record_type_settings` /
`bl_family_members` / `bl_member_permissions` / `bl_products` / `bl_measurements` / `bl_medications` /
`bl_baths` / `bl_spits` / `bl_pump_sessions` / `bl_vaccinations` / `bl_follow_ups` / `bl_visits` /
`bl_attachments` / `bl_reminders`

**6 张服务端专属/运营表**：

`bl_family`（家庭，V2.1 点亮）/ `bl_device`（设备绑定/踢出）/ `bl_feedback`（意见反馈）/
`bl_app_version`（版本与公告）/ `bl_vaccine_schedule`（疫苗时间表运营数据，区别于 bl_vaccinations 用户实例）/ `bl_ai_quota`（V3 启用）

> `bl_device` 在 **V2.2.5** 补了 `device_uuid` + `uk(user_id, device_uuid)`（M5：登录幂等落行）；
> `worker_id`（设备级雪花位）留 M6 与同步归属校验一起落地。

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

## App 认证（M5，会话 2026-11-12）

端点全在 `/app/babylog/auth/**`，**不动 system 的 `/app/auth/*`**；账号仍是 `sys_user` + app-user 独立 Token。

| 端点 | 鉴权 | 说明 |
|---|---|---|
| `POST /email/code` | 匿名 | 发 6 位验证码（300s 有效、60s 冷却、日 10 次、错 5 次作废） |
| `POST /email/login` | 匿名 | 验证码登录，邮箱未注册则自动建档（`newUser=true`） |
| `POST /register` | 匿名 | 账密注册（用户名 3-32 位字母数字下划线、密码 8-64 位），注册即登录 |
| `POST /password/login` | 匿名 | 账密登录，`account` 可为用户名或邮箱；失败统一 2001 |
| `POST /refresh` | 匿名 | 凭 refresh token 换新 access token（refresh 不轮换） |
| `POST /logout` | `@SaCheckLogin(app-user)` | 注销 access + 作废 refresh |

**token 策略（双 token，龙哥 2026-11-12 拍板）**：access **2h**（Sa-Token，`login(id, timeout)` 按次覆盖，全局 86400 不动）；
refresh **30d**（Redis 只存 SHA-256 哈希，键 `bl:auth:rt:<hash>`）。refresh 不轮换，理由写在 `RedisRefreshTokenStore` 类注释里。

**错误码**：8370 验证码错误/过期 · 8371 发送过频 · 8372 日配额满 · 8373 邮箱格式 · 8374 邮件服务未配置 ·
8375 发信失败 · 8376 refresh 失效 · 8377 邮箱已绑定其他账号；登录失败/停用仍用 system 的 **2001/2002**，只有一条登录失败语义。

**配置键**（全部内联 yml，各环境自带）：

```yaml
sa-token:
  timeout: 86400          # 管端口径，App 不受影响（登录时按次覆盖）

babylog:
  auth:
    access-token-ttl-seconds: 7200
    refresh-token-ttl-seconds: 2592000
    email:
      code-ttl-seconds: 300
      resend-interval-seconds: 60
      daily-limit: 10
      max-verify-attempts: 5
      subject: BabyLog 登录验证码
      from:               # 留空回退 spring.mail.username

spring:
  mail:                   # 只有 host 有值时 Boot 才装配 JavaMailSender
    host: smtp.163.com
    port: 465
    username:             # 龙哥填（发件邮箱全称）
    password:             # 龙哥填（163 客户端授权码，不是登录密码）
    properties.mail.smtp.ssl.enable: true
```

未填 SMTP 时：发码端点返回 **8374**，起服与其他端点不受影响（`EmailCodeSender` 用 `ObjectProvider` 探测）。

**技术债已销账（M5.5，2026-11-12 下午）**：原 `SysUserAccountDao`（JdbcTemplate 直写 `sys_user`）已删除。
上游 PivotOS 补齐写契约 `IUserAccountFacade` 后，本插件改注入 `IUserFacade`（`getById` / `getByUsername`）+
`IUserAccountFacade`（`getByEmail` / `existsByEmail` / `existsByUsername` / `createAccount` / `checkPassword`），
密码哈希交回 system 侧计算，biz 只传原文（自动建档 `rawPassword` 留空，由实现侧生成随机串）。

**错误码口径**：契约抛 system 段 `USERNAME_EXISTS(2003)` / `EMAIL_ALREADY_USED(2007)`，biz 在
`createAccount()` 内转译成 `USERNAME_ALREADY_USED(8378)` / `EMAIL_ALREADY_USED(8377)`——**App 端只认 8xxx 段**，
上游将来调码值不影响客户端。登录失败 `2001`、账号停用 `2002` 沿用 system 码（与 `/app/auth/login` 同语义）。

**注意**：契约的 `getByEmail` 只过滤逻辑删除、**不过滤 `status`**，停用账号照样返回，biz 侧自己判 `status`。

## 验证记录（M5 + M5.5）

- 单测 **35 个全绿**（`EmailCodeServiceImplTest` 11 / `AppAuthServiceImplTest` 20 / `EmailCodeSenderTest` 4），SMTP/Redis/DB/契约全 Mock
- ArchUnit **22/22 绿**（含 A11 五条匿名登记）
- dev 起服（18080 旁路实例，避开 8080 上在跑的旧实例）+ curl 全链路：
  8373 格式 → 8374 未配 SMTP → 注册 200 → 账密登录（用户名/邮箱）200 → refresh 200 → logout 200 →
  退出后 refresh 8376 → 重复用户名 8378 / 重复邮箱 8377
- Flyway 在 `babylog_dev` 上从 v2.2.4 → **v2.2.5**（M5）→ **v2.2.6**（M5.5，`idx_email`）
- 真发信已验证（163 SMTP，日志 `验证码邮件已发送 to=a***@163.com`）；同邮箱 60s 二次发送 8371
- **邮箱验证码登录已实测**（2026-11-12 16:15，真实收件箱）：首登 `newUser=true` + 随机用户名 + 昵称取邮箱前缀 + 设备落行；
  同邮箱二次登录 `newUser=false` 且 userId/username 完全一致、deviceId 复用；同一个码二次使用 8370；该邮箱再注册 8377；
  重发即作废旧码（抄旧邮件的码会 8370）
- ⚠️ 163 自家发自家（发件箱 → 同一地址）投递不稳，连发多封会延迟；要稳定验证请换跨域收件箱
- **鉴权头注意**：app-user 体系的 token 走 `Authorization: <token>` **裸值**（`sa-token.token-name: Authorization`，无 `Bearer` 前缀）；
  `/app/auth/getInfo` 是 sys-user 端点，拿 app token 调必然 1002，验证 app 侧鉴权请用 `/app/babylog/auth/logout` 当探针

## 后续（M6+）

- 同步接口：`GET /app/babylog/sync/changes?since=`（按 server_updated_at 游标）+ `POST /app/babylog/sync/push`（version 乐观锁 + 逐条权限校验 + 设备级 workerId）
- 设备：注册/心跳/踢出、`worker_id` 分配（M5 只落了 `device_uuid` 与登录时 upsert）
- 基础数据：products/vaccines 增量下发；管理端 controller + 菜单 SQL
- 注意：本插件全部表**不加 tenant_id**（多租户不启用；BabyLog 部署配置不得开 tenant）
