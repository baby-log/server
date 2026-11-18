# babylog-plugin-biz

BabyLog 育儿记录的业务插件（V2.0 云基座，会话 M4 落地骨架与建表，会话 M5 落地 App 认证）。

- 契约模块：`babylog-plugin-biz-api`（`com.babylog.biz.api`）——错误码 `BabyLogErrorCode`（**8300-8419**，8xxx 段内空闲子段，migration/search/datainspect 已占 8000-8221）
- 实现模块：`babylog-plugin-biz`（`com.babylog.biz`）——表前缀 **`bl_`**（ArchUnit A8 白名单已登记）
- 路由规划：App 端 `/app/babylog/**`（app-user 体系，注解 + `StpAppUtil` 校验）、管理端 `/babylog/**`（`@SaCheckPermission`，M10 已落地，见「管理端第一批（M10）」节）

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
> **V2.2.7（M6）** 补 `worker_id`（设备级雪花机器位，0-1023）+ `uk(user_id, worker_id)`。
>
> **V2.2.7（M6）** 给 18 张同步表统一补 `user_id`（同步归属）+ `idx_user_sync(user_id, server_updated_at)`：
> 之前只有 `bl_children.owner_user_id` 有归属语义，其余 17 张没有归属列，拉取过滤与归属校验
> 只能靠 join children 推导。例外是 `bl_family_members`（M4 建表已有 `user_id`＝成员用户 id，
> V2.0 单用户模型下它就是归属列，只补索引不补列）。**这是第 4 处已文档化的结构性差异**
> （服务端有、App 端 Room 没有的列，客户端不感知，也不回传）。

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

## 同步接口（M6，会话 2026-11-12）

两个端点，全在 `/app/babylog/sync/**`，**类级** `@SaCheckLogin(app-user)`（没有一条匿名可用）。
token 与 M5 一致：`Authorization: <裸值>`。

| 端点 | 说明 |
|---|---|
| `GET /changes?since=&sinceId=&categories=&limit=` | 按服务端游标增量拉取，含软删除行（`deleted=1`） |
| `POST /push` | 批量推送，单次上限 **200 条**，逐条 version 乐观锁 upsert |

### 游标：双段 + 不透明

```
nextSince   本页最后一条的 server_updated_at（epoch millis，服务端编码）
nextSinceId 本页最后一条的 id（同毫秒 tiebreak）
```

- **为什么加 id**：MySQL 的 `NOW(3)` 在**一条语句内是常量**——一次 200 行的 push 可能共用一个毫秒值。
  只按时间戳推进，会在「同毫秒行数 > 单页上限」时把后一半永久跳过。
- **为什么叫不透明**：`server_updated_at` 是数据库会话时区的墙上时间，用 JVM 时区解释它；
  数据库与 JVM 时区不一致时 `nextSince` 的绝对值会整体偏移（不报错、不影响增量），
  所以**客户端只准原样回传**，不许拿它和手机时钟比较、不许自己加减。
  要「从头拉」传 `since=0`。
- 无新数据时 `nextSince` 原样退回入参（避免客户端把游标清成 0 变成全量重拉）。
- `since` 超前服务端时间 10 分钟以上会被钳到服务端时间（防把手机 millis 当游标传）。

### 分类过滤

`categories=FEEDING,CARE`（逗号分隔，大小写不敏感）。

| 分类 | 表 |
|---|---|
| FEEDING | feedings |
| CARE | diapers / sleeps / baths / spits / pump_sessions |
| HEALTH | measurements |
| MEDICAL | medications / vaccinations / follow_ups / visits / attachments |
| **不参与过滤**（任何请求都下发） | children / record_type_settings / family_members / member_permissions / products / reminders |

基础设施表必须随行下发——少了它们，拉回来的业务记录连 `child_id` 都对不上号。
分类在 V2.0 **不构成安全边界**（真正的边界是 `user_id` 归属），V2.1 才叠加 `bl_member_permissions`。

### 推送裁决

请求体按表分组，键是**与 Room 表名逐字相同**的表标识；行内是 camelCase 属性名。

```json
{ "deviceUuid": "…", "changes": { "feedings": [ { "id": 1, "version": 3, "childId": 9, … } ] } }
```

逐条裁决顺序（M16 定稿）：`id`/`version` 合法性 → 内置产品段（id ≤ 99）→ 服务端管理表（8350）→ 归属（家庭维度放行）→ 版本门禁 → 权限钩子（8343）→ 落库。

| 结果 | 含义 / 客户端处置 |
|---|---|
| `upserted` | 落库（新增，或推送 version > 库内 → 整行覆盖，last-write-wins，不做字段合并） |
| `skipped` | 库内 version ≥ 推送 version → 服务端优先；重推同一批（幂等重试）走这条 |
| `rejected[8343]` | 无该分类写权限（M16 点亮）→ 丢弃不重试；客户端应把该分类入口置灰（UI 隐藏） |
| `rejected[8344]` | 主键属陌生账号（不在家庭可见范围）→ **换 ID 重新入队**（同一个 ID 重推永远不会成功）；M16 起家庭内成员的行放行，只对家庭外账号报此码 |
| `rejected[8345]` | 内置产品（id 1~99）→ 丢弃，内置行由服务端下发 |
| `rejected[8350]` | 服务端管理表（family_members / member_permissions）→ 丢弃不重试（M16 提权漏洞收口）；这两表只从 changes 拉，永不进 outbox |
| `rejected[8342]` | 格式非法或库约束拒绝（必填列缺失等）→ 丢弃并上报日志，重试不会成功 |

协议级错误（整批失败）：空载荷/全表为空 → **8342**；总数 > 200 → **8341**；未知表标识 → **8347**；
未知分类标识 → **8346**。`user_id` / `server_updated_at` / 审计四件套一律忽略客户端传值，服务端按 token 盖章。

### 通用 SQL 为什么不用 MyBatis-Plus

18 张表 × 三套语句 × 动态列，用 MP 注解会退化成千行手写 SQL（且列名与实际表结构容易错位，
M5 栽过一次）；更要命的是 MP 的 `@TableLogic` 会把 `deleted=1` 的行从读写路径里滤掉，
而**软删除正是要同步的事实**（客户端先删再改同一条时，会「插入撞主键 + 更新 0 行」双双失败）。
故收口到 `JdbcSyncRecordStore` 一个类：列清单由实体字段反射推导（`@TableField` 优先），
配 `NamedParameterJdbcTemplate`。这是**本插件自己的 bl_ 表**，与 M5 那个跨插件直写 `sys_user` 的技术债不同性质。

### 错误码（同步 8340-8359 / 设备 8360-8369）

8340 版本冲突 · 8341 超上限 · 8342 记录非法 · 8343 无分类写权限（**M16 已点亮**）·
**8344 归属冲突（M16 起家庭内放行，只对可见范围外账号报）** · **8345 内置数据不可推** ·
**8346 分类非法** · **8347 表非法** · **8350 成员/权限表不可推（M16 提权漏洞收口）** ·
8360 设备不存在 · **8361 设备数超限（1024 台，workerId 用尽）**

## 验证记录（M6）

- 单测 **74 个全绿**（M5.5 的 35 个 + 本次 39：`SyncServiceImplTest` 18 / `SyncTableTest` 7 /
  `SyncCursorTest` 4 / `SyncTableSqlTest` 5 / `DeviceWorkerIdAllocatorTest` 4 /
  `AppAuthServiceImplTest` +1）
- **字段遮蔽修复**（`2b086df`）：`BlFamilyMember` 曾重复声明 `userId`，遮蔽基类同名列 →
  反射推导出重复列 → `Duplicate column`。已删子类字段、`collectFields()` 按属性名去重兜底，
  并由 `SyncTableSqlTest`（5 例：每表 insert 列不重复 / 版本门禁在最后 / 拉取 SQL 无 `deleted = 0`）锁死
- ArchUnit **22/22 绿**（新端点走类级 `@SaCheckLogin`，A11 无需登记匿名欠条）
- dev 起服（18080 旁路）：Flyway v2.2.6 → **v2.2.7**；**`bl_family_members` 首次失败已修复**
  （该表 M4 建表就有 `user_id`，首次脚本重复加列；MySQL DDL 不进事务 → 前 5 表已加列，
  用一次性 repair 回滚半成品 + `flyway.repair()` 后重迁移成功，临时修复类已删除、未入库）
- curl 全链路 **22 项校验全过**：7 条首推全写入 → 重推全部 skipped（幂等）→ 全量拉取 3 表 7 条
  （`userId` 不外泄、`deleted` 随行下发）→ `limit=2` 逐页拉无丢失/无重复 → version 门禁
  （更大的覆盖、更小的跳过、覆盖后值正确）→ 跨账号同 ID **8344** 且原数据未被改写 →
  内置产品 **8345** → 未知表 **8347** → 201 条 **8341** → 非法分类 **8346** →
  软删除可推送、可被拉到、可被再次覆盖
- `workerId`：两个新账号各自从 0 起；老设备行（无 workerId）登录时补分配且不改行 id
- 扩展 **5 项**（覆盖 22 项之外的边角）：其余 4 张表（`record_type_settings` / `products` /
  `family_members` / `member_permissions`）都能推 → `family_members` 的 `user_id` 由服务端盖章
  （客户端没传也落库）→ 布尔列 `True` 正确落成 `1` → 自定义产品（id > 99）可推 →
  缺 `family_id` 时该条 **8342**、同批另一条照常落库（**单条失败不污染整批**）

### 客户端接口约定（给 App 端 V2.0 改造用）

1. **数字字段在 JSON 里是字符串**（项目全局 `Long → String` 序列化，防 JS 精度丢失）：
   `id` / `version` / `nextSince` / `nextSinceId` / `childId` 都要按字符串解析。
2. 游标存两段（`nextSince` + `nextSinceId`），一起回传，别只存时间。
3. 客户端**不发** `userId`；服务端以 token 为准。
4. 内置产品（`is_builtin=1`，id 1~99）**不要进 outbox**，只从 changes 拉。
5. 收到 8343/8342/8345/8350 丢弃并记日志（不重试）；收到 8344 **换 ID 重推**——服务端契约已收口
   （M16：家庭内放行、只对家庭外陌生账号报 8344），App 端换 ID 重推的执行逻辑（重写引用列）归后续 App 会话。
6. `family_members` / `member_permissions` **不要进 outbox**（M16 起 push 整表拒收 8350）：
   只从 changes 拉、随家庭服务（建家/加入/改角色/踢出）变化；App 端本地 V1 遗留的占位成员行也不要推。
7. 权限矩阵的裁决权在服务端：客户端置灰/隐藏只是 UI 表达，不是安全边界（01B 红线）；
   推送前可用拉到的 member_permissions 行做 UI 预判，但服务端 8343 才是终判。

## 后续（滚动更新；最新清单见文末「后续（M17+）」）

- ~~设备管理：注册/心跳/踢出端点~~ ✅ M8 已落（见「设备管理与基础数据下发（M8）」节）
- ~~基础数据：products 增量下发~~ ✅ M8 已落（V2.2.8 seed + `/app/babylog/base/products`）；
  vaccine_schedule 下发等管理端界面好了再排
- ~~管理端 controller + 菜单 SQL~~ ✅ M10 已落（见文末「管理端第一批（M10）」节）
- ~~意见反馈通道~~ ✅ M10 已落（表 V2.2.4 早就建好，本批补 App 端点 + 管理端查看/回复）
- ~~V2.1 家庭共享（服务端侧）~~ ✅ M15 读侧 + M16 写侧已收口；App 端接线见文末「后续（M17+）」
- 注意：本插件全部表**不加 tenant_id**（多租户不启用；BabyLog 部署配置不得开 tenant）

## 设备管理与基础数据下发（M8，会话 2026-11-12，V2.0 收官批）

> prod 部署与内测包暂缓（龙哥 2026-11-12 拍板「继续向下开发」），本批先把 V2.0 服务端自身的
> 两块拼图补齐：设备管理端点、内置产品 seed 与下发通道。管理端 controller 留下一批。

### 设备管理（`/app/babylog/device/**`，app-user 鉴权）

- `GET /list`：本账号全部设备（含 KICKED，按最后活跃倒序）。`deviceUuid` 给全量——
  App 端靠「本机 deviceUuid == 行 deviceUuid」识别当前设备。
- `POST /{id}/kick`：置 `status=KICKED`。行不物理删、workerId 不回收（软删行仍占
  `uk(user_id, worker_id)`，回收会把重新登录变成唯一键冲突 500）。设备不存在或属于
  他人统一 **8360**（不给存在性信号）。
- **refresh 拦截**：KICKED 设备的 refresh 请求 → revoke + **8376**（不是新错误码——
  App 端 `AuthRepository` 只对 8376/1002 走「清凭证」分支，换码会被当成可重试网络错退避）。
- 重新登录**可复活**（ACTIVE）：登录本来要凭证，能过就是本人。已知宽限：复活后
  仍在 Redis 未过期的旧 refresh 恢复有效，等 V2.1 家庭共享上轮换 + 复用检测一起收。

### 基础数据下发（`/app/babylog/base/**`，app-user 鉴权）

- `GET /products?since=&sinceId=&limit=`：`is_builtin=1` 的内置产品增量下发。
  **返回结构复用 `SyncChangesVO`**（changes 键仍为 `products`，行内 camelCase、含
  deleted/version）——App 端复用 `/sync/changes` 的解析与 upsert，只是**独立存一对游标**。
- 为什么不并进 18 表同步通道：同步通道的归属过滤是 `user_id = 自己`，内置行是全局数据
  （user_id=0 占位）会被滤成不可见；单独通道 + 单独游标，不给统一逻辑开例外口子。
- 游标/limit 归一化已从 `SyncServiceImpl` 下沉 `SyncCursor`（两通道一套口径）。

### V2.2.8 seed（内置产品 32 行）

逐字对齐 App 端 `BuiltinProducts.kt`（跨仓库契约唯一事实来源）：30 在售（id 1~30）+
2 历史墓碑（id 91/92，`deleted=1`——老用户库里就有，服务端缺行会让「软删行不复活」
失去依据）。`user_id=0`、`device_id='builtin'`、ODKU no-op 幂等（与 App 端
`INSERT OR IGNORE` 同语义：后台将来软删的行不会被重放复活）。

## 验证记录（M8）

- 单测 **85 个全绿**（M6 的 74 + 11：`DeviceServiceImplTest` 4 / `BaseDataServiceImplTest` 4 /
  `SyncTableSqlTest` +1（builtin 通道只对有 is_builtin 列的表生成、不按 user_id 过滤）/
  `AppAuthServiceImplTest` +2（refresh 拦截 KICKED + revoke、设备行缺失时放行））
- ArchUnit **22/22 绿**（两个新 controller 均类级 `@SaCheckLogin`）
- dev 起服（18080 旁路）：Flyway v2.2.7 → **v2.2.8**，32 行落库；curl 12 项：
  base/products `since=0` 拉 32 行（30 在售 + 2 墓碑，id=10 伊可新字段逐字一致）→
  游标推进空页**退回原游标** → 未登录 **1002** → device/list 行完整 → kick → KICKED →
  被踢设备 refresh **8376** → 重新登录**复活 ACTIVE 且 workerId 不变** → 踢不存在 id **8360** →
  18 表同步通道**回归全绿**（push upserted / 幂等 skipped / changes 只见自己数据、
  **同步通道不含内置产品行**——base 与 sync 两通道隔离确认）
- 联调踩坑：测试载荷把 `feedings.type` 写漏，8342 的 message 会直接指出缺失列
  （`Column 'type' cannot be null`），排查很快——**报文校验的报错质量就是联调效率**。

## 后续（M12+）

- ~~管理端前端页面~~ ✅ M11 已落（pivotos-ui fork = gitee `baby-log/manager`，本地 `业余项目/ui`，
  develop：`views/babylog/{product,device,version,feedback}/index.vue` + `api/babylog/` 四模块 +
  `packages/types/src/babylog.ts`；V2.2.10 菜单解封，详见「管理端前端（M11）」节；
  疫苗时间表维护未做——bl_vaccine_schedule 无 seed，等运营数据有了再排）
- ~~意见反馈通道~~ ✅ M10 已落（App 匿名提交 + 管理端查看/回复；App 端提交 UI 留 M12）
- ~~⚠️ 框架遗留债：`@RepeatSubmit` 全应用静默失效~~ ✅ M15 已修（`RedisAutoConfiguration` 加
  `@AutoConfiguration(after = Redisson 两类)` + CacheManager 兜底，详见 M15 节；红线豁免一次记录在案）
- ~~App 端反馈提交 UI~~ ✅ M12 已落（设置页入口）
- ~~V2.1 家庭共享服务端~~ ✅ M15+M16 已收口：读侧家庭可见 + 写拦截 8343 + 8350 拒推 +
  显式行覆盖；App 端接线（家庭 UI / outbox 处置 / 8343·8344·8350 客户端处置）归 M17 候选首位；
  refresh 轮换 + 复用检测仍未上（M8 遗留宽限问题）

## 管理端第一批（M10，会话 2026-11-12，「后台管理第一批」剩余拼图）

> 路由 `/babylog/**`（sys-admin 体系，方法级 `@SaCheckPermission(type = StpSysUtil.TYPE)`），
> 与 App 端点（`/app/babylog/**`，app-user 体系）两套鉴权互不相通。
> 菜单 SQL **V2.2.9**（5000 号段：5000 目录 + 5010 产品 / 5020 设备 / 5030 版本 / 5040 反馈，
> 提交前全仓 grep 过号段无占用）。M/C 行 `visible='1'` 隐藏——pivotos-ui 页面 M11 才有，
> 权限字符已生效可先用 curl 验证；页面就绪后一条 UPDATE 翻回 '0'。

### 端点清单

| 模块 | 端点 | 权限字符 | 说明 |
|---|---|---|---|
| 产品 | `GET /babylog/product/page` | babylog:product:list | 固定 `is_builtin=1`，分类/品牌/品名/上架过滤 |
| 产品 | `GET /babylog/product/{id}` | babylog:product:query | 详情（墓碑行按不存在处理） |
| 产品 | `PUT /babylog/product/{id}` | babylog:product:edit | 编辑含上下架（enabled 0/1）；改动经 base 通道增量下发 |
| 产品 | `DELETE /babylog/product/{id}` | babylog:product:remove | 软删置墓碑，App 端 UI 隐藏，不可复活 |
| 设备 | `GET /babylog/device/overview?account=` | babylog:device:list | 账号摘要+全部设备；account=用户名或邮箱 |
| 设备 | `POST /babylog/device/{id}/kick` | babylog:device:kick | 复用 M8 `DeviceService.kick`，与本人移除同语义 |
| 版本 | `GET /babylog/version/page` | babylog:version:list | platform/status 过滤，versionCode 倒序 |
| 版本 | `POST /babylog/version` + `PUT /{id}` + `POST /{id}/publish` + `DELETE /{id}` | add/edit/publish/remove | DRAFT→PUBLISHED 单向；已发布锁定（8391） |
| 反馈 | `GET /babylog/feedback/page` | babylog:feedback:list | 状态过滤，提交时间倒序 |
| 反馈 | `PUT /babylog/feedback/{id}/reply` | babylog:feedback:reply | PENDING→REPLIED，盖回复时间 |
| 反馈(App) | `POST /app/babylog/feedback` | **匿名**（A11 ③轨） | 不要求登录；登录了自动带 userId；联系方式可空 |

### 必须记住的语义（写代码前先看）

1. **管理端改内置产品 = 动 base 通道的游标与版本**：`updateById` 前必须把实体里 SELECT
   出来的 `serverUpdatedAt` 置 null——MP 默认 NOT_NULL 策略会跳过该列，数据库
   `ON UPDATE CURRENT_TIMESTAMP(3)` 才会触发；不置空 = 游标不前进 = App 永远拉不到。
   `version` 靠乐观锁拦截器（实体 @Version 非空自动 SET version+1），并发冲突按 8348 处理。
2. **两通道隔离不破**：管理端只准碰 `is_builtin=1`（8349 拒绝用户自建行）；墓碑行
   被 @TableLogic 滤掉查不到（8348），「软删行不复活」天然守住。
3. **账号解析口径**（设备查看）：先 `IUserFacade.getByUsername` 精确查，查不到再
   `IUserAccountFacade.getByEmail(trim+小写)`（与 AppAuthServiceImpl 同口径），都 miss → 8362。
4. **版本公告单向状态机**：PUBLISHED 行改/删/再发布一律 8391——用户可能已经看过这版公告，
   改历史会破坏「同一 versionCode 对外口径稳定」；要改内容就发新 versionCode。
   App 端升级检查端点（拉最新 PUBLISHED）属 V3，本批不做。
5. **反馈匿名端点的 A11 口径**：方法体读 `LoginContext.getUserId()`（未登录返 null 落匿名），
   走「业务代码兜底」③轨，**不登记** ANONYMOUS_ENDPOINTS（登记制留给纯匿名、不碰上下文的端点）。
   ~~`@RepeatSubmit` 已挂但因上方框架遗留债暂不生效~~ → **M15 修复后已生效**（dev 冒烟实测：
   同报文反馈连发两次，第二次 1005）。

### 错误码（本批新增 6 个）

8348 产品不存在（含墓碑）· 8349 仅内置产品可管理端维护 · 8362 账号不存在 ·
8381 反馈内容 5-1000 字 · 8390 版本记录不存在 · 8391 仅草稿可修改/发布/删除

## 验证记录（M10）

- 单测 **109 个全绿**（M8 的 85 + 本批 24：`AdminProductServiceImplTest` 7 /
  `AdminDeviceServiceImplTest` 5 / `AdminAppVersionServiceImplTest` 5 /
  `FeedbackServiceImplTest` 5 / `AdminControllerPermissionContractTest` 2——
  后者锁「controller 权限字符与菜单 SQL 逐字一致」+「App 反馈端点保持匿名且挂 RepeatSubmit」，
  菜单或 controller 任一侧改字立即红）
- ArchUnit **22/22 绿**（A8 无新 @TableName、A10 无 last(、A11 新端点全有鉴权信号）
- dev 起服（18080 旁路）：Flyway v2.2.8 → **v2.2.9**，16 行菜单落库；curl 冒烟：
  匿名反馈提交 200 → 内容过短 1001 → 管理端四端点未登录全部 **1002**（鉴权闸生效）
- ⚠️ 踩坑一枚：admin-server fat jar 的 **up-to-date 误跳过**——biz 源码变了但
  `package -am` 秒过、jar 时间戳不刷新、内嵌 biz jar 还是旧的（外层 unzip -l 还看不到内嵌
  jar 里的类名，极易误判）。复现姿势：删掉 target 里的 jar + jar.original 再打包，
  并解出 `BOOT-INF/lib/babylog-plugin-biz-3.2.0.jar` 核对新类在场才算数
- 发现框架遗留债：`@RepeatSubmit` 全应用静默失效（见上方 M12+ 清单⚠️条目）

## 管理端前端（M11，会话 2026-11-12，pivotos-ui fork 落地四页）

> 仓库形态：fork pivotos-ui → gitee `baby-log/manager`（私有，本地 `业余项目/ui`，develop）。
> origin = gitee fork，upstream = 本地 PivotOS pivotos-ui 目录（框架升级照服务端口径走手工移植）。
> **未做全量改名**：`@pivotos/*` 工作区包名、`pivotos-ui` 目录名保持原样——改名收益低
> （包名仅 monorepo 内部引用）而 diff 巨大，不值得；业务增量全部收在
> `views/babylog/`、`api/babylog/`、`packages/types/src/babylog.ts` 三个新增落点，与上游冲突面最小。

### 交付

| 东西 | 说明 |
|---|---|
| `packages/types/src/babylog.ts` | M10 端点契约类型；**Long 一律 string**（id/version/serverUpdatedAt/lastUsedAt/userId），Integer 开关（enabled/versionCode/forceUpdate）保持 number |
| `apps/admin/src/api/babylog/` | product（无新增端点——内置行只由 seed 产生）/ device / version / feedback 四模块，错误码语义写 JSDoc |
| `views/babylog/{product,device,version,feedback}/index.vue` | 既有插件页同款范式（useTablePage + YSearchForm/YTable/YForm/YDialog + v-hasPermi 按钮级权限） |
| `V2.2.10__babylog_admin_menu_visible.sql` | 菜单解封：5000/5010/5020/5030/5040 五行 visible 翻 '0'；5010 icon 'shopping'→'shop'（前者不在前端图标注册表）。权限字符零改动 |

### 必须记住的判断

1. **菜单 component 路径 = views 相对路径**：`babylog/product/index` → `views/babylog/product/index.vue`
   （`stores/permission.ts` 的 `import.meta.glob('../views/**/*.vue')` 构建期静态收集，纯文件路径映射，
   前端不需要注册路由）。**defineOptions name 必须等于路由名**（`/babylog/product` → `BabylogProduct`，
   规则在 system 侧 `MenuServiceImpl#toRouteName`：全路径分段大驼峰）——keep-alive/标签页刷新靠它。
2. **dev 起服联调口径**：vite 代理 `/api` → `VITE_API_BASE_URL`（默认 8080 龙哥旧实例），
   旁路联调用 `VITE_API_BASE_URL=http://127.0.0.1:18080 pnpm dev`；后端超管种子账号
   `admin` / `admin123`（V1.0.1 初始化数据，sys 体系登录 `POST /system/auth/login`）。
3. seed 的产品行 `enabled=0` 是**故意的**（对齐 App 端 BuiltinProducts「默认关」，用户自己开启），
   不是管理端 bug；管理端文案沿用服务端 @Schema 的「在售/下架」口径。
4. V2.2.9 在 dev 已应用（Flyway checksum 锁死不能原地改文案），翻牌走新号 V2.2.10；
   test/prod 未部署过 M10 jar，届时 V2.2.9+V2.2.10 按序一次到位。

## 验证记录（M11）

- 前端 `pnpm run build`（vue-tsc 类型检查 + vite build）全绿；eslint 新增文件零告警；
  dist 产物含四个 babylog 异步 chunk
- biz 单测 **109 全绿**（含 AdminControllerPermissionContractTest，纯反射比对不解析 SQL，
  迁移新增不影响）；dev 起服 18080：Flyway v2.2.9 → **v2.2.10** 应用成功
- admin 登录 → `/system/menu/routers`：BabyLog 运营目录 + 四子菜单 hidden=false、
  component/name 与前端逐字对齐、icon 归一为 shop
- 四端点契约冒烟（超管 token，前端将用的确切参数形态）：product/page（FORMULA 过滤、
  Long 字符串口径核对）→ version 全 CRUD（create 返回 id 字符串、update、delete、
  8390 报错路径）→ device/overview（account=admin 空设备表正常）→ feedback/page 状态过滤
  + reply（PENDING→REPLIED、replyTime 盖章）
- 遗留：dev 库留了一条 versionCode=99 的软删测试行与一条已回复的探针反馈行，可清可留；
  test/prod 环境部署仍排队龙哥 1Panel（现需带 M8+M10 jar + 前端 dist）

## 家庭共享与读侧权限点亮（M15，会话 2026-11-13，V2.1 第一批）

> 部署与内测包仍暂缓（M14 P1），本批只对 dev 18080 起服验证。开场三拍板（龙哥确认）：
> ①8344 换 ID 重推确认归 V2.1（M16 提示词列主候选，本会话不动 outbox 处置逻辑）；
> ②家庭模型按现有三表落地，**零 Flyway 新号**（v2.2.10 后无新迁移）；③权限点亮选
> 方案 A「读侧家庭可见」——成员能真拉到家庭数据，push 写拦截留 M16。

### 家庭管理端点（`/app/babylog/family/**`，类级 `@SaCheckLogin(app-user)`，token 裸值）

| 端点 | 说明 | 错误码 |
|---|---|---|
| `POST /` | 创建家庭：默认 OWNER + 回填 owner 名下宝宝的 family_id + 生成默认权限行 | 已在家 8303 |
| `GET /` | 我的家庭（inviteCode 仅 OWNER 可见） | 无家庭 8300 |
| `POST /invite/reset` | 重置邀请码（8 位无歧义字符，旧码立即失效） | 非 OWNER 8304 |
| `POST /join` | 凭码加入（大小写不敏感；码错/解散/软删统一 8301） | 重复加入 8303 |
| `GET /members` | 成员列表（含 REMOVED，按加入时间正序） | 无家庭 8300 |
| `PUT /members/{id}/role` | 改角色（CAREGIVER/VIEWER；OWNER 不可指定不可转让） | 8302/8304 |
| `POST /members/{id}/kick` | 踢出：status 置 REMOVED，**行保留、不吊销 token** | 8302/8304 |

**踢出的生效机制**：不删行不吊销登录（对方仍是独立账号）——`FamilyAccessScopeResolver`
只认 ACTIVE 成员行，被踢者**下一次拉取**立即回落「单用户范围」（只剩自己的数据）；
幂等（踢 REMOVED 行直接 200 空操作）；凭新邀请码可再加入（新成员行）。

### 读侧权限（/sync/changes，01B 第六节）

- 拉取过滤从 `user_id = :userId` 改 `user_id IN (:userIds)`：集合由
  `SyncAccessScopeResolver` 推导——无家庭（含被踢后）= {自己}（**行为与 V2.0 零变化，回归锁**），
  家庭成员 = 家庭全部 ACTIVE 成员（成员拉得到 owner 的宝宝/记录/成员行/权限行）
- 拉取分类 = 客户端请求 ∩ 成员可读分类（管道已建；三个角色默认读全开，M15 实际不收窄）
- **`bl_children.family_id` 转为服务端独占列**（`SyncTable.CHILDREN.serverOwnedProperties`）：
  不进载荷不回传——否则客户端重推宝宝行会把回填的 family_id 覆写成 null
  （README 上文「三处结构性差异」#2 的「不感知不回传」就此落死）
- 权限行生成：加入/创建时按 01B 角色默认矩阵落 `bl_member_permissions`（家庭宝宝 × 四分类，
  归属 user_id = 该成员，其 App 拉得到自己的矩阵）；**改角色 = 原地更新**已有行 can_write
  （version+1 同步可见）——uk(member_id, child_id, category) 不含 deleted，
  软删重插必撞 DuplicateKey（dev 冒烟实测踩过）；缺行（新宝宝）补插

### 写侧（push，M16 已点亮）

`OwnerFullAccessPermissionGuard` 已删，换 `MemberPermissionGuard`：按 `SyncAccessScope` 的
可写分类矩阵真实裁决，M16 起 `WRITE_ENFORCEMENT_ENABLED=true` 常开，越权写单条 8343 拒绝
（详见下方「M16」节）。

### @RepeatSubmit 框架债修复（M14 P3 红线豁免，双 commit）

1. `@AutoConfiguration(after = {RedissonAutoConfigurationV2, V4})`——类级
   `@ConditionalOnBean(RedissonClient)` 按字典序在 Redisson 之前求值 → 整类跳过 →
   RepeatSubmitChecker 缺失（M10 发现的静默失效）。方法级条件不可替代（求值时机相同）。
2. **连带件（必修，否则起不来）**：类真正装配后 `@EnableCaching` 首次生效，全应用从没有过
   CacheManager → APPLICATION FAILED TO START。补 `@ConditionalOnMissingBean` 的
   ConcurrentMapCacheManager 兜底（Boot 同款默认，应用自带即让位）。不能指望 Boot 的
   CacheAutoConfiguration 兜——排序在本类之后、同样的时序坑。

单测锁：`RedisAutoConfigurationTest`（after 注解含 Redisson 两类 + 有/无 client 的装配行为）。

## 验证记录（M15）

- biz 单测 **134 个全绿**（M11 的 109 + 25：`FamilyServiceImplTest` 12 / `InviteCodesTest` 4 /
  `FamilyAccessScopeResolverTest` 5 / `MemberPermissionGuardTest` 2 / `SyncServiceImplTest` +1（
  家庭范围拉取）/ `SyncTableSqlTest` +1（children familyId 独占））
- starter-redis 单测 4/4 绿；ArchUnit **22/22 绿**（新 controller 类级鉴权，A11 无欠条）
- dev 起服（18080）：**无新 Flyway 迁移**（v2.2.10 后零 DDL，符合开场拍板）；
  curl 冒烟 **49 项全绿**（脚本 `/tmp/m15-smoke.zsh`，账号/雪花 ID 运行时生成）：
  双账号注册 → 匿名 1002 → 无家庭 8300/零数据基线 → A 推宝宝+喂养 → 建家（OWNER/8 位码/
  回填生效）→ 重复创建 8303 → 错码 8301 / 小写空格加入成功 VIEWER / 重复加入 8303 →
  **B 拉取看到 A 的宝宝+喂养+成员行+权限行（读侧家庭可见）** → VIEWER 权限行全只读 →
  B 改角色/踢人被拒 8304 → A 改 B 为 CAREGIVER（B 权限行原地更新可写=2）/改自己 8304 →
  踢出后 B myFamily 8300、拉取看不到家庭数据（REMOVED 立即生效）→ 重复踢幂等 →
  旧码再加入成功 → 重置码后旧码 8301 → A 回归拉取不变 + 重推宝宝覆盖（family_id 不被
  客户端载荷覆写）→ **@RepeatSubmit 生效（同报文反馈第二次 1005）**
- 冒烟踩坑一枚（已修成产品行为）：改角色最初实现「软删权限行+重插」撞
  `uk_member_child_category`（uk 不含 deleted），改原地更新后语义更干净（行 id 稳定、
  同步语义是「权限值变了」而非「删旧插新」）
- **push 被会话安全策略拦（已知坑）**：AI 会话里 `git push` 被判数据外传拒绝，M15 的
  5 个 commit 只落本地，**龙哥自行执行 `git push origin develop` 双推**

## push 写拦截与家庭归属放行（M16，会话 2026-11-13，V2.1 第二批）

> 开场两拍板（龙哥确认）：①越权写口径 = **整条拒绝 8343**（进 rejected 列表，客户端出队
> 不重试，与 8345/8342 同款丢弃类语义；不采纳静默跳过）；②本会话排查发现的**提权漏洞**
> （family_members / member_permissions 行归属恰为成员自己，归属校验拦不住整行重推自我改
> OWNER / 翻转 canWrite）= **整表拒推新码 8350**（同 8345 下发型语义）。

### 交付四件

1. **写拦截点亮**：`MemberPermissionGuard.WRITE_ENFORCEMENT_ENABLED=true`；guard 抛的 8343
   由 `SyncServiceImpl` 收进**单条 rejected**（不拖累整批）；guard 签名换传 `SyncAccessScope`
   （push 一次解析，省掉逐条 resolveAccess 的三表查询）
2. **家庭维度归属放行**：归属校验从「行属当前用户」放宽为「行属当前用户或行属
   visibleUserIds 内成员」——成员编辑家人创建的行不再 8344，ODKU 更新段不动 user_id
   （STAMPED 列只在 INSERT 盖章），归属不会被编辑者抢走；范围外陌生账号仍 8344（换 ID
   重推语义由既有码消息承载，服务端契约就此收口；App 侧换 ID 重推执行逻辑归后续 App 会话）
3. **8350 整表拒推**：`SyncTable.isServerManaged()`（恰好 family_members / member_permissions
   两张，`SyncTableTest` 清单锁死）；预检段拒收，与内置产品 8345 同款；App 端后续把两表移出
   outbox（本地 V1 遗留占位成员行也不要推）
4. **权限行显式行覆盖**：M15 的「只加宽不减窄」废止——某分类存在任一显式行则该分类完全
   由行裁决（跨宝宝 OR 聚合），无行按角色默认兑底，减窄立即生效（OWNER 撤回授权下一次
   裁决即被拦）；脏数据口径维持 M15（取最新 + 告警不抛）

### 基础设施表的边界分工（写清楚防下次重设计）

| 表 | push 边界 |
|---|---|
| family_members / member_permissions | **整表拒推 8350**（服务端管理表，只拉不推） |
| children / record_type_settings / products / reminders | 归属校验（8344 家庭维度放行）；业务分类表另加 8343 |

### 验证记录（M16）

- biz 单测 **146 个全绿**（M15 的 134 + 12：`MemberPermissionGuardTest` 重写 7 例 /
  `FamilyAccessScopeResolverTest` +3（加宽/减窄/读收窄/兑底）/ `SyncServiceImplTest` +3
  （家庭归属放行 / 8350 拒推 / guard 异常收单条）/ `SyncTableTest` +1（服务端管理表清单锁））
- ArchUnit **22/22 绿**；fat jar 复核（删 jar 重打包 + 解出 biz/biz-api 内嵌 jar 核对
  8350 与 isServerManaged 在场）
- dev 起服（18080）：无新 Flyway 迁移；curl 冒烟 **31 项全绿**（脚本 `/tmp/m16-smoke.zsh`，
  A/B/C 三账号运行时生成，C 是家庭外陌生账号）：无家庭基线 3 条全过（回归锁）→
  B VIEWER 推尿布+喂养同批双条 8343（文案「无该分类的写权限」、0 落库、不拖累整批）→
  B 推 member_permissions / family_members（role=OWNER 自我提权载荷）均 8350 且权限行
  未被污染 → A 改 CAREGIVER 后 B 重推同条通过 → **B 编辑 A 的喂养行成功（不再 8344，
  A 拉到 amountMl=150 新值）** → C 推自己的行落库、B 推 C 的行 8344（文案「请更换 ID 后重试」）
  且 B 拉不到 C 的行 → A 改回 VIEWER 后 B 再推 8343（减窄立即生效）→
  C/A 拉取回归锁（无家庭零变化、OWNER 不变）
- 遗留：dev 库留着 m16 冒烟三账号的家庭与记录探针数据，可清可留；8350 改变了 App 端契约
  （两表不再可推），**App 侧移出 outbox 的会话必须排上**（M17 候选首位）

## 后续（M17+）

- ~~V2.1 家庭共享服务端：写拦截/归属放行/8350/显式行覆盖~~ ✅ M15+M16 已收口（读侧 M15、写侧 M16）
- ~~App 端家庭 UI 第一批~~ ✅ M17 已收口（BabyLog-Android@develop，家庭 UI + rejected 处置 + 8344 换 ID 重推）
- ~~服务端家庭退出/解散端点~~ ✅ M18 已落（见下方「M18」节）
- ~~改角色/踢出后增量游标不前进（M17 冒烟抓到的真 bug）~~ ✅ M18 P0 已修（updateById 前置空 serverUpdatedAt，见 M18 节）
- **refresh token 轮换 + 复用检测**（设备踢出复活宽限，M8 遗留）：M18 评估**单独排会话**——
  轮换会改 App 端契约（/refresh 响应须回新 refresh token，客户端要持久化新值），
  无客户端承接的半成品不上线；且要动 RedisRefreshTokenStore 存储结构（token 家族/已用墓碑）
- 权限按宝宝粒度裁决：当前跨宝宝 OR 聚合，payload 已带 childId，钩子签名已预留
- 疫苗时间表维护（bl_vaccine_schedule 无 seed，等运营数据）
- 注意：本插件全部表**不加 tenant_id**（多租户不启用；BabyLog 部署配置不得开 tenant）

## 家庭退出/解散与同步游标修复（M18，会话 2026-11-13，V2.1 服务端收尾批）

### P0：改角色/踢出后同步游标不前进（M17 真机抓到的 bug）

根因：`changeRole`/`kick`/`regeneratePermissionRows` 的实体从 SELECT 出来 `serverUpdatedAt`
非空，MP NOT_NULL 策略把旧值显式写回 SET 段 → MySQL `ON UPDATE CURRENT_TIMESTAMP(3)`
不触发 → 成员行/权限行拿不到新游标 → 增量拉取 0 行（M15/M16 冒烟只做 `since=0` 全量拉，
漏了这条路径；M17 真机「改角色后权限置灰不刷新，直到退出重登」即此）。修法与 M10 管理端
产品维护同口径：**同步表 updateById 前一律 `setServerUpdatedAt(null)`**。同类隐患全插件
grep 过：其余 updateById 都在服务端专属表（bl_family/bl_device/bl_feedback/bl_app_version，
无游标列），只有这三处中招。**新增任何同步表 updateById 都要照此办理**（类注释已写死）。

### 退出/解散端点（`/app/babylog/family/**` 续）

| 端点 | 说明 | 错误码 |
|---|---|---|
| `POST /leave` | 成员自助退出：自己成员行置 REMOVED，语义与踢出完全对齐（行保留、不吊销 token、权限行不动）；退出者立即回落单用户范围 | 无家庭 8300 · OWNER 8305 |
| `POST /dissolve` | OWNER 解散：家庭行置 DISSOLVED（旧邀请码 join → 8301）、全部 ACTIVE 成员行置 REMOVED（含 OWNER 自己）、宝宝 `family_id` **清空**（2026-11-13 龙哥拍板：与 create 回填对称，解散后重建家庭宝宝重新归入新家庭） | 无家庭 8300 · 非 OWNER 8304 |

**新错误码 8305**（`OWNER_LEAVE_FORBIDDEN`「家庭创建者不能退出，请先解散家庭」）：
OWNER 是不可转让兜底角色（01B），直接退出会把家庭留在「有成员却无人可管理」的锁死态。

**幂等语义**：重复 leave/dissolve → `requireActiveMembership` 找不到 ACTIVE 行 → 8300
（与 myFamily 无家庭同语义）；被踢/退出/解散三者殊途同归，都走 8300。

### App 端契约影响面（写给 M19+ 的 App 会话）

- **服务端只保证语义一致，App 端零新分支可复用 M17 已有逻辑**：退出（leave/被踢/解散）
  后客户端下一次 `myFamily` 得 8300 → 清 `familyMemberId`/`familyRole` 身份 → 回落单用户
  ——M17 已实现该分支，服务端返回口径与之对齐；
- `FamilyApi` 七端点 → **九端点**（+leave/+dissolve），UI 上：非 OWNER 成员列表挂「退出家庭」，
  OWNER 挂「解散家庭」（receive 8305 时引导文案「创建者请先解散」）；解散须二次确认；
- 退出后成员的下次**增量拉取**能拉到自己成员行 REMOVED（M18 游标修复保证），
  客户端按 M17 的镜像行处理即可；旧邀请码 join 一律 8301（码无效路径，已有处理）。

### 验证记录（M18）

- biz 单测 **151 个全绿**（M16 的 146 + 5：FamilyServiceImplTest 增量游标回归锁 2 例
  （changeRole/kick 进 updateById 的实体游标列必须 null）+ leave/dissolve 3 组 5 例）
- ArchUnit **22/22 绿**（新端点走类级 `@SaCheckLogin`，A11 无欠条）；fat jar 复核
  （删 jar 重打包 + 解内嵌 biz jar 核对 leave/dissolve/8305 在场）
- dev 起服（18080，先杀 M17 遗留旧实例）：无新 Flyway 迁移；curl 冒烟 **35/35 全绿**
  （脚本 `/tmp/m18-smoke.zsh`，账号/雪花 ID 运行时生成，**全程用「记游标→增量拉」
  而非 since=0 全量拉**——M15/M16 的盲区就是本批 P0 的藏身处）：
  - **P0 核心**：B 全量拉记游标 → A 改 B 为 CAREGIVER → B 增量拉 family_members/
    member_permissions 都 >0 行（修前=0），role=CAREGIVER、FEEDING/CARE 行 canWrite=1；
  - A 踢 B → B 增量拉到自己成员行 REMOVED（kick 同款游标前进）；
  - B 再加入 → leave 200 → myFamily 8300 → 重复 leave 幂等 8300；OWNER leave 8305；
  - A dissolve 200 → myFamily 8300 → 重复 dissolve 幂等 8300 → 旧码 join 8301 →
    A 增量拉看到宝宝行（family_id 清空触发 ON UPDATE）+ 自己成员行 REMOVED；
  - A 重建家庭 → C 凭新码加入 → C 全量拉看到 A 的宝宝（family_id 回填生效）+
    自己 4 条只读权限行；C（VIEWER）dissolve 8304
- 冒烟踩坑一枚（脚本侧）：权限镜像行**全家可见**，断言必须按自己的 memberId 过滤
  （M17 判断 3 原话）——C 全量拉 member_permissions 是 12 行（A 旧家 4 + A 新家 4 +
  C 自己 4），数全集必误报

## 小程序登录与今日汇总（M20，会话 2026-11-13，V2.2 第一批）

V2.2 微信小程序启动（家庭轻入口）。**架构拍板：小程序走 app-user token，家庭/同步端点零改动**——
system 插件早有 wx-mini 登录编排（`/mini/auth/**` 发 `wx-mini-user` token），但 biz 全部端点是
`@SaCheckLogin(app-user)`，Sa-Token 多账号体系 token 互不相认；故 biz 补第四条登录路，小程序在
服务端眼里就是「一台设备」（bl_device 落行、拿 workerId），家庭/同步/后续快速记录全链路直接复用。

### 端点（新增 2 个，现有端点零改动）

| 端点 | 鉴权 | 说明 |
|---|---|---|
| `POST /app/babylog/auth/mini/login` | 匿名（A11 已登记） | wx.login code → code2session（system 契约）→ 查 sys_social_user 绑定 → 未绑定首登自动建档（`wx_` 前缀随机用户名、随机密码、昵称「微信用户」）→ completeLogin 管线发 app-user 双 token + bl_device + workerId。unionId 遇到就后补（值不变不调契约） |
| `GET /app/babylog/summary/today` | `@SaCheckLogin(app-user)` | 只读聚合：按宝宝今日喂养（次数/ML 合计/母乳/辅食）/尿布（湿/脏）/睡眠（时长/进行中截断到当前）+ 最近动态（三类合并倒序前 10 条，透传 createdBy 成员显示名）。可见范围与读权限复用 `SyncAccessScopeResolver`；分类读权限显式减窄 → 对应聚合段返回 null（不可绕过）。「今日」= 服务端 JVM 时区当天 00:00 起 `started_at` 窗口，跨夜睡眠不算今天 |

### 契约依赖（system-api 新增，M20；同时是上游 PivotOS 同步素材）

- `IWechatMiniFacade#code2Session(app, code)` → `WechatSessionDTO`（openId/unionId）；
  system 插件适配 Bean `WechatMiniLocalFacade` 委托 `WechatMiniService`
- `ISocialAccountFacade#findByChannelOpenId/bind/updateUnionId` → `SocialBindingDTO`；
  适配 Bean `SocialAccountLocalFacade` 委托 `SocialUserService`（sys_social_user，channel=wechat-mini）
- **上游同步提示词见 BabyLog-Android/PROGRESS.md M20 节**——两仓库文件内容逐字一致，零冲突
- **配置位**：`babylog.wechat.miniapp.configs.babylog.appid/secret`（yml，龙哥自填；
  未配置时 mini login 返回 8395，其他端点不受影响）

### 错误码（小程序认证 8395-8399 段）

8395 小程序未配置（对齐 8374 MAIL_NOT_CONFIGURED 先例）· 8396 code 无效/已使用/微信接口失败
（客户端拉新 code 重登）· 8397 绑定冲突（openid 绑了别的账号 / 孤儿绑定 / 并发抢绑——换绑是管理
动作，登录路不做）。system 段 2080-2083 在 biz 收口转译，客户端只认 8xxx。

### 验证记录（M20）

- biz 单测 **165 个全绿**（151 基线 + 9 mini 登录 + 5 summary；MP lambda cache 需
  `TableInfoHelper.initTableInfo` 引导，FamilyServiceImplTest 同款）
- ArchUnit **22/22 绿**（A11 登记 `BabyLogAuthController#loginByWxMini`）
- dev 18080 冒烟 `/tmp/m20-smoke.zsh` **28/28 全绿**：mini login 未配置 8395 / 空 code 拒；
  账密注册→push 五行（child+2喂+尿+进行中睡眠）→ summary 断言 count/totalMl=120（SOLID 30g 不计）/
  wet/soiled/durationS≈2400/ongoing/recent[0]=FEEDING+妈妈记的；无 token 1002；无宝宝账号空集不报错
- **真 code2session 联调待 AppID**（小程序还在 mp 后台初始化；yml 填好 appid/secret 后重启即通）
- commits：`461dc1d`（system 契约）/ `c34d0aa`（mini 登录）/ `5dd592e`（summary）

## 小程序第二批配套（M21，会话 2026-11-13，V2.2 第二批）

小程序侧消费 M15/M18 存量家庭端点（join/members/myFamily），**服务端唯一改动一处**：

- **修 M15 遗留的成员来源写死**：`FamilyServiceImpl.insertMemberRow` 原来恒写 `user_type="APP"`
  （M15 写就时只有 App 用户）——小程序家人加入后成员名单会错标来源。改为按用户名 `wx_` 前缀
  （M20 首登自动建档约定）推导：`BlFamilyMember.USER_TYPE_WX_MINI`，否则 `USER_TYPE_APP`；
  只在插成员行时定值（成员行是同步表，不做实时推导、不回填历史行）。
  App 端无感知（userType 本就只用于展示来源标签）。
- 小程序侧判断记录（inviteCode 仅 OWNER 且非 OWNER JSON 键缺省、members 含 REMOVED 须过滤、
  8300 未加入是正常态、workerId=0 是首台设备合法分配）→ `BabyLog-Mini/PROGRESS.md` M21 节。
- 验证：biz 单测 **166 绿**（+1：wx_ 前缀 joiner → 成员行 WX_MINI）+ ArchUnit 22 绿 +
  dev 冒烟 `/tmp/m21-smoke.zsh` **28/28**（A 建家+推数据 → wx_ 前缀 B join → 家庭视角
  summary + members 来源标记 + 8301/8303 契约探针）。

## 小程序收尾批配套（M23，会话 2026-11-13，V2.2 收口，**服务端零改动**）

M23 没有新增端点、没有新错误码、没有新 Flyway 迁移——小程序这一批消费的两个端点都是**存量**的，
本节点只记契约口径，避免下次误以为是新功能。

| 端点 | 出处 | 小程序用法 |
|---|---|---|
| `POST /app/babylog/auth/logout` | **M5**（`BabyLogAuthController#logout`，`@SaCheckLogin(app-user)`） | 退出登录：body 带 `refreshToken` → `logoutCurrent()` 吊销当前 access + `revoke(refreshToken)` **双吊销**；不带只注销 access |
| `PUT /app/system/profile` | **system 插件**（`ProfileController#update`，`LoginContext` 解析三套账号体系） | join 家庭**前**改 `sys_user.nickname`，解决小程序首登昵称固定「微信用户」重名。body 全字段可选，只传 `nickname` 其余列不动 |

**两个必须记住的口径**：

1. **logout 不是踢设备**：`bl_device` 行保持 ACTIVE、workerId 不回收 → 下次静默登录复用同一设备行
   （冒烟 4c/4e 锁死：workerId 仍 0、device status 仍 ACTIVE）。被踢（KICKED）才走 8376 + 复活语义。
2. **成员显示名是 join 时快照**：`FamilyServiceImpl#resolveDisplayName` 取插行那一刻的 `sys_user.nickname`
   落 `bl_family_members.display_name`。所以「改昵称」必须在 join 之前；已加入后改 sys_user.nickname
   **不会**刷新成员行与历史记录显示名（也不回填）。将来要支持「加入后改名」，得在服务端补一条
   「成员行显示名刷新」口径（已作为 M24 备选 B 记录）。

**验证**：`/tmp/m23-smoke.zsh` **21/21 全绿**（运行时账号，biz 侧代码未改，故未重跑单测/ArchUnit）：
A 注册+推宝宝+建家 → B（wx_ 前缀，默认昵称「微信用户」）先 `PUT /app/system/profile` 改「奶奶」
再 join → 成员行 displayName=奶奶、userType=WX_MINI → logout → 旧 access **1002**、旧 refresh **8376**
→ 重新登录 userId 不变、workerId 仍 0、家庭身份仍 VIEWER、设备行仍 ACTIVE → 对照组 C 不改昵称
直接 join，displayName 仍是「微信用户」 → 未登录 logout 被 **1002** 拦。
另：`POST /app/babylog/family`（建家）**无尾斜杠**，带 `/` 会 1004，冒烟脚本已按无斜杠写。

## 后续（M21+）

- ~~V2.2 第二批：加入家庭（邀请码 join）/ 成员动态页~~ ✅ M21 已完成（小程序侧；见
  `BabyLog-Mini/PROGRESS.md`；服务端配套改动见上方 M21 节）
- ~~V2.2 第三批（M22）：快速记录（push 单条 FEEDING/CARE，VIEWER 置灰预判，雪花 ID 用
  登录响应 workerId 生成）~~ ✅ M22 已完成（**纯客户端批次，服务端零改动**——`/sync/push`
  与 rejected[8343] 均为 M6/M16 存量契约；小程序侧见 `BabyLog-Mini/PROGRESS.md`；
  dev 冒烟 27/27：`/tmp/m22-smoke.zsh`，含 VIEWER 8343 → 升级落库 → 汇总+1 →
  recent 标注 memberId → 降回 VIEWER 立即拦）
- ~~V2.2 收尾批（M23）：小程序 logout 接 M5 存量端点、「微信用户」显示名区分~~ ✅ M23 已完成
  （**服务端零改动**；消费 `/auth/logout` + `/app/system/profile`，冒烟 `/tmp/m23-smoke.zsh` 21/21；
  见上方「小程序收尾批配套（M23）」节）
- **V2.2 客户端四件套已全部收口**（加入家庭/今日汇总/快速记录/成员动态）——小程序侧后续只剩
  提审备案等发布动作（V3 前不动）
- refresh token 轮换 + 复用检测（评估结论见上方 M17+ 清单：改 App 契约，单独排会话）
- 权限按宝宝粒度裁决；疫苗时间表维护；多租户保持关闭（bl_ 表无 tenant_id）
- App 端「谁记的」端到端与 FamilyApi leave/dissolve 接线 ✅ M19 已全部完成（上方 M18 节
  「App 端契约影响面」已消费，勿再排）


## 喂养计时器（M37，会话 2026-11-14，V2.3 第一批：App+服务端同批）

Room v9 计时五列的服务端半边。与 Android 端（见 BabyLog-Android PROGRESS「已完成（会话 M37）」节）
同批交付。

### 改动

| 东西 | 说明 |
|---|---|
| Flyway **V2.2.11**（`V2.2.11__bl_feedings_timer.sql`） | `bl_feeding` 加 `in_progress` TINYINT NOT NULL DEFAULT 0 / `ended_at` BIGINT / `total_seconds` INT / `active_seconds` INT / `pause_events` VARCHAR(2048)。bl_ 表惯例**不加 tenant_id**；版本号落笔前 grep 已占用 |
| `BlFeeding` 五字段 | `inProgress` 用 **Integer**（与 TINYINT 语义对齐：JSON 里是 0/1 不是 true/false——App 端解析一律 `JsonExt.boolOrNull`，M19 红线同口径）；`pauseEvents` 是事件流 JSON 文本，服务端**只透传不算** |
| `SyncTable.pullPredicate`（新字段） | FEEDINGS 注册 `"in_progress = 0"` 拉取谓词——**进行中的计时半成品不下发**。客户端红线是 in_progress=1 永不进 outbox，这道谓词是服务端侧兜底（防御不合规客户端）；谓词拼在归属/游标条件之后（`SyncTableSql.buildSelectChangesSql`） |

### 契约注意（写给 App 端与会后维护）

- push 侧**不拦** in_progress=1（谓词只管拉不管推）：不合规客户端推上来的半成品会落库但永远不下发，等价无害；
- 新列随 `changes` 正常下发：`inProgress` 是 **JSON 整数 0/1**（不是布尔），`endedAt` 是字符串形态
  Long（全局 Long→String），`totalSeconds`/`activeSeconds` 是普通整数，`pauseEvents` 逐字透传；
- 现有行（旧客户端推的无计时列报文）新列落 NULL/`in_progress=0`，拉下去旧客户端直接忽略，无兼容问题。

### 验证记录（M37）

- biz 单测 **167/167 全绿**（+1：`SyncTableSqlTest.feedingsPullExcludesInProgressHalfDoneRows` 锁死
  半成品行不进拉取 SQL）；ArchUnit **P0 18/18** + commons 绿（零新端点，A11 无欠条）；
  fat jar 复核（删 jar 重打包 + 解内嵌 biz jar 字节码核对五字段/pullPredicate/V2.2.11 在场）
- dev 18080 起服：Flyway v2.2.11 应用成功（`Successfully applied 1 migration`）；
  冒烟 `/tmp/m37-smoke.py` **13/13 全绿**：注册 → 记游标 → push 已完成行 → **记游标→增量拉**
  断言新列逐字下发（inProgress=0/totalSeconds=600/activeSeconds=480/durationS=480/endedAt 字符串/
  pauseEvents 逐字）→ push in_progress=1 半成品行 → 增量拉 + 全量拉均断言其**不出现**（pull 谓词）
- `updateById` 前置空 serverUpdatedAt 红线：本批零 updateById 改动，M18 回归锁保持
- 待办（真机，龙哥）：v8→v9 真机升级走一遍（先导出 JSON 留底）、计时全链路真机回归

## 后续（M37+）

- ~~喂养计时器服务端五列 + 拉取谓词~~ ✅ M37 已完成（本节）
- M38 起为 ui 仓库批次（后台产品库图标上传），biz 侧零改动预期；M39 Android 本地规则引擎
  （服务端零改动）；M40 基础数据同步三要素（Android + 服务端，接 base 通道）

## 后台产品库图标上传（M38，会话 2026-11-14，**纯 ui 仓库批次，服务端零改动**）

> 需求 2 落地：`ui` 仓库（gitee `baby-log/manager`）`develop` 2f52f53 已双推。
> 本节是 server 侧的进度锚点（本批 biz/file 插件均零改动）。

### 交付（ui 仓库）

| 东西 | 说明 |
|---|---|
| `apps/admin/src/api/file.ts` 新增 `uploadFileWithMeta` | presign → PUT 直传 → register 三步，返回 `{ id: sys_file 主键（Long→String）, fileUrl }`；既有 `uploadFile` 复用之，返回签名不变 |
| `views/babylog/product/index.vue` 图标字段 | 手填 file_id 输入框改为内嵌 el-upload（custom http-request 直调 plugin-file，`/file/presign|register` 走 request 封装自动带 sys-user 登录态）；上传成功自动回填 `formModel.iconPath = file_id` + 即时预览 |
| 预览链路 | `presign-download(fileUrl)` 换限时可访问 URL（私有桶口径，同 sys_user.avatar 回显策略）；`file_id→fileUrl` 映射存 sessionStorage（`babylog:product-icon-urls`）供编辑回显；**M38 前手填的旧 file_id 无映射不可预览，仅显示 file_id 文本**（plugin-file 无 id→objectKey 端点，ui-only 批不动服务端） |
| 客户端限制 | 白名单 png/jpg/jpeg/webp/svg（plugin-file `allowed-extensions` 的图片子集）+ ≤500KB，`before` 校验拒绝并 ElMessage 提示；服务端扩展名白名单兜底仍生效 |
| 替换/移除口径 | **旧文件不即时删除**：`iconPath` 引用解除后旧 sys_file 行即成孤儿，按 M13 孤儿差集清理同口径回收（不做 eager deleteFile） |

### 契约注意（零变化）

- `bl_product.icon_path` 语义不变：内置行 = plugin-file 的 file_id（V2.2.4 注释原文）；
  App 端 base/products 拉取逻辑零改动（M38 前 App 端对内置行图标本就只认内置 drawable）。
- 服务端校验面：register 只查扩展名白名单不查大小——500KB 是图标场景的客户端约定。

### 验证记录（M38）

- `pnpm typecheck`（vue-tsc）零错误；eslint 两个改动文件零告警；
  临时 vite dev 起服：`/src/views/babylog/product/index.vue` 与 `/src/api/file.ts` 按需编译 200（含新逻辑符号）
- dev 18080 后端联调与真机回归归龙哥（`VITE_API_BASE_URL=http://127.0.0.1:18080 pnpm dev`，admin/admin123）

## 后续（M38+）

- ~~后台产品库图标上传（M38）~~ ✅ 已完成（ui 仓库 2f52f53，本节）
- M39 Android 本地规则引擎（数据分析与异常提醒，服务端零改动）；
  M40 基础数据同步三要素（Android + 服务端，接 base 通道）

## 基础数据通道范围扩展（M40，会话 2026-11-14，V2.3 第二批：base 通道扩四类，为 M41 云端规则与 M39 阈值下发打底）

### 交付

| 东西 | 说明 |
|---|---|
| Flyway **V2.2.12** | ① bl_vaccine_schedule / bl_app_version 补 `server_updated_at DATETIME(3) ... ON UPDATE CURRENT_TIMESTAMP(3)` + `idx_sync_cursor`（列级自动前进，与 18 张同步表同口径）；② 新建 `bl_alert_threshold`（threshold_key UK / threshold_value 文本 / 审计 / deleted）；③ seed 疫苗时间表 28 行（id 1~28，跨表对齐 App 端 NationalImmunizationSchedule，双方案 note 标注）+ 阈值 18 行（键值逐字对齐 App 端 `AlertThresholds` 默认值，黄疸 μmol/L） |
| `BaseDataTable` 注册表（sync/support，**不进 SyncTable**） | VACCINE_SCHEDULE / ALERT_THRESHOLDS / APP_VERSIONS 三张运营表走并列注册（apiName/表名/实体/pullPredicate）；`APP_VERSIONS` 谓词 `status = 'PUBLISHED'`——**不按 platform 过滤**，避免「过滤 × 游标分页」组合漏行（客户端自己按平台挑行比对 versionCode） |
| 为什么不进 SyncTable | SyncTable 是 18 张业务同步表的唯一清单，进表即获得 sync/changes 通道资格；这三张是**服务端运营数据的 base 通道成员**，category 置 null 会混进业务游标口径。并列注册 + BaseDataTableSql（复用 SyncTableSql 的 camelToSnake，反射生成拉取 SQL）是刻意的通道隔离 |
| 端点（BabyLogBaseDataController 追加 3 个，类级 @SaCheckLogin 已有，A11 无欠条） | `GET /app/babylog/base/vaccine-schedule` / `alert-thresholds` / `versions`，参数与 products 一致（since/sinceId/limit），changes 键 = apiName；products 路径零改动（M9 回归） |
| 实体红线 | BlVaccineSchedule / BlAppVersion / BlAlertThreshold **刻意不声明 serverUpdatedAt**（M10/M18 教训：实体带非空游标列会被 MP NOT_NULL 策略显式写回 → ON UPDATE 不触发 → 游标不前进；已写进 V2.2.12 头注释） |
| bl_ 表惯例 | 不加 tenant_id（同 M10 既有口径） |

### 验证记录（M40）

- `mvn test -pl babylog-plugins/babylog-plugin-biz`：**176/176 绿**（167 基线 + 9 新增：BaseDataOpsPullTest 4 + BaseDataTableSqlTest 5）；ArchUnit P0 **18/18 绿**
- dev 18080 冒烟 **15/15 绿**（注册 → vaccine-schedule since=0 拉 28 行 → alert-thresholds 18 行且 milk_drop_ratio=0.7/黄疸六档/supplement_check_hour=21 → versions 空表退回游标 hasMore=false → 游标推进后增量 0 行且不回退 → products 回归 32 行 id 1~99 全在）
- Flyway 在 babylog_dev 上 v2.2.11 → **v2.2.12**（冒烟起服时自动应用）
- Android 端配套（开关/每日任务/立即更新/独立游标/阈值下发缓存）见 BabyLog-Android PROGRESS M40 节；全量单测 316 绿 = 306 基线 + 10 新增
- **既有测试债（非 M40 引入，待龙哥拍板）**：admin-server 模块 4 个红——ProdOpsLoggingGuardTest×2 / NacosConfigImportGuardTest×2，根因是 5b3e8e6「废除环境变量注入，配置全部内联 yml」等后续提交推翻了 guard 测试守护的旧配置决策（修 = 按新配置口径改/删 guard 测试）；另 DruidPoolProductionConfigTest 期望已随 a0494de 的 prod 20/5 修正（M40 顺手对齐并加注释）

## 后续（M40+）

- ~~基础数据同步三要素 + 范围扩展（M40）~~ ✅ 已完成（本节）
- ~~M41 记录类型云端规则~~ ✅ 已完成（下节）
- M42~M44 小程序全功能三批；M45 电视端

## 记录类型云端规则（M41，会话 2026-11-14，base 通道第五类：运营可调已有类型的默认开关/起止月龄/排序/分组/文案，**不能新增类型**）

### 交付

| 东西 | 说明 |
|---|---|
| Flyway **V2.2.13** | 新建 `bl_record_type_rule`：`record_type` UK；`enabled/start_age_days/end_age_days` **可空 = 该项不覆盖**（运营只想改文案不用被迫填齐）；`sort_order NOT NULL DEFAULT 0`；`server_updated_at` 列级 `ON UPDATE`（双段游标同 18 表口径）；审计四件套 + deleted。seed 21 行（id 1~21）**逐字对齐 Android RecordType 注册表**：PUMP/MEDICINE enabled=0、SOLID/WATER start=180、PUMP end=730、CORD 60、SPIT_UP 365、VACCINE 2190、FOLLOW_UP 90、DIAPER 1095，sort 10 起步进（= 注册表顺序，拉过规则包但未改排序时客户端行为不变），display_label = 现行文案。菜单 5050（目录）/5051（列表）/5052（编辑），5050 visible=0 直接可见 |
| `BaseDataTable` 注册（**不进 SyncTable**，同 M40 三表口径） | `RECORD_TYPE_RULES("record_type_rules", "bl_record_type_rule", BlRecordTypeRule.class, "")`——空谓词全行下发 |
| 端点 | App 侧 `GET /app/babylog/base/record-type-rules`（类级 @SaCheckLogin 已有）；管理侧 `/babylog/record-type-rule`：`GET /page`、`GET /{id}`、`PUT /{id}`——**只有 page/get/edit 三个，无 add/remove**，新类型只能随客户端发版进来（边界红线） |
| 实体红线 | BlRecordTypeRule **刻意不声明 serverUpdatedAt**（M10/M18 教训：updateById 不 SET 游标列 → ON UPDATE 触发 → 游标前进）；空谓词 = 拉取 SQL 无 where，BaseDataTableSql 反射列跳过游标列/审计列（同 18 表口径） |
| 管理端写路径 | edit 全字段可空：blank 归一为 null（**null = 删除该项覆盖**，回落客户端代码默认）；错误码 `RECORD_TYPE_RULE_NOT_FOUND(8392)` |
| ui 仓库配套 | `apps/admin/src/views/babylog/recordTypeRule/index.vue` + `api/babylog/recordTypeRule.ts` + `@pivotos/types` 三个类型：21 行一页（pageSize 100）、enabled 三态（''回落/1/0）、**无新增按钮**、v-hasPermi 三权限点 |

### 验证记录（M41）

- `mvn test -pl babylog-plugins/babylog-plugin-biz`：**178/178 绿**（M40 基线 176 + 新增 2 用例文件增量：BaseDataTableSql 1 + BaseDataOpsPull 1 + AdminControllerPermissionContract +1 控制器/3 权限点）
- dev 18080 冒烟 `/tmp/m41-smoke.py` **22/22 绿**：注册 → record-type-rules since=0 拉 21 行（PUMP/MEDICINE enabled=0、SOLID start=180/文案「辅食」、changes 键 `record_type_rules`）→ 增量 0 行游标不回退 → admin/admin123 登录 → page 21 行 → edit SOLID 文案+排序 → App 端增量拉即时反映 → POST/DELETE 无映射被拒（框架包成 HTTP 200+业务 code≠0，已写进脚本断言）→ 无 token code=1002 → 复原数据
- Flyway 在 babylog_dev 上随起服自动应用 v2.2.13（冒烟 21 行 seed 即证据）
- Android 端配套（规则缓存表 v10 + 合并层 + 渲染改读）见 BabyLog-Android PROGRESS M41 节；全量单测 **332 绿 = 306 基线 + M40 13 + M41 13**
- **既有测试债（非 M41 引入）**：`babylog-starter-auth` 的 DemoAcceptanceTest 6 红——测试配置 Redis **空密码** vs 云端实例已设密码（dev yml 有密码 `ZpHSQ9qe22Y1UoHV`，WRONGPASS 实锤），与 M40 记的 admin-server 4 红同属「待龙哥拍板」类（修 = 测试 yml 补密码）
- 遗留：dev 库留一个 m41 冒烟注册账号，可清可留


## 服务端数据导出（M44，会话 2026-11-15，小程序档案与设置批）

### 端点与鉴权

`GET /app/babylog/export?format=json|csv&includeReminders=false`

- 类级 `@SaCheckLogin(type = app-user)`，`Authorization` 请求头使用既有裸 token；**query 中的 token 不参与鉴权**。
- 每次请求重新执行 `SyncAccessScopeResolver.resolveAccess`：登录态、当前家庭可见账号集合与分类可读减窄即时生效。退出家庭后不会凭旧文件地址继续读新请求。
- **没有永久公开 URL、下载专用 token、公开对象存储或 web-view 免登页**。小程序直接 `wx.downloadFile`，401 或 HTTP 200 文件体内的 `R{code:1002}` 都走 refresh，成功后只重试一次。
- `format` 非法使用既有 `1001`；未登录使用既有 `1002`。无新增 8xxx、无新表、无 Flyway。
- 附件头为 UTF-8 `Content-Disposition`，并带 `Cache-Control: no-store` / `X-Content-Type-Options: nosniff`。
- 控制器同步返回 `byte[]`，不使用 `StreamingResponseBody`。dev 实测发现后者异步完成后的二次 dispatch 会重走 app-user 注解鉴权，但 Sa-Token 请求上下文已清，导致完整 JSON 后追加错误 R 体；同步二进制已修复并通过真实下载。服务实现原本就全量装配行，当前不额外引入流式查询的复杂度。

### 文件规格（逐表对照 Android 会话 G）

- `ExportTableSpecs` 是 Android Room **schema v10 的 18 表**服务端镜像，列清单同时约束 JSON 与 CSV，防止 `user_id/family_id/remote_key/user_type` 等服务端专属列混入。
- JSON：`meta.schema_version=10`、`app_version=server-3.2.0`、数字 `exported_at`、`include_reminders`、`total_rows/table_counts`；`tables` 使用 snake_case 列名，保留软删行，ID 与 epoch 全是 JSON number。独立 `ObjectMapper`，刻意不复用 Web 全局 Long→String。
- CSV ZIP：排除软删行，UTF-8 BOM、CRLF、中文表头、中文枚举、是/否布尔、可读时间与原列 `_epoch` 双列、RFC 4180 转义；按宝宝拆文件，含已归档宝宝并标注，宝宝名路径字符消毒。全局表保持 `全局-*.csv`。
- 默认排除派生表 `reminders`，所以文件为 17 表；`includeReminders=true` 时才纳入。
- `family_members.family_key` 在导出边界由 `familyId/familyKey` 显式转字符串；内置产品通过 `selectBuiltinChanges` 合并，不能只查家庭 userIds。
- 查询复用 `SyncRecordStore.selectChanges` 的双段游标，每页 500，持续至末页；服务端不静默截断。CSV 宝宝清单按 `created_at/id` 排序。
- 只含服务端已同步数据，小程序未补传草稿不在文件内；附件口径仍同 Android 的索引行，不新增二进制附件上传/导出能力。

### 验证

- `mvn test -pl babylog-plugins/babylog-plugin-biz`：**183 tests，0 failures，0 errors**。新增导出 5 项覆盖 Android 列/数字 epoch/墓碑/内置产品/501 行翻页/分类减窄/reminders 可选/CSV 格式。
- `P0ArchitectureTest`：通过（完整 reactor 命令，无指定测试模块跳过）。
- `BabyLog-Mini/tests/m44-dev-smoke.py`：dev 18080 **65/0**。随机 OWNER/家人/陌生账号，覆盖建家加入、VIEWER 8343、三类医疗全字段与 version=2 墓碑、summary/HEALTH/MEDICAL 取数、OWNER/家人/陌生账号 JSON+CSV 实际下载、字段/软删/BOM/CRLF/中文枚举/时间双列/费用备注/归档文件、1001/1002/query token 不绕鉴权、refresh 后新 access 下载。
- 小程序 `wx.downloadFile` 与真机分享边界见 `BabyLog-Mini/PROGRESS.md` M44；生产环境仍需配置 HTTPS `downloadFile` 合法域名。

## 电视设备码绑定（M45，会话 2026-11-15）

### 四个端点与身份边界

统一前缀 `POST /app/babylog/device-bind`：

| 子路径 | 鉴权 | 请求与结果 |
|---|---|---|
| `/apply` | TV 匿名 | `{device:{deviceUuid,deviceName,platform,appVersion}}`；返回 6 位 `userCode`、高熵 `deviceCode`、数字 `expiresIn=600` 与 `pollIntervalSeconds=5` |
| `/poll` | TV 匿名 | 只接受 `{deviceCode}`；返回 `pending/approved/denied/expired` 与 `retryAfterSeconds`；只有一次性 `approved` 带 `login=LoginTokenVO` |
| `/confirm` | 手机 `@SaCheckLogin(app-user)` | 只接受 `{userCode}`；绑定身份只从 `LoginContext.getUserId()` 取，DTO 没有 `userId` |
| `/deny` | 手机同上 | 拒绝仍为 `PENDING` 的申请，返回 `status=denied` |

`AppAuthService.loginByBoundDevice(userId, device)` 回读账号并校验启用状态，然后复用统一 `completeLogin`：app-user 双 token、`bl_device` 落行／复用、`workerId` 分配与其它登录方式完全一致。TV 建议 `platform=ANDROID_TV`；服务端归一为大写。没有新增写记录端点，TV 后续仍走 `sync/push`，VIEWER 的 8343 终判不变。

### Redis 状态机、一次性领奖与限流

- 不新增表或 Flyway。`bl:device-bind:user:<sha256(userCode)>` 是短码索引，`device:<sha256(deviceCode)>` 保存状态与设备 JSON，`claim:<sha256(deviceCode)>` 是 30 秒独占领奖锁；另有 create/poll/confirm-fail 限流键。
- 原始 userCode/deviceCode 不进入 Redis 值或业务日志，只在申请响应中返回。`approvedBy` 始终以**字符串**穿过 Lua cjson，避免 63 位 userId 被 double 截断；散列不等于给 6 位码增加抗离线枚举能力。
- 内部 `PENDING → APPROVED/DENIED → CONSUMED`（仅 APPROVED 可领奖）。确认／拒绝脚本原子检查 PENDING、改状态并删除短码索引；重复确认、过期、已处理统一 8400。
- 领奖脚本先检查 device 状态并 `SET claim NX PX`；并发另一侧只见 pending。签发失败释放锁、保留 APPROVED；最终原子消费失败时撤销刚签发的 access/refresh 并返回 8403。CONSUMED 重放也返回 8403，绝不二次发 token。
- 申请每 IP 5 次/分钟；同 deviceCode 轮询间隔 5 秒；确认失败同账号 10 分钟 5 次或同 IP 20 次后锁 8402，成功确认／拒绝清计数。计数与首次 TTL 也在 Lua 内完成。
- 默认配置在 `babylog.device-bind.*`：`code-ttl-seconds=600`、`poll-interval-seconds=5`、`claim-ttl-seconds=30`、`apply-per-ip-per-minute=5`、`confirm-fail-user-limit=5`、`confirm-fail-ip-limit=20`、`confirm-fail-window-seconds=600`，无需向环境 yml 增加密钥。
- 来源 IP 解析沿用 App 登录审计的 XFF 首段口径。**生产 Nginx／网关必须覆盖客户端传入的 XFF**；若允许客户端直连并伪造该头，IP 限流可被换头绕过，账号失败计数仍是独立兜底。

### 错误码与匿名登记

- 8400 `DEVICE_BIND_CODE_INVALID`：短码无效、过期或已处理，不细分原因。
- 8401 `DEVICE_BIND_RATE_LIMITED`：申请或轮询过频。
- 8402 `DEVICE_BIND_CONFIRM_LOCKED`：账号／IP 确认失败窗口已锁。
- 8403 `DEVICE_BIND_ALREADY_CLAIMED`：已领奖或失去最终消费权。
- `P0ArchitectureTest.ANONYMOUS_ENDPOINTS` 只登记 `apply` 与 `poll`；`confirm` / `deny` 有 app-user 注解，不属于匿名面。

### 验证与未验边界

- `mvn test -pl babylog-plugins/babylog-plugin-biz`：**209 tests，0 failures，0 errors**（M44 183 + 设备码 23 + 绑定登录 3）。覆盖短码碰撞、原始码不进 store、双维失败计数、四状态、claim busy、签发失败释放、消费失败撤销、高位 userId、并发单侧领奖与停用账号。
- `P0ArchitectureTest`：**22/0**；完整 reactor `package` 成功，并直接核对 fat jar 内嵌 biz jar 含 `BabyLogDeviceBindController.class`。
- `scripts/e2e/m45-device-bind-smoke.py`：dev 18080 **36/0**。覆盖申请→pending→手机确认→领奖→refresh→summary/today→TV token 写完整 feeding（`inProgress=0`、真实 memberId）→OWNER 增量可见；另测 VIEWER 设备 token 8343、陌生账号请求体伪造 userId 无效、家庭隔离、deny、8400/8401/8402/8403、同设备重绑复用 deviceId/workerId，以及真实 Redis 两路并发只有一侧获得 token。
- `scripts/e2e/m45-device-bind-expiry-smoke.py`：以 `--babylog.device-bind.code-ttl-seconds=2` 临时启动后 **2/0**，通过公开 API 实测 pending→真实 TTL 到期 expired。两个 dev 进程验证后均已停止。
- **未验边界**：工作区仍无 BabyLog-TV 工程，无 TV APK、Leanback 启动、DPAD、模拟器或真机结果；手机端短码输入／确认 UI 也未在本批新增，手机确认由已登录 API 代替。详细建工程指导与后续提示词见 `独立产品规划/14-V2.4电视端启动评估.md`。


## 服务端 PivotOS 严格整改（S2，会话 2026-11-16，BabyLog V2 修复链）

> S1 四端统一契约（独立产品规划/15 号文档）已落地 Android 侧；本会话只做 **server 仓库**
> 的严格整改，不动 Android/iOS/小程序/TV/管理 Web 功能代码。
> **勘误（S3 补记）**：本节原写「四个任务全部收口」「遗留：无」**言过其实**——T2 当时
> 只做了白名单/形态锁/LIMIT 钳位的**安全加固**，`SyncTableSql`/`BaseDataTableSql`/
> `JdbcSyncRecordStore` 的动态 SQL 本体仍在，不等于 PivotOS「一律类型化」合规；
> 真正的类型化改造由 **S3（见下节）** 完成，本节 T1/T2 的描述保留作历史记录，
> 其中 T2 的全部内容与「T1 的 deleted IN (0,1) 恒真条件」均已被 S3 取代/清除。

### T1 BlDeviceMapper：唯一 @Select 手写 SQL 消除

- 原 `SELECT COALESCE(MAX(worker_id), -1) … WHERE user_id = #{userId}`（参数化无注入面，
  但属手写 SQL，违 PivotOS「一律 LambdaQueryWrapper」契约）改为 **default 方法 +
  LambdaQueryWrapper**：`.select(workerId).eq(userId).and(deleted=0 OR deleted=1)`，
  MAX 聚合移到 Java 侧（`maxWorkerIdOf` 纯函数）。
- **deleted IN (0,1) 的来由（踩坑记录）**：MyBatis-Plus 3.5.17 的逻辑删除是给
  wrapper 查询**建 MappedStatement 时静态烘焙**进 SQL 的（`sqlWhereEntityWrapper` →
  `getLogicDeleteSql`），`IgnoreStrategy` 没有 logicDelete 字段、
  `InterceptorIgnoreHelper` 对 selectList 运行时**不可绕过**。而 bl_device 全插件无任何
  软删代码路径（踢出 = status KICKED），deleted 列 NOT NULL——显式声明
  `deleted=0 OR deleted=1` 与自动追加的 `deleted=0` 合取后 ≡ 全行，**零 SQL 改写保住
  「软删行仍占 workerId」旧语义**，且全部类型引用、参数全占位符。
- 空条件语义逐条核对：无设备/全 null workerId → -1（同 COALESCE(MAX, -1)）；
  userId 必非空（token 派生）。新增 `BlDeviceMapperTest` 4 例钉死。

### T2 动态 SQL 收口（SyncTableSql / BaseDataTableSql / JdbcSyncRecordStore）

- **表名白名单**：两构造器统一 fail-fast 校验 `bl_[a-z0-9_]+`（表名只来自
  SyncTable/BaseDataTable 枚举 = 编译期清单，这是第二道复核）。
- **列名/谓词形态锁死**：标识符 `^[a-z][a-z0-9_]*$`、谓词 `^[a-zA-Z0-9_ =()']*$`
  （谓词是唯一被直接拼进 SQL 文本的片段，必须是代码内常量形态）。
- **LIMIT 内联收口**：`JdbcSyncRecordStore.withLimit`（包级，便于单测）——数值型 int、
  服务层已钳位，拼前最后防线 [1,1000] 越界即拒。
- 客户端载荷自始至终只走 `:占位符` 绑定（upsert 循环键是 `payloadProperties()` 内部清单，
  值才是载荷），墓碑/乐观锁/归属盖章语义零变化。
- 新增单测：`SyncTableSqlTest` +3（无拼接值/谓词形态/LIMIT 越界）、
  `BaseDataTableSqlTest` +1（base 通道同款白名单）。

### T3 A11 登记核对

- 本次**零新端点**；同步/设备/设备码/基础数据控制器逐个人工核对 + A11 全绿：
  sync/base/device 类级 `@SaCheckLogin(app-user)`，device-bind confirm/deny 方法级，
  apply/poll 在 `ANONYMOUS_ENDPOINTS` 在册（M45），AdminDeviceController 方法级
  `@SaCheckPermission`——登记无缺失、无新增欠条。
- **踩坑（环境债）**：本地 .m2 的 biz jar 停在 11-14（M45 之前），admin-server 单独
  `-pl` 测试解析到旧 jar → A11 报 apply/poll「登记失效」假红。修法 =
  `mvn install -DskipTests -pl babylog-plugins/babylog-plugin-biz -am` 后重跑。
  教训：**admin-server 的 ArchUnit 吃的是本地仓 jar，biz 改动后必须先 install**。

### T4 Nacos 配置门禁 → 敏感配置启动门禁

- **NacosConfigImportGuardTest 整改**（原 2 例恒红，根因 = M4 拍板「单体形态不接
  Nacos、配置内联 yml」推翻了旧假设）：改为条件式——import 若存在必须 `optional:` 前缀、
  nacos 连接键若存在必须 `${...}` 占位符；单体没接线是合法终态。
- **ProdOpsLoggingGuardTest 2 例整改**：不再要求 yml 字面出现 `BABYLOG_LOG_DIR` 等
  环境变量占位符（违背内联拍板）；改为断言 tunable 接在 `babylog.log.*` 且 logback
  `springProperty source` 真实接线（防 yml 改了不生效的静默失效）。
- **新增 `SensitiveConfigGuard` + `SensitiveConfigEnvironmentPostProcessor`**
  （admin-server）：密钥/口令/连接串缺失即 fail-fast——prod 硬拒（EnvironmentPostProcessor
  在 ConfigData 之后、**任何数据源/Redis Bean 创建之前**抛 IllegalStateException，
  进程非零退出），非 prod 逐条 WARN 不拦截（dev 零配置起服不受影响，与
  FieldEncryptKeyGuard 同口径）。规则：主库口令 + Redis 口令无条件必配（prod）；
  SMTP 配 host→要口令、file 通道 s3 形态→要 access/secret key、小程序配 appid→要
  secret（功能条件触发）；值残留 `${...}` 视为缺失；报告只含键名不回显密钥值；
  紧急绕过开关 `babylog.startup.sensitive-config-guard-enabled=false` 留 WARN 痕。
- **踩坑（S2 首版即中）**：Spring Framework 7 的 SpringFactoriesLoader 只读
  `META-INF/spring.factories`，Boot 2.4~3.x 的
  `META-INF/spring/...EnvironmentPostProcessor.imports` 在 Boot 4 下**静默失效**
  （类不加载、零报错）。已改 spring.factories 并用新增 `SensitiveConfigWiringTest`
  2 例锁死「正确文件存在 + 废弃文件不存在」。

### 验证记录（S2）

- biz 单测 **217/217 绿**（209 基线 + T1 4 + T2 4）；admin-server **52/52 绿**
  （含 P0ArchitectureTest 18 条、整改后 Nacos×2 / ProdOps×7 / Druid×5 /
  SensitiveConfigGuard×10 / SensitiveConfigWiring×2）
- 回归红线全部实测未回退：version 乐观锁（重推 skipped）、软删墓碑（deleted=1 推送
  可被拉到）、8343/8344/8350 拦截语义（单测锁死 + M16/M18 用例保持绿）、
  并发 push 语义（ODKU 版本门禁 SQL 逐字未动）、S1 契约（feedings 计时五列下发、
  pauseEvents 逐字透传、奶量 null → 90 补录全链路）
- dev 18080 冒烟 **27/27 绿**（脚本 `/tmp/s2-smoke.py`，运行时账号）：注册首设备
  workerId=0 → 第二设备 workerId=1（T1 真库聚合）、push/pull/分页/base 四通道、
  未登录 1002；门禁日志 `[敏感配置门禁] 通过（形态=非prod）` 在场
- fail-fast 反向实证：prod 形态 + 命令行置空主库口令 → 进程 exit=1、报告仅键名、
  拦截在 Druid 数据源初始化之前
- 遗留：**T2 动态 SQL 未真正消除（伪收口）**——白名单/形态锁只是加固，SQL 文本拼接
  本体仍在，PivotOS 合规留待 S3 类型化改造。两个 yml 的未提交敏感配置改动按龙哥要求
  原样保留（未读未动未提交）。


## 服务端 PivotOS 严格整改（S3，会话 2026-11-17，续 S2 伪收口）

> S2 的 T2 只是安全加固（白名单/形态锁/LIMIT 钳位），动态 SQL 本体仍在——本节把
> 「SyncTableSql/BaseDataTableSql/JdbcSyncRecordStore 动态 SQL 彻底消除」真正做完，
> 同步语义零回退。

### T2 真正收口：视图实体 + MyBatis-Plus 类型化存储

- **删除** `JdbcSyncRecordStore` / `SyncTableSql` / `BaseDataTableSql`（及两个旧 SQL
  形状测试）。`SyncTable`/`BaseDataTable` 枚举移除 `pullPredicate` 字符串字段——
  谓词改由注册规格（`SyncViewRegistry`）以 Lambda 列表达，枚举只剩编译期表名清单。
- **23 个视图实体**（`com.babylog.biz.sync.entity`）：18 同步表 `BlXxxSyncView` +
  4 base 表 + `BlDeviceView`，每个 `extends` 业务实体 + `@TableName` + **遮蔽
  `deleted`（不带 @TableLogic）**，同步表还遮蔽 `version`/`serverUpdatedAt`。
  这是本方案的核心：S2 已实证 MP 3.5.17 的逻辑删除是 MappedStatement **静态烘焙**
  进 SQL 的（`IgnoreStrategy` 没有 logicDelete 字段、运行时就绪不可绕过），所以不靠
  绕过——靠视图实体让 MP 字段收集**子类优先**，逻辑删除从同步表 SQL 中彻底消失；
  墓碑可读、可下发、可复活（H2 实测）。
- **`SyncLambdas`**：LambdaMetafactory 从实体 getter 纺 `SFunction`。**踩坑**：实例化
  类型必须强制为视图类（`MethodType.methodType(getter.getReturnType(), type)`），
  否则 MP 按 instantiatedType 查 TableInfo 缓存会落到父类报
  `can not find lambda cache`。
- **upsert 三态**（替代 ODKU）：版本门禁条件 UPDATE（`WHERE id=? AND version<?`）
  → 0 行则 INSERT（归属/审计列只在 INSERT 盖章）→ `DuplicateKeyException` 回退再
  条件 UPDATE，再 0 行 = 幂等 skip。NOT NULL 带默认列（children: isPremature/archived；
  feedings: inProgress）收到显式 null 时抛 `DataIntegrityViolationException`，
  对齐旧库拒绝语义（上层据此走 8342 单条拒收）。
- 22 个显式 `@Bean`（`SyncStoreHandlerConfiguration`）；存储构造时 fail-fast 校验
  18+4 全覆盖；limit [1,1000] 钳位。全链路零 `.last()`/`.apply()`/字符串列名。

### T1 收尾

- `BlDeviceMapper` 还原为纯 `BaseMapper<BlDevice>`；改由 `BlDeviceViewMapper`
  （视图实体，`deleted` 遮蔽后无需恒真条件）承载 `selectMaxWorkerId`，
  `deleted IN (0,1)` 恒真条件**全仓清除**；`DeviceWorkerIdAllocator` 改注入视图
  Mapper。软删行仍占 workerId 的旧语义由「不查 deleted」天然保住。

### 网关 Nacos 配置修复

- `babylog-gateway-server` `application.yml` 的 nacos config 5 键改为
  `${BABYLOG_NACOS_SERVER_ADDR:127.0.0.1:8848}` / USERNAME / PASSWORD / NAMESPACE /
  GROUP 占位符（原硬编码值在环境外不可用），`GatewayNacosConfigWiringTest` 4/4 修复。

### dev 冒烟抓到的真 bug（已修）

- 存储层门禁 `asLong` 只认 `Number`，而真实 JSON 链路中 id/version 以**字符串**形式
  到达泛型 Map（服务层 `SyncServiceImpl.asLong` 是字符串容忍的，两层口径不一）→
  feedings push 全部 1500。已把存储层 `asLong` 改为与服务层同口径，并加 H2 回归
  用例 `stringIdAndVersionFromJsonPayloadAreAccepted`（字符串 id/version 落库成功）。
- **教训**：H2 测试载荷全部用 Java Long 构造，掩盖了 JSON 字符串形态；dev 冒烟
  （真实 Jackson 反序列化）才是这类分层差异的试金石。

### 验证记录（S3）

- biz 单测 **226/226 绿**（S2 基线 217 + 26 新增 −16 旧 SQL 形状删除 −1 注释误报修正
  期计数调整）：`SyncViewRegistryTest` 11 / `SyncStoreWiringTest` 4 /
  `SyncStoreH2Test` 12（H2 1.4.197 test 依赖已入 pom，真实 SqlSessionTemplate 链路：
  8 线程 48 版本并发收敛、跨用户归属保持、墓碑三态、同毫秒双游标翻页不重不漏、
  M37 谓词、amountMl=null/pauseEvents 透传、NOT NULL 拒收、字符串 id/version）
- admin-server **52/52 绿**（P0ArchitectureTest 22 条含 A10 last() 红线、A11 登记）；
  gateway **7/7 绿**；starter/commons 19 模块与其余 12 个插件模块 reactor 全绿
- dev 18080 冒烟 **27/27 绿**（`/tmp/s2-smoke.py`，新 fat jar）：T1 workerId=0→1 回归、
  push/pull/双段游标分页/墓碑/版本门禁/base 四通道/未登录 1002；门禁 WARN 在场；
  验证后进程已停
- **环境债（与本阶段无关，未跑/失败）**：starter-auth（6 错）、starter-tenant（13 错）、
  plugin-message、plugin-system 的测试 yml 硬编码远程中间件（175.24.176.176 的
  Redis/MySQL），该服务器凭据已轮换（WRONGPASS/1045），MySQL 用例会无限重试挂死。
  修复方向 = 测试改嵌件/本地容器，属独立整改项。
- 遗留：上述 4 个模块的环境债；两个 yml 的未提交敏感配置改动按龙哥要求原样保留
  （未读未动未提交）。

## 记录增强链契约定稿 + 新列（M46，会话 2026-11-17，18 号契约第一棒）

> 记录增强链（M46~M51）的契约与服务端数据模型批次。**单一事实源 =
> `独立产品规划/18-记录增强统一契约.md`**（结构照 15 号；四端 M47~M50 逐端对齐，
> 本批 Android/iOS/Mini/TV 四仓零改动）。

### 交付

| 东西 | 说明 |
|---|---|
| Flyway **V2.2.14**（`V2.2.14__bl_bath_ended_at_sleep_transitions.sql`） | `bl_baths` 加 `ended_at BIGINT NULL`（D1：无进行中状态机，null=单点）；`bl_sleeps` 加 `transitions TEXT NULL`（D2：方式段事件流 JSON `[{"place":"CRIB\|BIG_BED\|HELD\|STROLLER","at":epoch}]`，首条=入睡方式，为空回落 `place` 旧列）。版本号落笔前已 grep：2.2.4~2.2.13 已占用。bl_ 表不加 tenant_id；MySQL DDL 不进事务 |
| `BlBath.endedAt`（Long）/ `BlSleep.transitions`（String） | 业务实体加字段。**S3 架构下加列只需改业务实体**：`SyncEntityMeta` 从视图实体反射收集字段（子类继承），新列自动进 payload/pull 清单——`BlBathSyncView`/`BlSleepSyncView`/`SyncViewRegistry` 零代码改动，由 `SyncViewRegistryTest` 新增锁断言实证（baths 载荷含 endedAt、sleeps 载荷含 transitions、两表无新谓词/无 NOT NULL 拒收列）。**注意：17 号 D2 把枚举简写成 BED/HOLD，18 号定稿纠偏为四端既有枚举 CRIB/BIG_BED/HELD/STROLLER** |
| `SyncStoreH2Test` +1 用例 | H2 真链路：新形态 push（endedAt 字符串形态 Long / transitions 逐字透传）→ 旧形态 push（无新列键 → 落 NULL 不 8342）→ 同 id 版本+1 旧形态覆盖（更新路径 SET NULL 不粘旧值）→ 拉取下发 camelCase 键 |

### 契约注意（写给 M47+ 各端会话，详见 18 号 §七）

1. **全局 NON_NULL：新列值为 null 时 JSON 键直接缺省**（M46 冒烟实测，M21 inviteCode 同款坑）——
   四端解析必须按键取值容忍缺键，禁止假设字段必在。存储层（Map）null 键在场，序列化后才缺键。
2. `endedAt` 下发是字符串形态 Long（全局 Long→String），push 收字符串/数字两种形态（`SyncPropertyAccess.coerce` 收窄）；
   `transitions` 逐字透传，服务端零解析（与 `pause_events` 同口径）。
3. 无新端点、无新错误码、无新拉取谓词、biz-api 零改动；summary/today 聚合**不消费新列**。
4. `ExportTableSpecs` 未加新列（镜像对象还是 Room v10）——**M47 随 Room v11 同步补**并升 `meta.schema_version`。

### 验证记录（M46）

- biz 单测 **228/228 绿**（S3 基线 226 + 2：`SyncViewRegistryTest` +1 继承锁、`SyncStoreH2Test` +1 新列全链路）
- fat jar 复核：删旧 jar 重打包 + javap 解内嵌 biz jar 核对 `BlBath.getEndedAt` /
  `BlSleep.getTransitions` / V2.2.14 在场（biz 模块 0.15s 秒过更必须复核）
- dev 18080 冒烟 **19/19 全绿**（脚本 `/tmp/m46-smoke.py`，运行时账号，**全程记游标→增量拉**）：
  空账号基线 0 行 → 旧形态三行（children+baths+sleeps）首推 upserted → 全量拉旧行
  新列键缺省（NON_NULL）→ 新形态两行 push（endedAt 字符串 Long / transitions 逐字）→
  增量拉只见新两行（游标判据）且新列逐字下发、hairWashed 整数 0/1 → 旧形态 version+2
  覆盖（更新路径 SET NULL）→ 数字形态 endedAt 落库 + 同版本重推 skipped（幂等）→
  下发仍字符串（Long→String）→ CARE 分类过滤回归 → 未登录 1002；Flyway 起服时
  v2.2.13 → **v2.2.14** 应用成功；验证后进程已停
- 本批未验项（如实）：四端 UI 零实现（M47~M50）；ExportTableSpecs 新列（M47）；
  正式服部署（M51，S3 整改 fat jar 也未部署，届时一起）；TV 仓 16 个未提交文件未触碰

## 后续（M46+）

- M47 Android 基线：Room v10→v11 + 补录统一入口页 + 计时环节编辑器 + 睡眠方式段 +
  洗澡起止（提示词见 18 号契约附录）
- M48 Mini / M49 iOS 对齐（含全类型编辑补齐）；M50 TV 对齐 + TV-4 收口；M51 验收交付

---

## M51 验收交付（会话 2026-11-18 · dev 服验证 + 打包留档；未解冻，零正式服变更）

> 发布节奏判定见 `独立产品规划/12-V3解冻待办清单.md` 与
> `发布包/2026-11-18-M51-dev留档/M51-交付清单.md`：D1 于 M32 特批提前完成 ≠ 解冻日 D0
> （13 号 §七），本批只做 dev 18080 验证 + 打包留档。

### §七-6 状态复核 + fat jar 修正（本批最重要的修复）

- 源码侧 M47 已落地：`ExportTableSpecs.ANDROID_SCHEMA_VERSION = 11`；`sleeps` 列清单含
  `transitions`（`extraSkips` 跳过 CSV、JSON 全字段保留）、`baths` 含 `ended_at`（`timeColumns` + CSV 表头）；
  `ExportServiceImpl` 第 62 行写 `meta.schema_version`。Android `ExportTables.kt` 同口径镜像。
  **18 号契约 §七-6 / §十一 已同步状态（只补状态、不改口径）**。
- **M10 坑复现并修复**：11-17 22:26 的 fat jar 内嵌 `ANDROID_SCHEMA_VERSION = 10`、
  `sleeps` 列清单不含 `transitions`（M47 只改源码跑单测，未重打包、未 install 到 m2）。
  处置：删 `target/babylog-admin-server.jar` + `.jar.original` →
  `mvn -o -pl babylog-plugin-biz -am install -DskipTests` →
  `mvn -o -pl babylog-admin-server -am package -DskipTests`（均 BUILD SUCCESSFUL）→
  `javap` 复核内嵌 = **11**、列清单含 `transitions`、`V2.2.14` SQL 在场。
- 留档 jar：`发布包/2026-11-18-M51-dev留档/babylog-admin-server.jar`，
  SHA-256 `da44cf2f901c25a0f41d76790a5ea75241b43d7713a46ec9ba17d28fddbadfc7`（343099409 B）。
  **该 jar 未部署到正式服**；M32 正服在跑的仍是 `发布包/2026-11-15-正服/` 的旧 jar
  （哈希 `341ff3bf6fc27913af1ff3a715f1f8814c5475ff6967c847ebea9409e269b08a`）。

### Flyway 版本号核对（写 ALTER 前先 grep）

- 实测 `V2.2.0` ~ `V2.2.14` 全部占用，**下一可用版本号 = 2.2.15**。
- M51 不需要新增 DDL；`V2.2.14` 仍只在 dev 库应用，正式服未应用。

### dev 服验证（新 fat jar，14:02:41 起服）

| 项 | 结果 |
|---|---|
| `/tmp/m46-smoke.py` | **19/19**（记游标 → 增量拉） |
| `BabyLog-Mini/tests/m48-incremental-check.js` | **16/16**（补录增量可见 + 反证 `true` → 8342） |
| `/tmp/m51-export-check.py` | **9/9**（导出 `meta.schema_version=11`、CSV 列口径、建家端点无尾斜杠） |
| 合计 | **44/44，0 失败** |

起服口径 `env -u SERVER__PORT java -jar ... --spring.profiles.active=dev --server.port=18080`，
连 dev 库 `babylog_dev`，启动日志无 ERROR、Flyway 无需新迁移；日志 `/tmp/m51-dev-18080.log`。
dev/prod yml 的龙哥自填凭据保持未暂存，本批未 commit / 未 push。

### 未验项（如实）

- 正式服部署、`V2.2.14` 正式库应用、正服冒烟：未执行（未解冻，作业单见交付清单第四节）。
- `summary/today` 聚合、管理端、base 通道零改动口径不变；**S3 整改 fat jar 仍未上线正服**。

---

## M52 收口（会话 2026-11-18 · PivotOS 严整改 + 测试分层 + 死锁重试）

> 台账见 `独立产品规划/19-V2收口执行档案-2026-11-18.md` §六（本批对应 #11/#12/#13/#14）。

### 1. 全量 reactor 首次 BUILD SUCCESS（#11）

- `mvn -o test`：**56/56 模块 SUCCESS，0 failures / 0 errors**（离线模式 1:54）。
- 之前「6+13 红 + 挂死 20 分钟」的根因有两个，均已处置：
  1. **依赖内网中间件的集成测试**（13 个类，连 `175.24.176.176` 的 MySQL/Redis，凭据已轮换）
     → 根 pom surefire `excludes` 排除；Druid 的 `CreateConnectionThread` 会无限重试，
     实测单个模块可挂死 20 分钟以上（`SaveBatchClasspathGuardTest` 是最后暴露的那个）。
  2. **排查时才发现 pom 注释写「13 个类」而实际只列了 11 条**——本次补齐
     `SaveBatchClasspathGuardTest`、`MessageFacadeContractTest` 后对账一致。
- 排除**不降低**覆盖：架构门禁（A1~A12/C1~C4）全量生效；`SaveBatchClasspathGuardTest`
  的 classpath 维度已由 `WarmFlowVersionGuardTest#w3`（反射断言 `executeBatch(Collection,int,BiFunction)`
  存在，零依赖）在默认门禁里守住，被排掉的只是「真落库一次 saveBatch」那一层。
- **坑**：`mvn test` 必须加 `-o`。非离线时公共仓库探测会把每模块拖长，且远程库不可达时的
  重试让「看起来在跑」和「其实在等死锁」难以区分。

### 2. A10 白名单清零 + A12 新增 SQL 插值门禁（#13/#14）

- **A10（禁 `.last(`）**：原 8 处历史写法全部清偿——列表类改类型化分页
  （`Page` + `selectPage` / `IService.page`），精确取一条改 `getOne(wrapper, false)`。
  全仓 `grep -c '\.last('` = **0**，`ALLOWED_LAST_USAGES` 已清空（空表即「新增即红」）。
- **A11（17 条匿名端点登记）**：不是待修项而是**已接受匿名面**——登录入口/发码/注册/公钥/
  Prometheus 抓取本就该匿名，逐条写明了理由；规则同时校验「登记已失效」（端点改名/删除后
  登记必须同步删），所以它不会烂尾。**结论：无代码待办，口径记在此处即闭环。**
- **A12（新增，注解 SQL 禁 `${}`）**：A5 只禁了 mapper XML，注解 SQL
  （`@Select/@Update/@Insert/@Delete` + `<script>`）同样能写 `${}`。实测全仓 107 个
  `*Mapper.java` 里 `${` 出现 **0 次**，故直接一刀切（不做登记制）。规则含防空转下限（≥50 个），
  防止路径口径失效时静默假绿。
- **拼接 SQL 复核结论**：`generator` / `datainspect` / `ai-coding` 的 `information_schema`
  查询确实用字符串拼接，但**已过标识符白名单**（`MySqlInspector#validIdentifier` 正则 +
  `isTableAllowed` 白名单 + `tenantId` 取 Long），且刻意不放在 Mapper 接口里——A12 的注释
  把这条「绕过规则必须是一次显式设计决定」写成了口径。`<script>` 全部是
  `<foreach …>#{item}</foreach>`（参数绑定，非插值）。

### 3. push 死锁重试（M52 实测缺陷，iOS 真机链路暴露）

- 现象：iOS 全量测试并行跑 `FamilyLiveTests` 时，dev 服报
  `Deadlock found when trying to get lock` → 整批 push 变 1500「系统内部错误」。
- 定性：**不是测试脚本问题**。家庭成员两台设备同时首推同一个宝宝时，
  `INSERT … ON DUPLICATE KEY` 在唯一键上互相等待即成死锁。MySQL 自己的错误信息就写着
  「try restarting transaction」——不重试等于把偶发碰撞升级成客户端可见失败。
- 落法：`SyncServiceImpl` 的 `@Transactional` 换成 **显式 `TransactionTemplate`**
  （`push` = 外层重试 3 次 + 内层单事务 `pushInTransaction`）。
  **为什么不能在方法上加 `@Retryable`**：死锁回滚的是**整个事务**，语句级重试只会
  在已作废的事务里继续报错；而「外层重试 + 内层事务」在同一个类里用 `@Transactional`
  写不出来（自调用不走代理）——所以把事务边界显式画出来。
- 重试范围刻意收窄：只认 `PessimisticLockingFailureException`（Spring 对 MySQL 1213/1205
  的翻译）与 SQLState `40xxx`；其它异常原样抛出，不做「无脑重试掩盖问题」。
- 回归：`SyncServiceImplTest` 新增 3 条（首次死锁→重试成功 / 非锁异常不重试 / 持续死锁
  3 次后放弃）。biz **231/231 绿**（原 228）。iOS 侧同一套并行测试复跑 **297/297 全绿**。

### 4. ArchUnit A3 规则未注册（#12）

- 病灶：`@ArchTest` 注解行被写进了 `//` 行注释里，规则**从未执行**（注释里宣称「已注册」）。
- 处置后实测报告里出现 `a3_starters_must_not_depend_on_plugins`，P0ArchitectureTest 全绿。
- 教训：ArchUnit 的规则失效是**静默**的——「加了一条规则」不等于「这条规则在跑」，
  必须看 surefire 报告里有没有对应 testcase 名。A12 因此自带防空转下限。
