# K8 / M79 · 画面云同步开关解锁 · 服务端门禁说明（server）

> 契约来源：`独立产品规划/24` ① / ② / ③ / ④；`22` §7.1-1 / §7.2；`23` §五 K8；
> K2 端点基线 = `docs/media-endpoints-M69.md`（本节点未改其一字）。
> 本文件是 K8 服务端门禁的交付说明；`24` / `22` / `23` / M69 归档原文均未改一字。
> 本节点边界与放行口径以龙哥 §0.4 门禁 A / B / C / D / E 拍板为准；M 号 M79。

---

## 一、结论：K8 服务端零业务端点改动

K8 是「① 解锁」节点（`24` §六），不是「画面来源」节点——**原始帧流 / 画面采集来源归
K9（V4.0 哨兵采集侧）**。K2 / M69 已交付的媒体存储底座在 V3 前只跑附件语义，
K8 做的是把「用户主动选择的静态画面」纳入既有上传端点，因此服务端：

| 核对项 | 结论 | 证据 |
|---|---|---|
| 端点签名 / 路径 | **不改**，逐字对齐 M69 §2.1 | `BabyLogMediaController` 7 端点实读 + `MediaCloudSyncGateTest#appMediaEndpointPathsStayAlignedWithM69` 锁死 |
| 错误码 8420~8427 | **不改**、不新增码 | `BabyLogErrorCode` 实读，8 码齐备；无「未开云同步」专用码（判据走全局 1002 / 登录注解） |
| `bl_media_file` / `bl_media_quota` | **不进 `SyncTable`、不参与同步通道** | `SyncTable.java` 实读 19 项，两表不在；`MediaCloudSyncGateTest#mediaTablesStayOutOfSyncChannel` 锁死 |
| `video/*` 拒收（8423） | **保持** | M69 §一 / §七「端点逐字对齐」；见本文 §三 |
| 保留 / 淘汰（24 ④） | **沿用**，不改 | `MediaRetentionService#isProtected` / `evictForCapacity` / `skippedLinked` / `physicalDeleteFailed` 实读在位 |
| 存储适配器（24 ③） | **沿用**，不改 | `MediaStorageRouter#writeBackend` / `newObjectKey` / `adapterFor`（8422）实读在位；local / cos / oss 三后端 |
| 配额 / yml / sys_config | **不读不改** | 零 yml 改动；K8 红线「不读 / 不改 sys_config」 |
| 是否新增服务端开关状态 | **不新增** | 门禁 C「不自建第二套状态」；见本文 §二 |
| 是否新增收费项 | **不新增** | `24` ①-5 / `22` §7.2 #8：成本由开启用户承担，全版本免费、唯一收费=AI 按量 |

⇒ **server 仓 K8 交付 = 3 处过期注释订正 + 1 个门禁证明单测 + 本说明文档 +
六仓文档 K8 段**，无任何业务端点 / 表 / 错误码 / 配置改动。

---

## 二、「已开云同步」的服务端等价判据（门禁 C 落地）

`24` ①-3 要求「画面上传开关**仅对已开启云同步的用户可见**；未开云同步的用户完全看不到入口」。
按龙哥 §0.4 门禁 C 拍板「新建统一开关·四端，**不自建与同步引擎冲突的第二套状态**」，
服务端**不落任何开关状态字段**，唯一判据：

- `BabyLogMediaController` 类级 `@SaCheckLogin(type = StpAppUtil.TYPE)` ⇒
  无有效 app-user token 时全部 7 个端点被登录注解拦截（全局 1002 UNAUTHORIZED），
  画面帧根本到不了 `MediaFileService#upload`。
- `currentUserId()` 从 `LoginContext.getUserId()` 取，`MediaUploadCmd` **无 userId 字段**，
  `user_id` 由服务端按 token 盖章，客户端伪造无效。
- 读 / 下载 / 删除 / 确认一律先过 `SyncAccessScopeResolver.visibleUserIds()`，
  不在范围内与「不存在」统一 8420，不区分以免枚举探测。

**必然推论（四端实现底线，移交 Task #3~#6）**：新建的云同步总开关在**关闭时必须清凭证 /
停同步**，使 app-user token 失效（沿用 Android `CloudSyncViewModel#setEnabled(false)`
→ `auth.logout()` 既有模式）。若只置本地布尔而不清凭证，服务端仍持有效 token、
等价判据即被架空，等于要求服务端自建第二套状态——与门禁 C 冲突。

---

## 三、画面类型边界：为什么 `video/*` 仍 8423

| 依据 | 内容 |
|---|---|
| `24` §六 落地节点对照 | 「④ 原始帧流 / ① 画面来源」= **K9（V4.0 哨兵采集侧）**；K8 只负责「① 解锁」 |
| `24` ①-4 | V3 前一律禁止上传画面；`22` §三-1「云端存视频不在任何版本规划内」**此前**保持有效 |
| `22` §7.2 #1 | 云端存视频**有条件解禁**：仅 V3 大版本后 + 仅已开云同步用户 + 用户主动开启 |
| K8 提示词 | 「端点**逐字对齐** `docs/media-endpoints-M69.md`」；M69 §一「`video/*` 一律 8423 拒收」 |
| K8 红线 | 「不碰截长图热区」「不改截图默认不出设备默认值」 |

⇒ K8 解锁的「画面」= 用户主动选择的**静态截图 / 照片类**（`image/*`，K2 底座本就允许），
不是摄像头视频流。视频流 / 原始帧流仍归 K9，`video/*` 8423 拒收**保持不变**。
本口径在四问报告如实登记，避免被误读为「K8 漏解禁视频」。

---

## 四、隐私政策与开关文案一致性（与 Task #1 对齐）

政策单一源 V3.0（2026-11-26）第九节承诺：开关默认关、仅已开云同步用户可见、
未开启时零画面离设备、不自动采集不静默上传、可随时关闭、删除同步到云端、
不新增收费项。服务端门禁与之逐条对应：登录注解 = 未开云同步零上传；
`DELETE /file/{id}` 两步删（先物理删再 DB 逻辑删）= 删除同步到云端；
无付费入口 / 不读 sys_config = 不新增收费项。

---

## 五、本节点改动清单（server 仓）

| 文件 | 改动 |
|---|---|
| `media/service/MediaFileService.java` | 类注释「V3 前零画面数据 / 不做云同步开关（归 K8）」→ K8 解锁后语义 + 登录态等价判据 |
| `media/dto/MediaUploadCmd.java` | 类注释同上；明确无 userId、原始帧流归 K9、`video/*` 8423 保持 |
| `media/controller/BabyLogMediaController.java` | `@Operation` summary「V3 前仅附件语义」→「K8 解锁后含…静态画面」 |
| `test/.../media/MediaCloudSyncGateTest.java` | **新增** 4 个证明性单测（登录注解 / 无 userId / 两表不进同步通道 / 7 端点路径逐字对齐 M69） |
| `docs/media-cloud-sync-gate-K8-M79.md` | **新增** 本说明 |

> 三处源码改动**全部是注释 / 文档字符串**，零字节码行为变化；
> `MediaFileService#upload` 的 `video/*` 短路、`user_id` 盖章、配额、两步删均未动。

---

## 六、验证留痕

| 项 | 结果 |
|---|---|
| Maven 单测 | media 组 27/27 全绿；全量 `babylog-plugin-biz` **355/355 全绿**，`BUILD SUCCESS`（含新增 `MediaCloudSyncGateTest` 4 个） |
| ArchUnit P0 | **24/24 全绿**，`BUILD SUCCESS`（A8 表前缀 / A10 文本扫描未触红线） |
| dev 18080 冒烟 | 未跑（服务未起，按 K2~K7 先例如实登记，不阻塞验收） |
| 六仓 `PROGRESS.md` / `CHANGELOG.md` | 文末纯追加 K8 段（Task #8） |

---

## 七、本节点未做（越权红线）

- 未新增 / 修改任何端点签名、未新增错误码、未改 `SyncTable` 19 项语义、未碰同步通道。
- 未解除 `video/*` 8423、未接摄像头帧流、未做 K9 采集侧、未生成 K9 提示词。
- 未改 yml / 生产凭据 / `sys_config`；未新增服务端开关状态字段、未新增收费入口。
- 未改 `24` / `22` / `23` / M69 归档原文；未改 19 张同步表语义。
- 未起 xxl-job、未碰正服 SQL / Navicat / 手机 / 商店后台；未上传 1Panel、未 4G 自测。
- 未 commit / 未 push、未打 v3 tag、未改版本字段、未出 V3 发布包（门禁 A / D）。
- 未自行判定验收通过、未填 `23A` 验收结论两列。
