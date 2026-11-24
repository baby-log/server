# 更新日志

本文件记录 BabyLog 服务端的版本变更，格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。
服务端不单独发版号，变更按全局批次 M 序列与部署批次记录；逐批次实测证据见
[19-V2收口执行档案](../独立产品规划/19-V2收口执行档案-2026-11-18.md) 与本仓
[biz README](babylog-plugins/babylog-plugin-biz/README.md)。

## [2.4.0] — V2 固定版（2026-11-19）

> **版本号固定**：六端产品版本统一为 `V2.4.0`；服务端不单独发 Maven 版本号，
> `pom.xml` 的 `3.2.0` 是 PivotOS 框架版本，双轨保留不改坐标。
> 生产 fat jar `babylog-server-2.4.0.jar`（内嵌 prod yml，配置明文值由龙哥自填）。
> 尚未对外公开发布。按发布节奏，V3 完成前不做任何公开处理；正式服 M32 已部署，测试服/dev 服按批次起服验证。

### 修复（M53 · 验收问题修复批，仍归 V2.4.0）

用户验收发现的 8+4 条问题里，服务端承接以下四项（代码已落地，biz 232/232 全绿）：

- **多端不实时 / 「喝奶中」看不见**：移除 `feedings` 拉取谓词 `in_progress = 0`，进行中计时行正常下发；
  新增 `GET /app/babylog/sync/ping`（账号级变更戳，四端前台轮询即可秒级感知，小程序也能用普通 HTTP）；
  `push` 有行落库即抬高变更戳。同步触发加密由四端客户端承接。
- **冲突与重复记录**：`SyncPushResultVO` 新增 `conflicts` 明细（库内版本不旧于推送版本时不再静默跳过）；
  收到「已结束」的计时行时按 (账号, 宝宝, 类型) 软删其它设备留下的 `in_progress=1` 副本，
  避免多设备各开一个计时后残留成重复记录。
- **后台功能太少**：新增 `/babylog/admin/**` 七个只读端点（概览 / 账号 / 家庭 / 宝宝 /
  记录明细 / 疫苗 / 提醒），sys-admin 登录态鉴权，动态表名走白名单、用户输入全参数化；
  菜单 `V2.2.15__babylog_admin_console.sql`（5060~5064），前端页面由 ui 仓承接。
- **口径**：所有版本始终免费，不订阅、不买断；本地开发始终连 `127.0.0.1:18080`，
  dev 服由 AI 自行启动，xxl-job 不由 AI 启动（已写入项目长期记忆）。

验证：`mvn -o -pl babylog-plugins/babylog-plugin-biz test` → **232 tests / 0 failures / 0 errors**
（原 231 + 新增「结束计时收敛其它设备进行中行」1 条）；`P0ArchitectureTest` 全绿；
dev 服同账号双设备 push/pull 实测互通（含 `categories` 四分类 + `limit=200` 的 iOS 同款参数）。

### 修复（M52 收口，代码侧真缺陷）

- **ArchUnit A3 规则从未执行**：`@ArchTest` 注解行被写进 `//` 行注释里，注释却宣称已注册。
  移出注释后核对 surefire 报告出现 `a3_starters_must_not_depend_on_plugins`。
  教训：「加了规则」≠「规则在跑」，必须看报告里有没有对应 testcase。
- **push 死锁 1500**：iOS 并行全量测试期间 dev 服 `Deadlock found when trying to get lock`
  导致整批 push 返回 1500（家庭成员两台设备同时首推同一宝宝，ODKU 在唯一键互等，是真实生产场景）。
  `SyncServiceImpl` 改 `TransactionTemplate` 外层最多 3 次重试 + 内层单事务，
  重试范围只收 `PessimisticLockingFailureException` 与 SQLState `40xxx`；新增 3 条回归用例。

### 变更（M52 收口，规范侧）

- **A10**：8 处 `.last(` 全清偿（列表类改 `Page` + `selectPage` / `IService.page`，精确取一条改
  `getOne(wrapper,false)`），全仓 `*.java` 的 `.last(` = 0，`ALLOWED_LAST_USAGES` 白名单清空。
- **A12（新增）**：注解 SQL 同禁 `${}`，实测 107 个 `*Mapper.java` 命中 0 次，一刀切并带
  `scanned >= 50` 防空转下限。
- **全量 reactor 可跑**：13 个依赖内网中间件 `175.24.176.176` 的集成测试移出默认 surefire 门禁
  （Druid `CreateConnectionThread` 会无限重试导致挂死），覆盖不降级——classpath 维度由
  `WarmFlowVersionGuardTest#w3` 守住。

### 验证（真实数字）

| 项 | 结果 |
|---|---|
| `mvn -o test` | **BUILD SUCCESS，56/56 模块**，0 failures / 0 errors，1:27 |
| biz 单测 | **231/231**（原 228 + 死锁重试 3 条） |
| `P0ArchitectureTest` | **24 个唯一 testcase 全绿**（A1~A12 / C1~C4），0 failures / 0 errors / 0 skipped |
| 正服冒烟（M32） | 19/19 全绿 |

> surefire XML 根节点 `tests=19` 与文内 24 个 `testcase` 的差异是引擎统计口径，
> 判定「规则真实执行」以 testcase 名单为准，逐条含 A3 与 A12。

提交 `8db5d7d`（gitee + GitHub 双推）。

## 历史批次（择要）

| 批次 | 内容 |
|---|---|
| M4~M6 | 24 表 → 认证 → 同步协议（双段游标 + ODKU） |
| M8 / M10 / M11 | 设备 + base 通道 / 管理端 / 管理端前端 |
| M15~M19 | 家庭读侧、写拦截、V2.1 收官（含 M18 游标不前进修复） |
| M20~M23 | V2.2 小程序三批（mini/login 双 token、加入家庭、快速记录与收尾配套） |
| M32 | 正式服部署新版：1Panel + 正式库清空重灌 + 正服冒烟 19/19 |
| S2+S3 | PivotOS 严格整改：动态 SQL 彻底消除，23 视图实体 + 类型化存储 |
| M46 | 记录增强链第一棒：18 号契约 + Flyway V2.2.14（`bl_baths.ended_at` / `bl_sleeps.transitions`），biz 228/228 + dev 冒烟 19/19 |
| M56 | 同步修复：`/sync/ping` 长轮询改 `CountDownLatch` 阻塞式（弃 `DeferredResult`，避免 SaToken ThreadLocal 在 async dispatch 丢失导致 1500）+ `markChanged(visibleUserIds())` 可见范围级变更戳；五仓提交推送，biz 245/245 |
| M57 | 契约层：Flyway `V2.2.16` 给 `bl_children` 加 `feeding_method VARCHAR(16) NULL`；`BlChild` / `ExportTableSpecs`（`ANDROID_SCHEMA_VERSION` 保持 11）/ `AdminConsoleService` 补齐；新增 `ChildrenFeedingMethodContractTest`；biz 247/247（工作区未提交） |
| M58 | 收口：M57 契约层随五仓 conventional commit 入库（`ec818ce` 等，**只 commit 未 push**）；授权重启 dev 服重跑联机（`m44-dev-smoke.py` 首次实跑 65/65、iOS 325/325 含 8 条联机，M56 jar 基线回归、不覆盖 M57 服务端新代码）后优雅关闭；两处 test 域名临时改动保留；「发布包重建 + 正服部署」仅出配置对比表、未执行 |
| M59 | 决策收口：龙哥确认 M57 五仓提交「现在 push」（AI 交付逐仓命令、由龙哥自跑、未代跑未验证远端）；test 环境「验收未完成、暂不恢复」两处 test 域名保留；发布包重建与正服部署均不授权、正服保持 M32；只读核查六仓零漂移；口径修正「6 个文档文件」实为 5 个（server 无 PROGRESS.md） |
| M60 | 执行收口：四项全授权（push / 恢复 / 六端重建 / 部署）；两处 test 域名临时改动按第 3 节恢复且 diff 归零；五仓 tracking 引用全部到 M57（push 完成、远端实时待龙哥核实）；六端发布包重建判 OK（fat jar 内嵌 `V2.2.16` 已核对、Android/TV APK 带签名、Mini 显式覆盖正式域名、iOS/macOS `.app` 用 `OTHER_SWIFT_FLAGS=-disable-sandbox` 绕过 SwiftData 宏沙箱嵌套、`.app.zip`/DMG 重打）；正服部署因无服务器地址/凭据/1Panel 会话交回龙哥，正服 ping/changes 均 1002 与 11-20 实测不同、「正服=M32」存疑 |
| M61 | 决策收口：龙哥四项拍板 —— ① push 远端实时 SHA「不单独核实」（接受五仓 tracking 到 M57 作证据、留痕远端实时未核实，AI 沙箱 SSH 读私钥被 deny 不重试/不绕路）；② 发布包重建产物「验收通过」（6 端判 OK + 8 件 `-newermt 16:41` 全 NEW 实证、本轮不动不重跑）；③ 正服部署「龙哥手动、AI 不碰」（产出 `独立产品规划/13-D1正服部署前核对单-M61.md`：4.1 契约矩阵 + 1Panel 部署前确认项 + 单实例/Flyway/回滚/mp 域名/安全）；④ `临时接口地址改动记录.md` 补记恢复收尾（状态行改「已恢复」+ 新增第 5 节）。M61 只读复核六仓零漂移、两处临时域名 diff 归零、18080 无监听；5 个文档文件仍未提交（含 M61 节） |
| M62 | 正服部署结果回填（龙哥手动完成）+ 授权验收冒烟：正式域名 + 独立账号 35/35 全绿（19/19 判据 + M53 ping/conflicts/进行中行跨设备可见/计时收尾 + M57 feeding_method 往返作 V2.2.16 间接铁证）；只读复核六仓零漂移、两处临时域名 M62 三核归零；四项决策（部署完成授权冒烟 / 5 文档随下批入库不 commit / 记录留痕全部不动 / 暂无新需求）；正服实测 ping lastChangeAt 是字符串（覆盖核对单 4.1 旧描述），无一是线上缺陷 |
| M63 | 纯收口：龙哥四项拍板 —— ① 暂无新需求、纯收口（不落代码）；② 5 个未提交文档改为**现在单独 commit**（覆盖 M62「随下批入库」口径，只 commit 不 push、双推由龙哥）；③ 正服 `flyway_schema_history` 已由龙哥在 1Panel 直接确认（`V2.2.16` Success、无 failed），**回填 PROGRESS** 覆盖 M62「间接铁证 + 待确认」；④ M62 遗留的核对单 4.1 #1/#10「ping 返回 long 数字」滞后表述本轮一并修订为「正服实测 JSON 字符串、全局 Long→String 无例外」（该文件在工作区根独立目录、不入库）。M63 四核只读复核六仓零漂移、两处临时域名 diff 四段归零；`记录.md` 与测试环境 4 包保持不动；本批各仓 1 个 `docs(M63)` 提交后未提交项归零、tracking 仍停 M57（ahead=1） |
| M64 | 纯收口：龙哥三项拍板 —— ① 暂无新需求、纯收口（不落代码）；② 实测五仓仍 `ahead=1`，本批 5 个 `docs(M63)` **暂不处理双推**（保持留痕、AI 不代跑 push / 不给待推清单 / 不核实远端实时 SHA，双推仍由龙哥）；③ 暂无其他待办（V3 候选评估不启动）。M64 七核只读复核六仓零漂移、两处临时域名 diff 四段归零、18080 无监听、核对单 4.1 无「long 数字」残留 + M62 实测注记在；`记录.md` 与测试环境 6 条目保持不动。本会话零 commit / 零 push，收口写入后 4 仓 `PROGRESS.md` + server `CHANGELOG.md` 成为 5 个未提交文档、按 M62 惯例随下一批代码一并入库 |
| M67 | 服务端契约层（M67-A，2026-11-23）：新增第 19 张同步表 `bl_product_prefs`（Flyway `V2.2.17`）与 `bl_products.sort_order`（需求 #7）；偏好语义=**家庭共享**（龙哥拍板）；后台 `sortOrder` 可编辑、`defaultCount` 只读聚合（需求 #8 后台仅查看默认值）。biz **248/248**、ArchUnit **24/24**、本地 dev 服 e2e **10/10**；只动 server 仓、未 commit / 未 push；四端与后台 ui（M67-B~F）、M68 疫苗链未开工 |
| M67-H | 跨版本兼容修复（2026-11-23）：`products.sort_order` 登记为「载荷缺键保留现值」（`SyncSpec.preserveOnAbsentProperties`）。M67-A 上线后，M67 之前的 App 载荷无 `sortOrder` 键，按既有「缺键 = null」契约会在更新路径拼出 `SET sort_order = NULL` 撞 NOT NULL → 整条 8342，表现为老用户「改了产品名没生效」；而补 0 又会把后台设的排序静默清零。biz **253/253**（新增 5 例）、ArchUnit **24/24**；未登记列的既有契约零变化 |

### M67 · 服务端产品偏好契约层（M67-A，2026-11-23）

**需求与根因**：13 项优化里的产品共享 / 排序 / 默认值 / 上次使用（#1/#6~#11）。现有
`bl_products.enabled/last_used_at` 与全局产品定义同行；内置产品 id 1~99 经 base 单向
下发、push 8345 拒，无法承载「内置产品也可设默认/启停」；Mini 自定义产品纯本地
wx Storage 不接主同步通道，是 #1 共享失败的根因。

**两项拍板**（龙哥 11-23）：
1. 偏好（启停/默认/上次使用）= **家庭共享**：`product_prefs` 行随 `visibleUserIds`
   下发、家庭内成员可见可改（M16 家庭维度归属放行）；客户端写前查同 `product_id`
   已有行收敛，竞态产生的跨账号重复行按 version 取最新防御。
2. 排序 `sort_order` 存**产品行**（产品级共享，后台可维护含内置行）；选择排序口径 =
   上次使用 → 默认 → `sort_order` 降序 → 创建日期降序（需求 #10）。

**落码**：
- `V2.2.17__bl_product_prefs.sql`：建 `bl_product_prefs`（`uk_user_product(user_id,product_id)`
  唯一键；`category` 冗余分类免 join；TINYINT 启停/默认）+ `bl_products` 加 `sort_order`；
  旧列 `enabled/last_used_at` 保留不删（老版本兼容），新逻辑以偏好表为准。
- `BlProductPref` / `BlProductPrefSyncView` / `BlProductPrefMapper` /
  `BlProductPrefSyncViewMapper`；`SyncTable` 注册第 19 表（category=null 基础设施）、
  `SyncViewRegistry` + `SyncStoreHandlerConfiguration` 各注册一行 Handler（注册即用，
  零 per-table SQL）。8345 是 `PRODUCTS` 专属判断，新表不受影响。
- 后台：`ProductEditCmd/ProductVO` 补 `sortOrder`，`AdminProductServiceImpl` 编辑可改排序、
  列表按 sort_order 降序、新增 `defaultCount`（只读聚合 `is_default=1` 偏好行数，
  **不提供设置/取消**）。
- 测试：19 表注册守卫（SyncTableTest / SyncViewRegistryTest / SyncStoreWiringTest）、
  拉取全表次数、后台聚合与 MP lambda 缓存初始化。

**验证**：重打 fat jar（内层 biz jar 实证含 `V2.2.17` 与全部新类）→ 本地 dev 服 18080
Flyway 自动迁移 → `/tmp/m67a-dev-e2e.js` **10/10**：注册独立账号 → push 偏好行（引用
内置产品 id=1 不被 8345 拒）→ 全量拉（字段完整、无 `userId` 盖章、`nextSince` 字符串）
→ 同账号另一 token 可见 → version=2 更新 → `base/products` 内置行带 `sortOrder` 新列。
正式环境零写、xxl-job 未动、临时 dev 服用完已停。

**边界**：本批只落服务端契约层；四端选择器排序/默认/自定义保存弹框与 #5 品牌/医疗
关联产品（M67-B~E）、后台 ui 排序编辑列（M67-F）、M68 疫苗链均未开工。
`defaultCount` 是跨账号聚合计数，家庭共享语义下读侧仍须按 product_id+category 收敛，
展示口径由 M67-F 决定。

### 修复（M67-H · products.sort_order 跨版本兼容，2026-11-23）

**问题**：`bl_products.sort_order` 是 V2.2.17 新增的 `INT(11) NOT NULL DEFAULT 0` 列。
M67 之前的 App 版本，products 载荷里**没有 `sortOrder` 这个键**。同步存储对「缺键」的
既有契约是「按 null 写入」（对齐旧 ODKU 显式绑定 NULL），于是同一条老载荷在两条路径上
表现不同：

| 路径 | 实际行为 | 用户感受 |
|---|---|---|
| 更新（行已存在） | `SET sort_order = NULL` 撞 NOT NULL → 整条 8342 拒收 | 改产品名/停用产品「没生效」，下次拉取又变回旧值 |
| 插入（新自定义产品） | MyBatis-Plus `NOT_NULL` 策略省略该列 → 落列 DEFAULT 0 | 正常（正好等于「不参与排序」） |

**修法**：不给 `sortOrder` 补 0——那会把后台设的排序静默清零；改为在注册表上显式登记
「缺键保留现值」，把「缺键」与「显式 null」彻底分开：

- `SyncViewRegistry.SyncSpec` 新增第 6 个组件 `preserveOnAbsentProperties`，并保留原 5 参
  构造器，既有 23 处注册零改动；仅 `PRODUCTS` 登记 `{sortOrder}`。
- `GenericSyncTableHandler.upsert`：缺键且登记在该集合的列**不写实体、不进更新 SET**；
  `versionGatedUpdate` 按此跳过。显式 null（键存在、值 null）不放宽，仍由库拒绝 → 8342。
- 插入路径不变：实体里该字段仍为 null，由 MP 的 `NOT_NULL` 策略省略，落列 DEFAULT 0。

**契约边界（刻意不扩散）**：只对登记列生效。未登记列「缺键 = 置 NULL」的既有契约、
插入路径的列 DEFAULT 行为全部零变化——`SyncStoreH2Test.missingPayloadFieldsGetNullNotDefaults`
仍绿即是证据。后续若再加 NOT NULL 列且要求老客户端无感，登记一行即可。

**测试**：新增 `SyncStoreProductCompatH2Test` 4 例（H2 真库 + 真实 MyBatis-Plus 链路，
表结构镜像 V2.2.4 + V2.2.7 user_id + V2.2.17 sort_order）：老载荷改名保留排序、
老载荷首插落 DEFAULT 0、显式 null 仍被库拒、未登记列缺键仍置 NULL；
`SyncViewRegistryTest` 加注册守卫（**只有** products 的 `sortOrder`，且不得与拒收列重叠）。

**验证**：biz **253/253**（原 248 + 新增 5）、ArchUnit **24/24**、`BUILD SUCCESS`。

**边界**：本批只动 server 仓，未 commit / 未 push；未起 18080 用真实老载荷打一发
（H2 覆盖的是同一条 `versionGatedUpdate` SQL 路径，差别仅 H2 与 MySQL 的报错码）；
客户端零改动——新版本本来就发 `sortOrder`，老版本靠本次服务端兜底。

### M69 · K2 媒体存储底座 · 服务端（2026-11-23~24）

**范围**：只改 server 仓，消费 `独立产品规划/24` 号 ②③④ 契约（**24 号原文未改**）；M 号 M69（写前 grep 六仓 PROGRESS 占用 0）。V3 前零画面数据，不碰云同步开关（K8）。

**新增表**：`V2.2.18__bl_media.sql` → `bl_media_file` / `bl_media_quota`。两表与 `bl_family` / `bl_device` / `bl_vaccine_schedule` / `bl_ai_quota` 同口径，**明确不进 `SyncTable` 枚举、不参与 `/sync/changes`**，走独立业务端点 ⇒ 不新增第 20 张同步表。`user_id` 是直写 `bl_*` 表的必填语义列（正服迁移漏写落 DEFAULT 0 拉不到的坑）；`used_bytes` 口径二选一选「由 `bl_media_file` 聚合」（`SUM(size_bytes) WHERE deleted=0`），不落冗余列避免双写漂移。

**三后端统一接口**：`MediaStorageAdapter`（`backend` / `exists` / `put` / `get` / `delete`），`local` 用 `java.nio`、`cos` / `oss` 共用 `S3MediaStorageAdapter`。S3 侧**复用 `babylog-plugin-file` 的 `S3CompatibleStorageStrategy` 同款 `software.amazon.awssdk:s3`（BOM 仲裁 2.49.3）与 `babylog.file.*` COS 配置键**，零 yml 改动、零新 SDK；因 P0 ArchUnit C1 / A1-A2 禁止 `com.babylog.biz..` 依赖 `com.babylog.file..` 实现层、`IFileFacade` 又无字节流 put/get/delete，故在 biz 内自建 media 垂直域——复用的是同一 SDK / 同一协议 / 同一配置键与构造范式，不是跨插件调用。每行落 `storage_backend` + `object_key`，`MediaStorageRouter` **按行路由** ⇒ 改 `defaultBackend` / `keyPrefix` 只影响新写入，存量键不变，搬迁只能走 `MediaMigrationService`（离线手动工具，禁止隐式全量重定位）。

**端点**：App `/app/babylog/media`（`@SaCheckLogin(app-user)`）7 个、管理端 `/babylog/media`（类级 `@SaCheckLogin(sys)`，不新增菜单权限字符）4 个。读侧按 `visibleUserIds` 家庭口径，不存在与无权统一 8420 避免枚举探测；下载同步返回 `ResponseEntity<byte[]>`（不用 StreamingResponseBody，异步二次 dispatch 会重走鉴权且上下文已清）。出站 Long 全局 `WriteLongAsString`、`isFinalFrame` 用 Integer 0/1、null 键缺省。错误码新开 8420~8427 子段。

**淘汰（24 ④）**：`MediaRetentionService` 三类触发（已确认 2h / 未确认 3 天 / 容量满按 `createdAt` 升序删最早），保护规则统一在 `isProtected`（`linked_record_type`+`linked_record_id` 同时非空 = 已关联正式记录；`isFinalFrame=1` 定稿帧），三类一律跳过并计 `skippedLinked`。**两步删顺序**（④-3 授权 K2 自定）：选「先物理删 → 成功后才 DB 逻辑删」——适配器 `delete` 幂等、失败保留 `deleted=0` 的 DB 行供下次任务自然重试；若反过来先逻辑删，`@TableLogic` 会让 `deleted=1` 行对常规查询不可见，物理删失败就留下扫不回来的孤儿。确认瞬间「定稿帧转存记录附件」实现为**元数据关联**（不复制字节、不新建行），该行因已关联被排除、长期保留。`MediaRetentionJob` 只出 `@XxlJob("mediaRetentionCleanup")` 代码 + 配置清单，**刻意不加 `@PostConstruct` 本地兜底注册**（AI 不启 xxl-job，启停交龙哥）。

**测试**：新增 4 个单测类共 28 例（`MediaStorageRouterTest` 8 / `LocalMediaStorageAdapterTest` 5 / `MediaRetentionServiceTest` 5 / `MediaFileServiceTest` 10），覆盖按行路由、未知后端 8422、本地适配器真实 `@TempDir` 读写与路径穿越、三类淘汰计数、已关联/定稿帧保护、物理删失败保留 DB 行、容量删最早、上传 `user_id` 盖章、`video/*` 拒收、配额满 8424、家庭范围外 8420、两步删顺序、定稿帧确认。

**验证**：biz **281/281**（原 253 + 新增 28）、ArchUnit `P0ArchitectureTest` **24/24**、`test-compile` 257 主源码 + 32 测试源码全绿、`BUILD SUCCESS`。

**坑（本批实证）**：`mvn package -pl babylog-admin-server -DskipTests` 的**增量构建复用了旧 fat jar**——首轮日志写着 `Replacing main artifact`，实际产物 mtime 仍是 16:58（约 7 小时前），抽出 `BOOT-INF/lib/babylog-plugin-biz-3.2.0.jar` 一看：0 个 media 类、无 `V2.2.18`、无 `MEDIA_` 错误码。改走 **`clean package`** 后 mtime=当刻，内嵌核验才全绿（26 media 类 / 1 个 V2.2.18 SQL / 3 个端点与 job 类 / 8 个 `MEDIA_` 错误码）。⇒ 以后「改 main 必重打 fat jar」一律 `clean package`，并**按退出码 + 时间戳 + 内嵌常量池三件套判 OK**，不能只看 BUILD SUCCESS。

**验证未过 / 未执行（如实登记）**：dev 18080 冒烟**未跑**——本机 18080 无监听、3306 无监听、无 mysql 客户端、dev 服未起（脚本 `/tmp/k2_media_smoke.sh` 已备，交龙哥在有库环境执行）；COS 后端**未实测**（dev 未注入 COS 凭据，端点返回 8421 属「后端未配置」，S3 适配器代码路径由本地后端与单测覆盖）；xxl-job 任务创建与启停**交龙哥**。

**边界**：零画面数据、不改 19 张同步表语义、不改 yml 生产凭据、不引统计/崩溃/推送 SDK、不启 plugin-message、不改 23/24/15/18/20/21 原文、不实现 V3.1/V4.1/V4.2、不预先实现 K3、不碰正服 SQL/Navicat/手机、不改隐私 md；**未 commit / 未 push**。

## 未解冻 / 候选（非 V2 缺口）

- 按宝宝粒度权限裁决、refresh 轮换（M20+ 候选）
- 7xxx 错误码重叠、starter-auth `DemoAcceptanceTest` 配置债（框架遗留，不动）
- V3 公开处理（部署解冻与内测包压到 V3 完成）

## K4 · M71 站内通知 + 单人确认 + 时间口径 + 无头异常 · 服务端（2026-11-24，工作区未提交 · 未纳入版本段落）

> 本批是 `develop` 上**未提交未提审**的开发批次（M 号 M71），不新开版本号段落、不回改 `[2.4.0]` 及以上历史、不回改 M67/M69 章节。逐条交付与验证见 [PROGRESS.md](PROGRESS.md) 尾章「M71」。四端（Android/iOS/TV）读侧评估为**零改动**。

- **落库 / 契约**：新增 Flyway `V2.2.19__bl_notice.sql` → `bl_notice`，**服务端专用表**：不进 `SyncTable` 枚举、不参与 `/sync/changes`、端点不占 `/sync/*`，与 `bl_media_file`/`bl_media_quota`/`bl_family`/`bl_device` 同口径（`24` ⑤）。含 4 个审计列（继承 `BaseDO` 自动填充，K2 媒体表同款教训）；`user_id` 按 token 盖章、`server_updated_at ON UPDATE` 自动维护双段游标。错误码新开 8440~8443 子段（写前复核 8440+ 全仓 0 命中）。
- **端点**：App `/app/babylog/notice`（类级 `@SaCheckLogin(app-user)`、裸 token）3 个——`POST /events`（构造事件·K4 接收侧验证入口，零画面）、`GET /changes`（按 `visibleUserIds` 家庭读侧拉取式，双段游标 `since+sinceId`，`nextSince` 原样回传）、`POST /{id}/confirm`（单人确认）。服务端存、小程序拉；不动 plugin-message、不引推送 SDK、不发一次性订阅消息。
- **单人确认 + 时间口径（24 ⑥）**：复用 `version` 乐观锁 + `conflicts`（结构对齐 M53 `SyncConflictVO`），两名成员同时确认仅一人成功（单行原子 CAS `WHERE version=? AND is_confirmed=0`），后到者 `confirmed=0` + 一条 conflicts 出队拉取。`detectedAt`（13:30 识别）/ `confirmedAt`（13:50 确认）都是 Long 字符串，**落正式记录用 `detectedAt`**（`businessTime=detectedAt`），`confirmedAt` 只作审计。`version`（CAS 条件）与 `server_updated_at`（增量拉游标）两套绝不混用；正式记录由客户端走既有 `/sync/push` 到 19 张同步表，**服务端零代写**、自动检测永不自动落库保持（`22` §7.2）。
- **无头异常（24 ⑦）**：纯函数规则引擎 `HeadlessAnomalyRule` 加「醒来无入睡 `WAKE_WITHOUT_SLEEP` / 结束无开始 `END_WITHOUT_START`」两条；命中**如实记录**——逐条合成**单点行**（只有 `eventAt` 一个时间点、非计时行、无 `endedAt`/pauseEvents）+ `isHeadless`/`isAbnormal`=1 异常标记 + 异常站内消息，全体成员可见；不丢弃、不伪造配对、不假装完整会话、**不复用 `15` §二 同 ID 收尾、不造第二条结束行**（契约扩展而非改写）。配对齐全则返回空列表、不造假异常。V3 前零画面：`events` 只接结构化事件点（type+at，零字节），不接采集、不实现 K9、不定义 `bl_alert_rule` 阈值外的东西。
- **测试**：新增 2 单测类共 26 例（`HeadlessAnomalyRuleTest` 9 纯函数 / `NoticeServiceTest` 17 Mockito），覆盖两条规则、配对齐全不伪造、脏数据宽容、13:30/13:50 时间口径、两人确认仅一人成功 + conflicts、非家庭成员 8440、增量拉「记游标→增量拉」+ `visibleUserIds` 隔离、刚落库 `serverUpdatedAt` 未回填时键缺省不输出 `"0"`。坑：单测 JVM 无 MP 启动引导，`LambdaUpdateWrapper.set(SFunction)`/`LambdaQueryWrapper` 方法引用需 `@BeforeAll` 用 `TableInfoHelper.initTableInfo(assistant, BlNotice.class)` 初始化列缓存（与 `AdminProductServiceImplTest` 同款）；`getParamNameValuePairs()` 是惰性绑定，断言前须先触发 `getSqlSegment()`。
- **验证**：biz 单测 **307/307**（原 281 + 26）、ArchUnit `P0ArchitectureTest` **24/24**（本地仓 biz jar 内 20 个 notice 类全在扫描路径，A8 `bl_` 前缀 / A11 类级鉴权 / A10 无 `last()` / A12 无 `${}` / C1-C2 跨插件依赖全绿）、`test-compile` 全绿。
- **验证未过 / 未跑（如实登记，不伪造）**：`23` §五 K4 五项构造事件冒烟（13:30→13:50 正式记录时间 / 两人同时确认仅一人成功 / 无头异常全体成员可见 / 家庭读侧可见·非成员不可见）**全部未跑**——本机 18080/3306 无监听、无 dev jar、无 mysql 客户端、无登录态。补跑条件：龙哥在有库环境起 dev 服 + 三个 app-user token（同家庭两成员 + 非成员）+ `bash /tmp/k4_notice_smoke.sh`（`bash -n` 通过、缺凭据 SKIP 不伪造）；同等语义已由服务端单测覆盖。
- **边界 / 遗留**：K0 四项 + K2 三项 + K3 三项遗留不随本节点关闭、只登记移交；零画面数据、不碰云同步开关（K8）、不改 19 张同步表语义、不改 yml 生产凭据、不碰正服 SQL/Navicat/手机/xxl-job、不改 23/24/22/15/18/20/21 原文、未自行判定验收、未生成 K5 提示词；**未 commit / 未 push**，报告后即停待龙哥综合验收。

## K5 · M73 V3.0 AI 第一批 · 服务端（2026-11-24，工作区未提交 · 未纳入版本段落）

> `develop` 上**未提交未提审**的开发批次（M 号 M73），不新开版本号段落、不回改 `[2.4.0]` 及以上历史、不回改 M67/M69/M71 章节。逐条交付与验证见 [PROGRESS.md](PROGRESS.md) 尾章「M73」。

- **拍板（龙哥 2026-11-24）**：设额度 + 超限拒绝（`PARSE_RECORD` 20 次/天、`WEEKLY_REPORT` 2 次/周，数值只在 yml）/ 周报仅按需生成（不引 xxl-job）/ 计次按**家庭 × 功能** / **不做付费入口**（全版本免费、唯一收费=AI 按量的框架不动，也不新增收费项）。
- **落库**：Flyway `V2.2.20__bl_ai_quota_scope.sql` —— `bl_ai_quota` 加 `scope_key`（`F:<family>` / `U:<user>`），唯一键换 `uk_scope_feature`；仍是**服务端专用表**（不进 `SyncTable`、不参与 `/sync/changes`）。
- **端点**：`/app/babylog/ai/parse-record`（自然语言 → 草稿，`needConfirm` 恒 true）、`/app/babylog/ai/weekly-report`（按需生成）。**只产草稿、不落库**：19 张同步表零写入，正式记录由客户端确认后走既有 `/sync/push`。
- **AI 接线**：只走 `plugin-ai` 的 `IAiFacade`（biz 新增只依赖 `babylog-plugin-ai-api`），零新 SDK；用 `dynamicChat` 而非 `internalChat`——没有 AI 配置时宁可报错，也不用静态兜底文案冒充结果。
- **计次**：预占（单条条件更新 `quota_used < quota_limit`，并发不超额）→ 失败回滚；窗口到期条件归零（DAILY 次日 00:00 / WEEKLY 下周一 00:00）。
- **降级**：无 provider / 无 Key → `5020`；配了但调用不通（Key 失效 / base-url 不可达）→ `5022`；输出不合 JSON 约束 → `8461`；额度用完 → `8460`（模型一次不调）。全部不抛 5xx、不返空草稿、不伪造内容；5020/5022 均为**复用既有码**，未新造。
- **周报**：5 张同步表只读聚合出硬数字，模型只写 3~5 句解读（数字不经过模型）。
- **测试**：新增 3 个单测类 34 例中的 28 例（`AiQuotaServiceTest` 12 / `BabyLogAiServiceTest` 10 / `RecordDraftParserTest` 6）；biz 全量 **335/335**、ArchUnit **24/24**、`clean package` fat jar 三件套核过。
- **验证未过 / 未跑（如实登记，不伪造）**：dev 18080 冒烟未跑（本机 18080/3306 无监听、无 java/mysql、无登录态）；脚本 `/tmp/k5_ai_smoke.sh` 已备，缺凭据 SKIP 退出码 2。补跑条件见 `babylog-plugin-biz/docs/ai-endpoints-M73.md` §七。
- **边界**：不做 K6 客户端、不碰 K7 隐私 md / K8 云同步开关、不动媒体与通知底座、不改 19 张同步表语义、不引 SDK、不改 yml 生产凭据、不改 23/24/22/15/18/20/21 原文、未自行判定验收；**未 commit / 未 push**。
