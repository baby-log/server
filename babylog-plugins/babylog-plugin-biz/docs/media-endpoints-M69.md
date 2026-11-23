# K2 / M69 · 媒体存储底座端点与运维清单（server）

> 契约来源：`独立产品规划/24-后续链统一契约.md` ② 媒体表 / ③ 存储适配器 / ④ 淘汰契约；
> 红线以 `22` §七（尤其 §7.2）为准；协作与验收口径见 `23` §五 K2、`23A` K2 行。
> 本文件是 K2 端点签名与运维配置的**唯一交付说明**；`24` 号原文未改一字。

---

## 一、边界（先声明，避免误读）

- **V3 前零画面数据**：端点只承载附件语义（E6 就医附件 / V3.1 手写导入拍照），
  `image/*`、`application/pdf` 等允许，`video/*` 一律 8423 拒收。不接摄像头帧流。
- **不做云同步开关**（归 K8 / `24` ①）、不改 19 张同步表语义。
- **两表不进同步通道**：`bl_media_file` / `bl_media_quota` 是服务端专用表，
  **不进 `SyncTable` 枚举、不参与 `/sync/changes`**（不新增第 20 张同步表），
  走下列独立业务端点，端点路径不占用 `/sync/*` 命名空间。
- **不引任何新 SDK**：S3 侧复用 `babylog-plugin-file` 同款 `software.amazon.awssdk:s3`
  （BOM 仲裁 2.49.3）与同一套 `babylog.file.*` COS 配置键；本地后端用 `java.nio`。
  因 P0 ArchUnit C1 / A1-A2 禁止 `com.babylog.biz..` 依赖 `com.babylog.file..` 实现层，
  且 `IFileFacade` 无字节流 put/get/delete，故在 biz 内建 `media` 垂直域自建适配器接口
  —— 复用的是同一 SDK / 同一协议 / 同一配置键与构造范式，不是跨插件调用。
- **零 yml 改动**：不改任何配置文件、不回显凭据；`MediaStorageProperties` 用
  Spring `Environment` 只读查询配置键，未配置的云后端调用返回 8421 且不阻断起服。

---

## 二、端点清单

### 2.1 App 端（`/app/babylog/media`，类级 `@SaCheckLogin(type = StpAppUtil.TYPE)`）

token 走 `Authorization: <裸值>`；`user_id` 一律服务端按 token 盖章，客户端传值无效。

| # | 方法 | 路径 | 入参 | 出参 | 说明 |
|---|---|---|---|---|---|
| 1 | POST | `/upload` | `multipart/form-data`：`file`(必填) + `MediaUploadCmd` 字段 | `R<MediaFileVO>` | 上传附件；容量满先「删最早」腾挪再复核；`backend` 仅 dev 实测可覆盖 |
| 2 | GET | `/file/{id}` | path `id` | `R<MediaFileVO>` | 元数据详情；不在 `visibleUserIds` 内返回 8420 |
| 3 | GET | `/files?childId=` | query `childId` | `R<List<MediaFileVO>>` | 按宝宝列，家庭可见范围内，`createdAt` 降序 |
| 4 | GET | `/file/{id}/content` | path `id` | `ResponseEntity<byte[]>` | 下载字节；按**行内** `storage_backend` 路由；同步返回（同 `BabyLogExportController`） |
| 5 | DELETE | `/file/{id}` | path `id` | `R<Void>` | 先物理删、成功后才 DB 逻辑删 |
| 6 | POST | `/file/{id}/confirm` | path `id` + JSON `MediaConfirmCmd` | `R<MediaFileVO>` | 确认 / 定稿帧转存（见 §五） |
| 7 | GET | `/quota?childId=` | query `childId` | `R<MediaQuotaVO>` | 读初始容量 / 调整后 / 已用 / 剩余 |

`MediaUploadCmd` 表单字段：`childId`(必填)、`mimeType`、`linkedRecordType`、
`linkedRecordId`、`detectedAt`、`isFinalFrame`、`backend`（均为可选覆盖）。
`MediaConfirmCmd` 字段：`linkedRecordType` + `linkedRecordId`（同时必填）。

### 2.2 管理端（`/babylog/media`，类级 `@SaCheckLogin(type = StpSysUtil.TYPE)`）

沿用项目惯例（管理端路径无 `/admin` 前缀）。用类级登录注解而非
`@SaCheckPermission`：K3 配额页落地时再一并登记菜单权限字符，本节点不新增权限字符，
也不影响 `AdminControllerPermissionContractTest`（只遍历硬编码的 5 个历史 controller）。

| # | 方法 | 路径 | 入参 | 出参 | 说明 |
|---|---|---|---|---|---|
| 8 | PUT | `/quota` | JSON `MediaQuotaAdjustCmd` | `R<MediaQuotaVO>` | 动态调整容量，只写 `adjustedBytes` |
| 9 | POST | `/retention/run` | 无 | `R<MediaRetentionVO>` | **运维手动触发**一次淘汰（不经 xxl-job） |
| 10 | POST | `/migrate` | query `from` / `to` / `limit` | `R<MediaMigrateVO>` | 存量对象手动迁移（见 §四） |
| 11 | GET | `/backends` | 无 | `R<Set<String>>` | 已注册后端标识诊断（`local` / `cos` / `oss`） |

`MediaQuotaAdjustCmd` 字段：`childId` + `ownerUserId`（必填，仅「行尚不存在」时用于建行；
已存在行保留原 `user_id`）+ `adjustedBytes`（>= 0，否则 8425）。

---

## 三、字段与全局契约坑（24 §五 / MEMORY.md）

| 项 | 实现 |
|---|---|
| 所有 Long → String | VO 内直接声明 `Long`，由 `starter-core` 的 fastjson2 `WriteLongAsString` 全局序列化；入站 Long 客户端发字符串，Spring 宽松转 `Long` |
| TINYINT 布尔 1/0 | `isFinalFrame` 用 `Integer`，JSON 输出 `0/1`，绝不 `true/false` |
| null 键缺省 | 全局 NON_NULL；`adjustedBytes` / `mimeType` / `confirmedAt` / `linked*` 可空即缺省，四端容忍缺键 |
| `user_id` 必填语义列 | 上传由 token 盖章写入；建配额行由 `ownerUserId` 写入；直写 `bl_*` 表必带，避免落 DEFAULT 0 拉不到 |
| 软删游标 | `deleteById` 走 `@TableLogic` ⇒ `UPDATE deleted=1`，`server_updated_at ON UPDATE` 自动刷新；**禁手写 version+1** |
| 家庭读侧 | `visibleUserIds`：`get` / `files` / `content` / `delete` / `confirm` 先解析范围；不在范围内与「不存在」统一 8420，避免枚举探测 |
| 同步表专属坑 | 两表非同步表，`preserveOnAbsentProperties` / `updateById 置空 serverUpdatedAt` 不适用（`24` ②-6） |

### 错误码（8420~8427，`babylog-plugin-biz-api` 契约模块）

| 码 | 常量 | 语义 |
|---|---|---|
| 8420 | `MEDIA_FILE_NOT_FOUND` | 文件不存在或不在家庭可见范围内（不区分） |
| 8421 | `MEDIA_BACKEND_UNAVAILABLE` | 后端未配置 / 不可用（本地目录不可写、COS/OSS 缺 endpoint 或凭据） |
| 8422 | `MEDIA_BACKEND_UNKNOWN` | 行内 `storage_backend` 不在注册表（配置漂移 / 迁移中断兜底） |
| 8423 | `MEDIA_PAYLOAD_INVALID` | 上传体为空 / 超单对象上限 / `video/*` 画面数据 |
| 8424 | `MEDIA_QUOTA_EXCEEDED` | 配额已满且保护文件无法腾挪，需后台调配额或确认关联 |
| 8425 | `MEDIA_QUOTA_INVALID` | `adjustedBytes < 0` 等参数非法 |
| 8426 | `MEDIA_STORAGE_IO_FAILED` | 适配器 put/get/delete 本次 IO 失败 |
| 8427 | `MEDIA_MIGRATE_FAILED` | 迁移源目标相同或参数非法 |

---

## 四、存储适配器三后端（24 ③）

统一接口 `MediaStorageAdapter`：`backend()` / `exists(key)` / `put(key,data,mime)` /
`get(key)` / `delete(key)`。`quota` **不在适配器里**——容量配额是业务语义，由
`bl_media_quota` 承载（对应 24 ③-1「或等价语义的最小接口集」与 ②-5「used_bytes 由文件行聚合」）。

| 后端 | 实现 | 用途 |
|---|---|---|
| `local` | `LocalMediaStorageAdapter`（`java.nio`，临时文件 + 原子移动，delete 幂等，路径穿越防御） | dev / 自托管，零配置 |
| `cos` | `S3MediaStorageAdapter`（S3 兼容） | 复用 `babylog.file.*` COS 配置 |
| `oss` | `S3MediaStorageAdapter`（同一个类，只换配置） | COS→OSS 换 endpoint / 凭据 / bucket，接口与业务层零改动 |

**「路径可变更」的收窄实现（24 ③-3）**：每行落 `storage_backend` + `object_key`，
读 / 下载 / 删除 / 淘汰**按行路由**（`MediaStorageRouter.adapterFor`），
只有 `writeBackend()` / `newObjectKey()` 参与新写入。⇒ 改配置不会隐式重定位存量对象。
存量搬迁只能走 `POST /babylog/media/migrate` 显式手动工具：
源存在性检查 → 读字节 → 按当前 `keyPrefix` 生成新键写目标 → **回写 DB 行** →
best-effort 删源对象；DB 回写成功才切换行路由，前序任一步失败保持原行可重试。

**配置键（只读查询，零 yml 改动）**：

| 用途 | 键（按优先级回落） | 缺省 |
|---|---|---|
| 新写入默认后端 | `babylog.media.default-backend` | `local` |
| 本地根目录 | `babylog.media.local-dir` | `${java.io.tmpdir}/babylog-media` |
| 对象键前缀 | `babylog.media.key-prefix` | `babylog-media` |
| 每宝宝初始容量 | `babylog.media.default-quota-bytes` | 1 GiB |
| 单对象上限 | `babylog.media.max-object-bytes` | 20 MiB |
| 保留时长 | `babylog.media.retention.confirmed-hours` / `.unconfirmed-days` | 2 / 3 |
| 单批上限 | `babylog.media.retention.batch-size` | 200 |
| COS endpoint/凭据/bucket | `babylog.media.cos.*` → **回落 `babylog.file.*`** | 未配置 ⇒ 8421 |
| OSS endpoint/凭据/bucket | `babylog.media.oss.*` | 未配置 ⇒ 8421 |

> dev 实测「本地 + COS 两个后端」：本地零配置可用；COS 侧用既有 `babylog.file.*`
> 真实 bucket，`upload` 时传 `backend=cos` 即走 S3 适配器（不落库生产凭据）。
> 若 dev 环境未注入 file 插件 COS 凭据，COS 端点返回 8421 —— 属「后端未配置」，
> 适配器代码路径本身已由本地后端与单测覆盖，实测结论在四问报告如实登记。

---

## 五、淘汰契约实现（24 ④）

任务本体：`MediaRetentionService.run()`（xxl-job 与运维诊断端点共用同一实现）。

**三类触发**（`MediaStorageProperties` 可配，允许误差）：

| 触发 | 判据 | 排序 |
|---|---|---|
| 已确认 2h | `confirmedAt` 非空且 < `now - 2h` | 单批 `batchSize` |
| 未确认 3 天 | `confirmedAt` 为空、`detectedAt` 非空且 < `now - 3 天` | 单批 `batchSize` |
| 容量满删最早 | `SUM(size_bytes) > effectiveBytes` 时按 `createdAt` 升序腾出超额 | 逐宝宝，`createdAt` 升序 |

**保护规则（统一在 `isProtected`，不在 SQL 里各写一份）**：
① `linkedRecordType` + `linkedRecordId` 同时非空 = 已关联正式记录（④-2）；
② `isFinalFrame=1` 定稿帧（④-4）。三类淘汰一律跳过并计入 `skippedLinked`。

**两步删与顺序（④-3 明确「两步顺序与失败重试口径归 K2」）**：本批选
**先物理删 → 成功后才 DB 逻辑删**：
- 适配器 `delete` 幂等（S3 DeleteObject / 本地 `deleteIfExists`），失败可安全重试；
- 物理删失败 ⇒ 保留 `deleted=0` 的 DB 行，下次任务自然重试，计入 `physicalDeleteFailed`；
- 若反过来「先逻辑删」，`@TableLogic` 会让 `deleted=1` 行对常规查询不可见，
  物理删一旦失败就留下「DB 已删、对象还在」且扫不回来的孤儿。
- 两步都做，严格满足「禁止只删 DB 不删文件、也禁止只删文件不删 DB」。

**确认瞬间定稿帧转存（④-4）**：`POST /app/babylog/media/file/{id}/confirm` 把该行
置 `confirmedAt` + `isFinalFrame=1` + 关联正式记录。**「转存」实现为元数据关联**，
不复制对象字节、不新建第二条媒体行 ⇒ 零双份存储，且该行因「已关联」被排除、
长期保留；原始帧流照常淘汰，用户回看正式记录附件时取到同一对象。

**`used_bytes` 口径**：由 `bl_media_file` 聚合（`SUM(size_bytes) WHERE child_id=? AND deleted=0`），
不落冗余列，读侧与淘汰任务共用 `MediaQuotaService.sumUsedBytes`，避免双写漂移。

---

## 六、xxl-job 配置清单（**交龙哥，AI 不启 xxl-job**）

| 项 | 值 |
|---|---|
| 执行器 `appname` | 沿用现有 `babylog` 执行器（不新建执行器） |
| JobHandler | **`mediaRetentionCleanup`**（`MediaRetentionJob.HANDLER`，逐字一致） |
| 任务类 | `com.babylog.biz.media.retention.MediaRetentionJob` |
| 建议 cron | `0 15 * * * ?`（每小时一次；三条触发本身允许误差） |
| 路由策略 / 阻塞处理 | 沿用现有清理任务同款（单机执行 / 丢弃后续调度） |
| 任务参数 | 无（时长与批量走 `babylog.media.retention.*` 配置键） |
| 失败重试 | 依赖「先物理删再逻辑删 + DB 行保留」自愈，不依赖 xxl-job 重试次数 |
| 注册方式 | **只 `@XxlJob`，无 `@PostConstruct` 本地兜底注册** —— 避免 AI 侧启动本地调度 |
| 手动验证入口 | `POST /babylog/media/retention/run`（sys 登录态，不经过调度器） |

> 待办（龙哥）：在 xxl-job  admin 控制台新建任务、填 cron、绑定执行器并启停。
> 本节点只交付代码 + 本清单，**未创建任务、未启动执行器、未改任何调度配置**。

---

## 七、复用点对齐（只对齐口径，不实现）

| 复用点 | 本节点对齐的口径 | 未做 |
|---|---|---|
| E6 就医附件上云 | 上传端点接受 `linkedRecordType` / `linkedRecordId`（如 `VISIT`），已关联文件不参与淘汰 | 未实现 E6 端侧接入 |
| V3.1 手写导入拍照上传 | 上传端点接受 `image/*` + `detectedAt`，走同一配额 / 后端 / 对象键口径 | **未实现 V3.1**，未预先定 V3.1 / V4.1 / V4.2 任何细节 |
| `24` ① 云同步开关 | 本节点完全不碰开关；V3 前零画面数据 | 开关归 K8 |

---

## 八、dev 冒烟步骤（脚本放 `/tmp`，不落仓）

1. 起服：`env -u SERVER__PORT java -jar ... --server.port=18080`（dev 服 AI 自启）。
2. `GET /babylog/media/backends`（sys 登录态）应返回 `local` / `cos` / `oss`。
3. `POST /app/babylog/media/upload`（`backend=local`，小 `image/png`）→ 记 `id`；
   断言 `userId` 为 token 用户、`storageBackend=local`、所有 Long 为字符串、`isFinalFrame=0`。
4. `GET /app/babylog/media/file/{id}` → 查；`GET .../content` → 字节一致。
5. `GET /app/babylog/media/quota?childId=` → 初始容量 / 已用 / 剩余正确。
6. `PUT /babylog/media/quota`（`adjustedBytes` 调小到略小于当前已用）→
   `POST /babylog/media/retention/run` → 该文件被「容量满删最早」淘汰；
   `GET /app/babylog/media/file/{id}` 返回 8420；本地目录对象已消失。
7. 重新上传两份：一份 `POST /file/{id}/confirm` 关联正式记录（定稿帧），一份不关联；
   再跑一次淘汰 → 断言「已关联记录不淘汰」（`skippedLinked >= 1`）。
8. `DELETE /app/babylog/media/file/{id}` → 两步删（DB 逻辑删 + 物理删）。
9. `POST /babylog/media/migrate?from=local&to=cos&limit=10` → 本地行切到 COS 后端；
   `GET .../content` 仍按行内新后端读到相同字节。
10. 冒烟脚本放 `/tmp`，不落仓；正服 SQL / Navicat / 手机 / xxl-job 启停一律不碰。

---

## 九、本节点未做（越权红线）

- 未写任何画面数据、未做云同步开关、未改 19 张同步表语义、两表未进 `SyncTable` 枚举。
- 未改 `24` / `23` / `15` / `18` / `20` / `21` 原文；未改 yml 生产凭据；未引统计/崩溃/推送 SDK；
  未启 `plugin-message`；未碰隐私政策单一源 md。
- 未实现 K3 配额页 / 四端接入、V3.1、V4.1、V4.2；未生成 K3 提示词。
- 未启 xxl-job（任务创建 / 执行器启停交龙哥）；未碰正服 SQL / Navicat / 手机；
  未处置 iOS 两处未暂存源码；未 commit / push。
