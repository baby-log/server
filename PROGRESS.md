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

## M76 · 育儿知识库 · 服务端 KB/RAG 底座（K6-S，2026-11-25，工作区未提交）

- **节点 / 范围**：K6-S「③ V3.0」（S=Server，与 K6-A/B/C 客户端并列、**不占 `23` §四 K0~K12 总表编号**、与 `09` 号 S1~S4 无关），M 号 **M76**（写前 grep 六仓 PROGRESS `M76` 占用 0、全局上限 M75）。只改 server + ui 两仓；基线 HEAD server `06c3cbc` / ui `7fb174e`（本会话实测），其余四仓零改动。
- **第一道门禁（`09` R）**：龙哥 2026-11-25 拍板 `KB_QA = 10 次/天 DAILY`；随后选 **「C 授权落点微调」** —— 本节点把 KB_QA 额度改为参数中心 `sys_config` 可配（回落 yml），V3 购买引导 / 虚拟支付 / 线下购买 / 个人主体支付能力**移交主会话**，节点不改归档事实、不做付费入口。
- **上游复用（不重造 RAG）**：`babylog-plugin-ai-kb` / `ai-kb-api` 已有完整 simple / BM25 / HybridFusion / RRF / rerank / 溯源骨架，`07` 号拍板不部署 Milvus ⇒ 本节点**只走 `IKnowledgeBaseFacade` 契约 + `IAiFacade.dynamicChat`**，不重造解析 / 分块 / 检索 / 重排；biz 新增依赖仅 `babylog-plugin-ai-kb-api`（契约 jar，dependencyManagement 已仲裁），零新 SDK / 零新中间件 / 不启 `plugin-message`。
- **落库**：Flyway `V2.2.21__bl_kb_faq.sql` 建 `bl_kb_faq`（条目化 FAQ，`source` 必填、正文应用层限 2000 字）与 `bl_kb_favorite`（纯关联表、不带 `deleted`、取消收藏物理删、`uk_user_faq` 兜底并发），并预置 `sys_config` 的 `babylog.ai.quota.kb_qa.limit = 10`（`id=5`、`INSERT IGNORE`、`config_type=N`）。两表**服务端专用**：不进 `SyncTable`、不参与 `/sync/changes`、不进 outbox、端点不占 /sync/ 开头的同步端点、不发 `bl_notice`；19 张同步表零改动。
- **端点**：App `/app/babylog/kb`（类级 `@SaCheckLogin(app-user)`、裸 token）7 个——`GET /categories`、`GET /faqs`、`GET /faqs/{faqId}`、`GET /favorites`、`POST /favorites`、`DELETE /favorites/{faqId}`（全免费）+ `POST /ask`（计 `KB_QA`）；管理端 `/babylog/kb/faq`（类级 `@SaCheckLogin(sys)`）3 个——`GET /list`、`POST /save`、`DELETE /{id}`。字段、全局契约（Long 字符串 / 整数 1/0 / null 键缺省）、降级表见 `babylog-plugin-biz/docs/kb-endpoints-M76.md`。
- **三条红线（`10` §4.1）**：① `source` 必填（`8465`）+ RAG 引用透传 `fileName/docId/chunkId`；② `KbDisclaimer.TEXT` 固定免责「仅供参考，不构成医疗建议，异常请及时就医」在每个问答响应（含降级）恒下发；③ 检索为空 / 模型返回空 ⇒ **一次模型都不调**、不占额度、返回空答案 + 空引用 + `degraded=1`，正常路径 `citations` 恒非空。禁用词清单只写进代码注释，0 处用于 UI / 接口 / prompt 文案。
- **额度（门禁 C）**：`AiFeature.KB_QA` 三处逐字一致（枚举 / yml / 落库）；`AiQuotaService` 新增**三参** `reserve(..., limitOverride)`，两参重载恒传 `null` ⇒ K5 行为与既有单测零变更；覆盖时同步已存在行的 `quota_limit`（后台改额度对存量用户即时生效），首建行也写覆盖值。`AiQuotaConfigReader` 用 `JdbcTemplate` 单表只读 `sys_config`（`deleted = 0`），实时查库无缓存，避免跨插件 import system 实现层踩 ArchUnit C1；缺失 / 非数字 / 负数回落 yml，`0` = 不限次。
- **FAQ 与 RAG 的关系（最易误读）**：FAQ 表负责浏览 / 分类 / 关键词搜索 / 收藏（列表过滤，**不是向量检索**）；RAG 问答引用来自**上游 ai-kb 知识库分块**，上游文档灌入走既有 `/ai/kb/doc`。当前 `kb-store.json` 为空 ⇒ 问答在灌入前返回「无可引用资料」降级响应，这是红线三的正确行为、不是缺陷；灌入属环境依赖，登记遗留。
- **验证**：biz 单测、ArchUnit `P0ArchitectureTest`、`clean package` fat jar 内嵌三件套、ui `pnpm typecheck` + `pnpm build` 的实测数字见本节点四问报告；dev 18080 冒烟**未跑**（本机 18080/3306 无 LISTEN、无 dev java / mysqld、无登录态、Flyway 未到 V2.2.21、向量库为空、AI Key 未配），补跑用例见 `docs/kb-endpoints-M76.md` §九，如实登记、不伪造、不阻塞。
- **新登记移交**：① 上游知识库文档灌入（走 `/ai/kb/doc`，本节点不改上游）② 管理端菜单行 / 方法级 `@SaCheckPermission` / `AdminControllerPermissionContractTest` 三处配套（K3 先例；本节点不新增权限字符）③ `sys_config` 直查在微服务拆库后须补 `IConfigFacade` 契约（架构欠账）④ 个人主体支付能力 / V3 购买引导 / 虚拟支付 / 线下购买**移交主会话**，含「个人主体不能支付」过时事实的订正建议 ⑤ K5「数值只落 yml」口径按本段文末追加登记（KB_QA 例外：参数中心可配），旧语义与行为不改 ⑥ K0/K2/K3/K4/K5/K6-A/K6-B 既有遗留不随本节点关闭。
- **状态**：待龙哥综合验收；**未 commit / 未 push**（惯例 A）；不填 `23A` 验收结论列、不自行判定验收、不生成 K6-C / K7 提示词。


### K7 / M78 · 隐私政策改版（server 零改动 · 2026-11-25）

- 本节点未改 server 任何文件：隐私政策单一源与生成链在 `BabyLog-Android/docs/privacy/`，服务端不含政策文案载体。
- 本批 fat jar **不参与出包**（无政策载体；且 fat jar 内嵌 prod yml，禁传公开 Release）。
- 六端产物与出包留痕见 `发布包/K7-M78-隐私政策改版/`（`BUILD-LOG.md` 含逐端退出码 / 时间戳 / SHA-256）。
- 未跑：dev 18080 冒烟（18080 / 3306 `nc` CLOSED、无 dev 服、无登录态），按 K2~K6 先例如实登记、不阻塞验收。
- **未 commit / 未 push**；不自行验收、不生成 K8 提示词。

### K8 / M79 · 画面云同步开关解锁（server 门禁对齐 · 2026-11-25）

- **结论**：K8 服务端**零业务端点改动**。「已开云同步」唯一等价判据 = 有效 app-user token（`BabyLogMediaController` 类级 `@SaCheckLogin(type = StpAppUtil.TYPE)`、`currentUserId()` 从 `LoginContext.getUserId()` 取），不新增第二套状态、不改 19 张同步表语义、不碰 `/sync/` 同步端点。
- **交付**：新增 `MediaCloudSyncGateTest.java`（4 例：类级 app-user 登录注解 / `MediaUploadCmd` 无 `userId` 字段 / `bl_media_file`·`bl_media_quota` 不在 `SyncTable.values()`（仍 19 项）/ 7 个端点路径逐字对齐 M69）与 `docs/media-cloud-sync-gate-K8-M79.md`；`BabyLogMediaController` `@Operation` summary、`MediaFileService` / `MediaUploadCmd` 类注释从「V3 前零画面数据」订正为 K8 解锁后静态画面语义，业务逻辑与签名零改动。
- **沿用 K2**：端点、错误码 8420~8427、video 类 8423 拒收、两步删、保留/淘汰策略（`24` ③/④）全部不改；两张媒体表仍是服务端专用表、不参与同步通道。
- **验证**：Task #2 留痕 biz 全量 **355/355**、ArchUnit **24/24** BUILD SUCCESS；dev 18080 / 3306 未起，冒烟未跑（按 K2~K7 先例如实登记、不阻塞验收）。
- **边界**：fat jar 不参与出包（内嵌 prod yml 禁传公开 Release）；不做付费入口、不读/不改 `sys_config`、不生成 K9。
- **状态**：待龙哥综合验收；**未 commit / 未 push**；零对外动作（1Panel 上传 / 4G 自测 / 商店三处字段 / 真机安装 / push 全归龙哥）；不填 `23A` 验收结论列、不自行判定验收。

### K10 / M81 · 设备绑定 + 按钮进行中行闭环（server 契约锁 · 2026-11-26）

- **开工门禁拍板（龙哥 2026-11-26）**：① 端别范围 = **零业务代码·全端对齐**（server 零业务改动，仅契约对齐测试 + 文档；四端各补单测锁）② 验证形态 = **仅契约 + 单测锁**，不做 UI 载体 ③ M 号申报 **M81**（写前六仓 `PROGRESS.md` grep `M81` 复核 0 命中）④ `26` #4 新规确认（验收通过后 commit+push）⑤ 放行总纲「**全部确认·按推荐开工**」（边界 A / 26 六条 / 27 四条 / 门禁甲 / 两处口径差按最新拍板）。
- **关键发现（本节点定位依据）**：`24` §八 的设备绑定 + 按钮进行中行闭环能力**几乎全部已存在于 V2 既有代码且已验收**——`BabyLogDeviceBindController` 四端点 / `bl_device` / 8400~8403（M45）、`SyncTable` 19 项含 `FEEDINGS`、M53 的 `in_progress=1` 下发 + 同 ID 收尾软删、计时五列齐备。⇒ 按「零新增端点/表/码/通道、不碰 19 张同步表语义」红线，K10 实质是「闭环验证 + 契约锁死」节点，不写新功能。
- **交付（本仓业务源码零改动）**：新增 `babylog-plugin-biz/src/test/java/com/babylog/biz/contract/K10ContractLockTest.java`（6 例，纯反射 / 查表、不启 Spring 上下文）锁：device-bind 四端点路径与 apply/poll 匿名·confirm/deny app-user 登录策略、8400~8403 恰四码且无 8404+、`SyncTable.values()` 恰 19 项且不含 device/event/button 表、`syncSpecs()`=19 / `baseSpecs()`=4 / `BaseDataTable.values()`=4、FEEDINGS 无 `in_progress=0` 拉取谓词（M53）且 `rejectNullProperties` 恰 `inProgress`、计时五列类型 Integer/String/Long/Integer/Integer。
- **验证（真跑）**：`-pl :babylog-plugin-biz test` **361/361**（基线 355 + 新增 6）、0 failures / 0 errors / 0 skipped、BUILD SUCCESS；`-pl :babylog-admin-server -am -Dtest=P0ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test` **24/24** BUILD SUCCESS。dev 18080 全链跨端冒烟未跑（环境依赖，统一移到 V3 发布收口补跑，按 K2~K9 先例如实登记、不阻塞验收）。实跑 Java 25（语法均 17 兼容，未受影响）。
- **边界**：零业务端点改动、零新增端点 / 表 / 错误码 / 设备事件流通道、不改 19 张同步表语义、不做固件（K11）、不改 `23` 号正文及其它归档原文；fat jar 不参与出包。
- **状态**：待龙哥综合验收；**未 commit / 未 push**（按 `26` #4 须龙哥明确「综合验收通过」后才执行）；不填 `23A` 验收两列、不自行判定验收、不生成 K11 及以后任何节点提示词；`28` §九 九项遗留照单携带、不随本节点关闭。
