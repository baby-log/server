# server · PROGRESS

> 本仓此前无 `PROGRESS.md`（批次流水散见 `CHANGELOG.md` 与工作区日志）。按 `独立产品规划/23` §五 K0 要求，自本节点起建立进度台账，首条即基线锚点。

## K0 · 基线锚点（2026-11-23 · 节点制起点）

- 本地基线 tag `babylog-v2.4.1` → `f229ab8`（当前 HEAD / `develop`）；六仓同名、仅本地、未 push、不占 M 批次；上游 `v2.5~v3.2` 框架历史 tag 未动。本行属 K0 授权文档改动，未 commit。

## M69 · 媒体存储底座 · 服务端（K2，2026-11-23~24）

- **节点**：K2「② V3 前地基」，只改 server 仓；基线 HEAD `f229ab8` / `develop`；M 号 **M69**（写前 grep 六仓 PROGRESS 占用 0）。
- **契约**：消费 `24` 号 ② 媒体表 / ③ 存储适配器 / ④ 淘汰契约；红线以 `22` §七为准（**V3 前零画面数据**）。`24` 号原文未改一字。
- **落库**：Flyway `V2.2.18__bl_media.sql` → `bl_media_file` / `bl_media_quota`，**服务端专用表，不进 `SyncTable` 枚举 / 不参与 `/sync/changes`**（不新增第 20 张同步表）；`user_id` 必填语义列、`used_bytes` 由文件行聚合不落冗余列。
- **适配器**：`local` / `cos` / `oss` 三后端统一接口；S3 侧复用 file 插件同款 AWS SDK v2 2.49.3 与 `babylog.file.*` COS 配置键（零 yml 改动），因 ArchUnit C1 禁跨插件依赖而在 biz 内自建 media 垂直域。每行固化 `storage_backend` + `object_key` **按行路由** ⇒ 配置变更只对新写入生效，存量走 `POST /babylog/media/migrate` 手动工具。
- **端点**：App `/app/babylog/media`（上传 / 查 / 列 / 下载 / 删除 / 确认 / 配额读 7 个）+ 管理端 `/babylog/media`（配额调整 / 淘汰诊断 / 迁移 / 后端诊断 4 个）；签名、全局契约坑、错误码 8420~8427、xxl-job 配置清单见 `babylog-plugin-biz/docs/media-endpoints-M69.md`。
- **淘汰**：`MediaRetentionService` 三类（已确认 2h / 未确认 3 天 / 容量满删最早）+ 保护（已关联记录 / 定稿帧）+ **先物理删再逻辑删**两步（④-3 授权 K2 自定顺序，失败保留 DB 行可重试）；`MediaRetentionJob` 只出 `@XxlJob` 代码与配置清单，**AI 不启 xxl-job**。
- **验证**：biz 单测 **281/281**、ArchUnit `P0ArchitectureTest` **24/24**、`clean package` fat jar 内嵌常量池核验通过（26 media 类 / V2.2.18 SQL / 3 端点 job 类 / 8 个 MEDIA_ 错误码 / mtime=当刻）；本地后端 5 例真实文件系统实测。
- **未过 / 移交**：dev 18080 冒烟**未跑**（本机无 MySQL / 3306 无监听 / 无 mysql 客户端 / 服务未起；脚本 `/tmp/k2_media_smoke.sh` 已备）；COS 后端**未实测**（dev 未注入凭据）；xxl-job 任务创建交龙哥。
- **状态**：待龙哥综合验收；**未 commit / 未 push**（`PROGRESS.md` 为 `??` 未跟踪，按 23 §2.2-3 追加即可，勿擅自 git add）。

## M71 · 站内通知 + 单人确认 + 时间口径 + 无头异常 · 服务端（K4，2026-11-24）

- **节点 / 范围**：K4「② 接收侧闭环」，M 号 **M71**（写前 grep 六仓 PROGRESS 占用 0）。改 server + Mini 两仓为主，四端（Android/iOS/TV）读侧评估为**零改动**。基线 HEAD `43a35e4`/`develop`（K2 只 commit 未 push）；消费 `24` 号 ⑤ 站内通知 / ⑥ 单人确认 / ⑦ 无头异常，原文未改一字。
- **落库**：Flyway `V2.2.19__bl_notice.sql` → `bl_notice`，**服务端专用表，不进 `SyncTable` 枚举 / 不参与 `/sync/changes` / 端点不占 `/sync/*`**（不新增第 20 张同步表）；含 4 个审计列 `create_by`/`create_time`/`update_by`/`update_time`（继承 `BaseDO` 自动填充，K2 媒体表同款）。`user_id` 必填语义列按 token 盖章、`server_updated_at ON UPDATE` 自动维护双段游标。
- **端点**（`BabyLogNoticeController`，类级 `@SaCheckLogin(app-user)`、裸 token）：`POST /app/babylog/notice/events`（构造事件·K4 接收侧验证入口，零画面）/ `GET /changes`（按 `visibleUserIds` 家庭读侧拉取式、双段游标 `since+sinceId`，`nextSince` 原样回传）/ `POST /{id}/confirm`（version 乐观锁 CAS，先到者赢、后到者出 `conflicts` 拉取）。错误码 8440~8443（8440+ 全仓 0 命中所选新段）。
- **单人确认 + 时间口径**：`version` 是 CAS 的 WHERE 条件（非同步游标），`server_updated_at` 才是增量拉游标，两套绝不混用；先到者 `confirmed=1` + `businessTime=detectedAt`（13:30 识别 / 13:50 确认 ⇒ 正式记录业务时间=13:30，`confirmedAt` 只作审计），后到者 `confirmed=0` + `conflicts`（结构对齐 M53 `SyncConflictVO`）出队拉取。**服务端不代写正式记录**：客户端拿 `businessTime` 走既有 `/sync/push` 到 19 张同步表，自动检测永不自动落库保持。
- **无头异常**：纯函数规则引擎 `HeadlessAnomalyRule.detect(...)` 两条（醒来无入睡 `WAKE_WITHOUT_SLEEP` / 结束无开始 `END_WITHOUT_START`）；命中逐条合成**单点行**（只有 `eventAt` 一个时间点 + `isHeadless`/`isAbnormal`=1）+ 异常站内消息，全体成员可见；不伪造配对、不假装完整会话、不复用同 ID 收尾、不造第二条结束行（对 `15` §二 的契约扩展而非改写）。配对齐全则返回空列表、不造假异常。
- **V3 前零画面**：`events` 只接结构化事件点（type + at，零字节），不接摄像头/麦克风、不定义 `bl_alert_rule` 阈值外的东西、不实现 K9 采集。
- **验证**：`mvn -pl babylog-plugins/babylog-plugin-biz test` **307/307**（原 281 + 新增 26：`HeadlessAnomalyRuleTest` 9 / `NoticeServiceTest` 17）、ArchUnit `P0ArchitectureTest` **24/24**（biz jar 内 20 个 notice 类全在扫描路径，A8 表前缀 `bl_` / A11 类级鉴权 / C1-C2 跨插件 / A10 无 `last()` / A12 无 `${}` 全绿）、`test-compile` 全绿。单测层覆盖 13:30/13:50 时间口径、两人确认仅一人成功 + conflicts、单点行如实落库 + 异常标记、`visibleUserIds` 隔离（非家庭成员 8440 / 单用户 scope 排除他人行）、「记游标 → 增量拉」、刚落库 `serverUpdatedAt` 未回填时键缺省不输出 `"0"`。
- **未过 / 移交（如实登记）**：`23` §五 K4 五项构造事件冒烟**全部未跑**——本机 dev 18080/3306 无监听、无 dev jar、无 mysql 客户端、无登录态，无法注册/登录换 app-user token；补跑条件：龙哥在有库环境起 dev 服（Flyway V2.2.19 已建表）+ 三个 token（同家庭两成员 + 非成员）+ `bash /tmp/k4_notice_smoke.sh`（脚本 `bash -n` 通过、缺凭据时 SKIP 不伪造结果）。K0 四项 / K2 三项 / K3 三项遗留不随本节点关闭；K4 新登记：通知端点未纳入 `AdminControllerPermissionContractTest`（本节点不涉管理端）、`POST /events` 为接收侧验证入口（K9 采集侧接真实事件点时须复用规则引擎语义并复核鉴权/限流）。
- **状态**：待龙哥综合验收；**未 commit / 未 push**（惯例 A）。四端读侧零改动（通知是小程序内轻量站内消息、按家庭读侧拉取；Android 隐私政策已明写「不接推送 SDK」，不引 SDK / 不改权限 / 不动签名 / 不建本地通知表，不为凑四端硬改）。

## M73 · V3.0 AI 第一批 · 服务端（K5，2026-11-24）

- **节点 / 范围**：K5「③ V3.0」，M 号 **M73**（写前 grep 六仓 PROGRESS `M73` 占用 0）。只改 server 仓；基线 HEAD `43a35e4`/`develop`（K4 M71 仍在工作区未提交）。消费 `09` 会话 R + `10` §十 + `24`，红线以 `22` §七为准。
- **拍板（龙哥 2026-11-24，四问一次定）**：① 额度 = **设额度 + 超限拒绝**（`PARSE_RECORD` 20 次/天、`WEEKLY_REPORT` 2 次/周，数值只落 yml）；② 周报 = **仅按需生成**（不引 xxl-job）；③ 计次 = **家庭 × 功能**；④ **不做付费入口**（V3.0 无支付通道、小程序禁支付引导）。
- **落库**：Flyway `V2.2.20__bl_ai_quota_scope.sql` —— `bl_ai_quota` 加 `scope_key`（`F:<family_id>` 家庭共享 / `U:<user_id>` 无家庭），唯一键 `uk_user_feature` → `uk_scope_feature`，`user_id` 退为「最后操作人 + 审计」。**为什么不直接加 family_id**：无家庭时 `family_id=0` 会让所有单用户撞同一行互相吃额度，且唯一索引对 NULL 各自独立、拦不住并发首建。表仍是服务端运营表：**不进 `SyncTable` 枚举 / 不参与 `/sync/changes`**（不新增第 20 张同步表）。
- **AI 接线（不新造）**：只走 `plugin-ai` 的 `IAiFacade` 契约（biz pom 新增 `babylog-plugin-ai-api`，ArchUnit A1/A2 放行 `com.babylog.ai.api..`），**零新 SDK、零 ChatClient 自装配**；选 `dynamicChat`（无静态兜底）——补录/周报拿不到模型就该明说拿不到，不能用兜底文案冒充 AI。
- **端点**（`BabyLogAiController`，类级 `@SaCheckLogin(app-user)`、裸 token、路径不占 `/sync/*`）：`POST /app/babylog/ai/parse-record`（自然语言 → 草稿，`needConfirm` 恒 true）/ `POST /app/babylog/ai/weekly-report`（按需生成）。**两个都只产草稿、不落库**：19 张同步表一行不写、不进 outbox，正式记录仍由客户端确认后走既有 `/sync/push`（沿用 K4「自动检测永不自动落库」）。
- **计次（范围 A）**：预占 → 失败回滚。占位是**单条条件更新** `SET quota_used = quota_used + 1 WHERE scope_key=? AND feature=? AND quota_used < quota_limit`，影响行数 0 即 `8460`；没有「先 select 再 +1」的裸读改写。窗口 `reset_at` 到期条件归零（幂等），DAILY=次日 00:00 / WEEKLY=下周一 00:00；首建并发由 `(scope_key, feature)` 唯一键兜底（落败方吞 `DuplicateKeyException` 不重试）。未配置的功能 = 不限次只计次（`HANDWRITING_IMPORT` 只留枚举位，V3.1 再做）。
- **降级（范围 C，复用既有码）**：无 provider / 无可用 Key → **`5020`**（AI 模型未配置，不新造码、不改 5020 语义）；配了但调用不通（Key 失效 / 余额 / 限流 / base-url 不可达）→ **`5022`**；模型输出不合 JSON 约束 → `8461`；全部**不抛 5xx、不返空草稿、不输出伪造内容**，且 AI 失败一律回滚额度。
- **周报（范围 D）**：5 张同步表只读聚合（feedings / diapers / sleeps / pump_sessions / measurements，`user_id IN visibleUserIds`），**数字由服务端算、模型只写 3~5 句解读**（模型没有编数据的入口）；睡眠按与窗口重叠分钟计；周报不落库、不发通知。
- **错误码**：新增 `8460 AI_QUOTA_EXCEEDED` / `8461 AI_DRAFT_UNPARSEABLE` / `8462 AI_CHILD_NOT_VISIBLE` / `8463 AI_REPORT_WINDOW_INVALID`（写前复核 8440~8443 K4 已占、8444+ 空闲）。
- **验证**：`mvn -pl babylog-plugins/babylog-plugin-biz test` **335/335**（原 307 + 新增 28：`AiQuotaServiceTest` 12 / `BabyLogAiServiceTest` 10 / `RecordDraftParserTest` 6）；ArchUnit `P0ArchitectureTest` **24/24**；`clean package` fat jar 三件套核过（退出码 0 / mtime 10:35 / 内嵌 biz jar 8 个 `ai` 包 · `V2.2.20` SQL · yml `PARSE_RECORD` · biz-api 常量池含 `AI_QUOTA_EXCEEDED`）。
- **未过 / 移交（如实登记）**：dev 18080 冒烟**未跑**——本机 18080/3306 无监听、无 java 进程、无 mysql 客户端、无登录态；脚本 `/tmp/k5_ai_smoke.sh` 已备（`bash -n` 通过，缺凭据打印 SKIP 且退出码 2，不伪造结果）。补跑条件见 `docs/ai-endpoints-M73.md` §七。K0 四项 / K2 三项 / K3 三项 / K4 三项遗留不随本节点关闭。
- **新登记移交**：①「base-url 不可达」按「配了但打不通」归 `5022`（`09` R 字面写 5020；如要合并改 `AiErrorMapper` 一行）② `weeklyReport` 的 `stats` 目前不含医疗类（用药/疫苗/就医），K6 若要在周报里展示再扩 ③ 额度按家庭计次 ⇒ 家庭新成员加入后与既有个人额度行的合并逻辑已在迁移里处理，但「成员退出家庭」不会把额度拆分回去（只登记）。
- **状态**：待龙哥综合验收；**未 commit / 未 push**（惯例 A）。

