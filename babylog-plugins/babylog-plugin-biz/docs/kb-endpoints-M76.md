# K6-S / M76 · 育儿知识库（服务端 KB/RAG 底座）端点与配置清单

> 交付说明，唯一口径来源：本文 + `23A` K6-S 行 + 代码。
> 契约侧依据 `10` §4.1 三条红线 / `09` 会话 R / `22` §七五条拍板 / `24` §五全局契约坑 /
> `07` 文末十四节（simple 模式）/ `23` §2.1~2.3 节点制。
> 归档文档一律未改（`23`/`24`/`22`/`10`/`09`/`07` 原文零改动）。

## 一、边界（先声明，避免误读）

1. **RAG 不是新建，是接线**：检索只走 `IKnowledgeBaseFacade`（`babylog-plugin-ai-kb-api` 契约，
   3 个只读方法 `listOptions/search/kbStats`），生成只走 `IAiFacade.dynamicChat`。
   不重造解析 / 分块 / 检索 / 重排、不依赖 ai-kb 实现层、不部署 Milvus、
   不引新 SDK / 新中间件、不启 `plugin-message`。simple 模式由上游装配（`07` 号拍板），
   `data/kb/simple/kb-store.json` 当前为 `{}`（无分块）。
   biz 新增依赖仅 `babylog-plugin-ai-kb-api`（契约 jar，dependencyManagement 已仲裁）。
2. **两张新表都是服务端专用表**：`bl_kb_faq` / `bl_kb_favorite` 不进 `SyncTable` 枚举、
   不参与 `/sync/changes`、不进 outbox、端点不占 /sync/ 开头的同步端点、不发 `bl_notice`。
   **19 张同步表一行未改**，同步语义零触碰。
3. **FAQ 与 RAG 是两条互补链路（本节点最易误读的一点）**：
   - `bl_kb_faq` = BabyLog 自建的条目化权威资料摘录，负责 **浏览 / 分类 / 关键词搜索 / 收藏**（全部免费）；
     这里的「搜索」是列表的标题/正文关键词过滤，不是向量检索。
   - RAG 问答的**引用条目来自上游 ai-kb 知识库分块**（`IKnowledgeBaseFacade.search()` 返回的
     `KbSearchResultDTO`），溯源靠 `fileName` / `docId` / `chunkId` 三件套；
     上游文档灌入走既有 `/ai/kb/doc` 管理端点，本节点不改上游、不重造灌入链路。
   - 当前 `kb-store.json` 为空 ⇒ 问答在灌入前返回「无可引用资料」的降级响应，
     **这正是第三条红线「不允许裸生成」的正确行为，不是缺陷**。灌入文档属环境依赖，登记遗留。
4. **全版本免费、唯一收费 = AI 按量**（`22` §七保持）：浏览 / 分类 / 搜索 / 收藏全免费；
   只有 `POST /ask` 计 `KB_QA`。**V3 购买引导 / 虚拟支付 / 线下购买 / 个人主体支付能力
   移交主会话**（龙哥 2026-11-25 门禁 C「授权落点微调」），本节点零相关代码、不改归档事实。
5. **额度后台可配（门禁 C）**：`KB_QA` 回落基线 `10 次/天 DAILY` 在 `application.yml`；
   运营在「系统工具 - 参数设置」改 `babylog.ai.quota.kb_qa.limit`（`sys_config`）后
   下一次问答即时生效、无需重启。K5 的 `PARSE_RECORD` / `WEEKLY_REPORT` 仍是
   「改 yml + 重启」，**旧口径按文末追加登记、语义不改、行为零变更**。

## 二、端点清单

### App 端（`/app/babylog/kb`，类级 `@SaCheckLogin(type = StpAppUtil.TYPE)`、裸 `Authorization` token）

| 方法 | 路径 | 入站 | 出站 | 额度 | 说明 |
|---|---|---|---|---|---|
| GET | `/categories` | — | `List<KbCategoryVO>` | 免费 | 分类码 + 该分类已上架条目数 |
| GET | `/faqs` | `category?`, `keyword?` | `List<KbFaqVO>` | 免费 | 只看 `enabled=1`；排序 `sortOrder → id`（Java 内存排，不用 `.last()`） |
| GET | `/faqs/{faqId}` | path | `KbFaqVO` | 免费 | 不存在 / 已下架 → `8464` |
| GET | `/favorites` | — | `List<KbFaqVO>` | 免费 | 只返回仍上架的收藏条目 |
| POST | `/favorites` | `{faqId}` | `R<Void>` | 免费 | 幂等（唯一键兜底，吞 `DuplicateKeyException`） |
| DELETE | `/favorites/{faqId}` | path | `R<Void>` | 免费 | 物理 DELETE，幂等 |
| POST | `/ask` | `{question, kbId?, topK?}` | `KbAnswerVO` | **`KB_QA`** | RAG 问答；答案必带引用条目，无可引用资料不裸生成 |

`userId` 恒由服务端从 token 取（`LoginContext.getUserId()`），**客户端绝不发 userId**。
类级鉴权同时满足 P0 ArchUnit A11 的鉴权信号，且不新增 `@SaCheckPermission` 权限字符。

### 管理端（`/babylog/kb/faq`，类级 `@SaCheckLogin(type = StpSysUtil.TYPE)`，项目惯例无 `/admin` 前缀）

| 方法 | 路径 | 入站 | 出站 | 说明 |
|---|---|---|---|---|
| GET | `/list` | `category?`, `keyword?` | `List<KbFaqVO>` | 含下架条目（与 app 端只看上架不同） |
| POST | `/save` | `KbFaqSaveCmd` | `KbFaqVO` | 来源必填（`8465`）；正文限 2000 字（`1001`）禁整篇搬运 |
| DELETE | `/{id}` | path | `R<Void>` | 逻辑删条目 + 物理清收藏关联 |

ui 管理端页面：`views/babylog/kbFaq/index.vue` + `api/babylog/kb.ts`。
动态路由按 `permission.ts` 的 `import.meta.glob` 装配，**菜单 component 串必须逐字为
`babylog/kbFaq/index`**；本节点不改前端路由表，菜单行由龙哥/后续服务端节点补登记。

## 三、字段与全局契约

| VO / Cmd | 字段 | 说明 |
|---|---|---|
| `KbFaqVO` | `id`(Long→字符串)、`category`、`title`、`content`、`source`、`sourceUrl`、`enabled`、`sortOrder`、`favorited` | 字段全可选：NON_NULL 契约下 null 键缺省，四端容忍缺键 |
| `KbCategoryVO` | `category`、`count` | 分类码升序 |
| `KbAskCmd` | `question`(1~200 字)、`kbId?`(Long→字符串)、`topK?`(1~10，缺省 5) | 超长 / 空问题 → `1001` |
| `KbCitationVO` | `index`、`fileName`、`docId`、`chunkId`、`kbId`、`score`、`excerpt` | 溯源三件套原样透传上游 `KbSearchResultDTO`；`excerpt` 截 300 字防超长 prompt |
| `KbAnswerVO` | `answer`、`citations`、`disclaimer`、`quotaRemaining`、`degraded` | `answer` 非空时 `citations` 必非空；降级时 `answer=null`（JSON 缺键）+ 空 citations + `degraded=1` |
| `KbFaqSaveCmd` | `id?`、`category`、`title`、`content`、`source`、`sourceUrl?`、`enabled`、`sortOrder` | `source` 空 → `8465` |

**全局契约坑逐条守**：

- JSON 里所有 Long 都是字符串（实际引擎是 fastjson2 `JSONWriter.Feature.WriteLongAsString`）；
- TINYINT 布尔 / 状态字段 `enabled`、`favorited`、`degraded` 一律**整数 1/0**，
  绝不发 `true/false`（客户端读走 `JsonExt.boolOrNull`）；
- null 键缺省，四端容忍缺键 —— `KbAnswerVO.answer` 降级时缺键是设计内；
- 服务端加 NOT NULL 列需登记 `SyncSpec.preserveOnAbsentProperties` 一事**本节点不触发**：
  两张新表都不进同步表，四端无对应表、无 push 载荷。

## 四、三条红线（`10` §4.1）落点

| 红线 | 服务端落点 | 验证 |
|---|---|---|
| ① 内容来源必须可追溯 | `bl_kb_faq.source` 非空约束（`8465`）；RAG 引用保留 `fileName/docId/chunkId` | `AdminKbFaqServiceTest#sourceIsMandatory`、`KbCitationAssemblerTest#keepsTraceFieldsAndNumbersContinuously` |
| ② 全程「非医疗建议」免责 | `KbDisclaimer.TEXT = "仅供参考，不构成医疗建议，异常请及时就医"`；**每个问答响应（含降级）恒下发**，ui FAQ 页顶部常驻同一句 | `BabyLogKbQaServiceTest` 全部用例断言 disclaimer |
| ③ AI 答案必须带引用条目、不许裸生成 | 检索结果为空 ⇒ **一次模型都不调**、不占额度、返回空答案 + 空引用 + `degraded=1`；模型返回空文本 ⇒ 回滚额度后同样降级；正常路径 `citations` 恒非空 | `BabyLogKbQaServiceTest#noCitationsMeansNoModelCallAndNoQuotaReserve` / `#blankModelAnswerRollsBackQuotaAndDegrades` / `#answerAlwaysCarriesCitations...` |

**文案红线**：允许「育儿知识库」「仅供参考」「不构成医疗建议，异常请及时就医」「AI 帮你解读，请核对」；
禁用词清单**只写在 `KbDisclaimer` 的注释里**，0 处用于 UI / 接口 / prompt 文案；
系统提示词明确要求模型「不做疾病诊断、不给用药建议、不吓唬家长」「不编造数据」「资料不足就说现有资料不足」。
免责文案是合规物料，刻意不抽成后台配置项。

## 五、额度策略（龙哥 2026-11-25 拍板 + 门禁 C）

| 项 | 拍板结果 | 落点 |
|---|---|---|
| 功能码 | `AiFeature.KB_QA`（枚举名 = yml 键 = `bl_ai_quota.feature` 落库值，三处逐字一致） | `AiFeature.java` / `application.yml` / 代码 |
| 额度数值 | **10 次/天 DAILY**（`KB_QA`）；`PARSE_RECORD` 20/天、`WEEKLY_REPORT` 2/周 **不变** | `application.yml` → `babylog.ai.quota.features.KB_QA` |
| 后台可配 | **是**（门禁 C）：`sys_config.babylog.ai.quota.kb_qa.limit`，实时读库、无需重启 | Flyway `V2.2.21` 预置 `id=5` 行（`config_type=N`）；`AiQuotaConfigReader` 单表只读查 `sys_config`（`deleted = 0`） |
| 计次口径 | **家庭 × 功能**，沿用 K5 `scope_key = F:<familyId>` / `U:<userId>` | `AiQuotaService`（未改 scope 逻辑） |
| 超限行为 | **拒绝**（`8460`），不发模型请求、不降级伪造内容 | 占位在模型调用之前，失败即抛 |
| 窗口 | `DAILY` = 次日 00:00 归零（服务端时区）；参数中心只改 limit，不改 window | `AiQuotaService.nextResetAt` |
| 付费入口 | **不做**（本节点）；V3 购买引导 / 虚拟支付 / 线下购买**移交主会话** | 服务端零相关代码 |

### 实现要点（对 K5 零侵入）

- `AiQuotaService` 新增**三参** `reserve(userId, feature, limitOverride)`；
  两参重载恒传 `null` ⇒ K5 行为与既有 14 个 `AiQuotaServiceTest` 用例**逐字等价、零改动**。
- `limitOverride != null` 时：① 用覆盖值替代 yml 值；② 在占位前把**已存在额度行**的
  `quota_limit` 条件更新成新值（否则运营后台改额度对存量行不生效）；③ 首建行也写覆盖值。
- `BabyLogKbQaService.resolveLimitOverride()`：参数缺失 / 非数字 / 负数 → 返回 `null` 回落 yml；
  `0` 是合法值（不限次、只计次）。方向仍是「不挡人」。
- **架构口径**：`sys_config` 是 system 插件的表，但 `ConfigService` 位于 system **实现层**，
  biz 直接 import 会踩 ArchUnit A1/A2 与 C1「跨插件只走契约」；全仓也没有 `IConfigFacade`。
  故在 biz 内用 Spring 自带 `JdbcTemplate` 做**单表只读**查询（同 `AdminConsoleService` 先例），
  不 import 任何 system 包、不建 `@TableName("sys_config")` 实体、不写 Mapper XML、SQL 用 `?` 占位。
  **架构欠账**：微服务化拆库后此直查会失效，届时按 Facade 体例补 `IConfigFacade` 替换。
  当前单体 fat jar，零新 SDK / 零新中间件。

## 六、降级语义（复用既有码，不新造）

| 场景 | 返回 | 行为 |
|---|---|---|
| 检索结果为空 / 全是无内容片段 | `200` + `answer` 缺键 + 空 `citations` + `degraded=1` + 固定免责 | **不调模型、不占额度、不裸生成** |
| 模型返回空文本 | 同上 | 回滚本次预占额度 |
| 无 provider / 无可用 Key（`5020`/`5022` from ai） | **`5020`** AI 模型未配置 | 回滚额度；客户端提示「暂不可用」 |
| 配置在但调用不通（`5003` from ai） | **`5022`** 暂无可用 API Key | 回滚额度；客户端提示「稍后再试」 |
| 额度用完 | **`8460`** | 占位失败即抛，**模型一次不调**、不回滚（未预占成功） |
| 参数非法（空/超长问题、超长正文、空来源） | `1001` / `8465` | 检索与模型都不调 |

> 口径沿用 K5 `AiErrorMapper`：`5020` = 未配置，`5022` = 已配置但打不通。
> `09` R 字面「base-url 不可达 → 5020」的既有口径差仍按 M73 §四登记，本节点不改。

## 七、错误码（`babylog-plugin-biz-api`）

| 码 | 名称 | 说明 |
|---|---|---|
| 8464 | `KB_FAQ_NOT_FOUND` | 知识条目不存在 / 已下架 / 无权访问（不区分，避免枚举探测） |
| 8465 | `KB_FAQ_SOURCE_REQUIRED` | 管理端录入未填资料来源（红线一） |

复用既有码：`1001 PARAM_INVALID`、`8460 AI_QUOTA_EXCEEDED`、
`5020 AI_NOT_CONFIGURED`、`5022 NO_AVAILABLE_KEY`。
段位：`8460~8463` 是 K5；本批取 `8464~8465`，`8466~8469` 留空，`8470` 起留给 V3.1 手写导入。

## 八、Flyway `V2.2.21__bl_kb_faq.sql`

- `bl_kb_faq`：`category/title/content/source/source_url/enabled/sort_order` + `BaseDO` 审计列；
  `source` `NOT NULL DEFAULT ''` 只兼容迁移，业务层强制非空（`8465`）；
  `content` 用 `TEXT`，2000 字上限由应用层卡，避免迁移期数据截断。
- `bl_kb_favorite`：**纯关联表，不带 `deleted` 列** ⇒ 取消收藏是物理 DELETE，
  不会被「逻辑删行仍占唯一键」卡住（同 V2.2.20 对计次表的判断）；
  `uk_user_faq(user_id, faq_id)` 兜底并发重复收藏，服务端吞 `DuplicateKeyException` 幂等返回。
- `sys_config` 预置 `id=5`（V1.0.1 用 1/2、V2.2.1 用 3、V2.2.2 用 4）、
  `config_key=babylog.ai.quota.kb_qa.limit`、`config_value='10'`、`config_type='N'`，
  `INSERT IGNORE` + `uk_config_key` 保证重复执行 / 已有环境不炸。
- 迁移里**无任何跨库 JOIN**（C4 扫描口径），只有 bl_ / sys_config 单表 DDL / DML。

## 九、dev 冒烟（脚本放 `/tmp`，不落仓）

本机当前不满足前置条件，**本节点如实登记为「未跑」**（沿用 K2~K6-B 先例，不伪造结果、不阻塞验收）：

- 18080 / 3306 无 LISTEN、`nc` CLOSED、无 dev java / mysqld 进程，无 app-user 登录态；
- Flyway 未跑到 `V2.2.21`、`sys_config` 参数行未灌入；上游 `kb-store.json` 为空 `{}`；
- `plugin-ai` 未配 provider + 可用 Key（只能验证 `5020` 降级分支）。

补跑用例（龙哥在有库环境执行）：

1. `GET /app/babylog/kb/categories` + `/faqs` → 免费返回、`id` 是字符串、`enabled/favorited` 是 1/0；
2. `POST /favorites` 连打两次 → 幂等；`DELETE` 后再收藏成功（验证物理删不撞唯一键）；
3. `POST /ask` 在空向量库下 → `degraded=1`、空 `citations`、`answer` 缺键、免责在，**模型零调用**；
4. 灌入一篇上游文档后 `POST /ask` → `answer` 非空且 `citations` 非空、溯源三件套齐；
5. 后台改 `babylog.ai.quota.kb_qa.limit` 为 `1` → 连打 2 次，第 2 次 `8460`；改回 `10` 后存量行即时恢复；
6. 无 Key 时 `POST /ask` → `5020`；配置在但打不通 → `5022`；
7. `/sync/changes` 增量 0 条（证明两张新表零同步表写入）；`SyncTable` 枚举 19 项不变。

## 十、本节点未做（越权红线）

- 不做 K6-C 客户端（Android/iOS/Mini 浏览 / 搜索 / 收藏 / 问答 UI），不生成 K6-C / K7 提示词；
- 不改上游 `babylog-plugin-ai-kb` 实现、不灌入文档、不部署 Milvus、不引新 SDK / 新中间件、不启 `plugin-message`；
- 不做 V3 购买引导 / 虚拟支付 / 线下购买 / 个人主体支付能力（**移交主会话**），不改归档里「个人主体不能支付」的过时事实（订正建议移交主会话）；
- 不改 19 张同步表语义、不新增第 20 张同步表、不进 outbox / `/sync/*` / `bl_notice`；
- 不改 K5 的 `PARSE_RECORD` / `WEEKLY_REPORT` 额度行为与 yml 口径，仅在文末追加登记 KB_QA 的后台可配差异；
- 不新增 `@SaCheckPermission` 权限字符（菜单 SQL / 方法级注解 / `AdminControllerPermissionContractTest`
  三处配套登记到「遗留与移交」，与 K3 配额页先例一致）；
- 不改任何归档文档原文；只改 `23A` K6-S 行 + server / ui 两仓 `PROGRESS.md` / `CHANGELOG.md`；
- **不填 `23A` 验收结论列、不自行判定验收、不 commit / 不 push**；四问报告后即停等龙哥综合验收。
