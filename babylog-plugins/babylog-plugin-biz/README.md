# babylog-plugin-biz

BabyLog 育儿记录的业务插件（V2.0 云基座，会话 M4 落地骨架与建表，会话 M5 落地 App 认证）。

- 契约模块：`babylog-plugin-biz-api`（`com.babylog.biz.api`）——错误码 `BabyLogErrorCode`（**8300-8399**，8xxx 段内空闲子段，migration/search/datainspect 已占 8000-8221）
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
