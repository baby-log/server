# K5 / M73 · V3.0 AI 第一批（服务端）端点与配置清单

> 交付说明，唯一口径来源：本文 + `23A` K5 行 + 代码。契约侧依据 `09` 会话 R / `10` §十 / `24`；
> 红线以 `22` §七（尤其 §7.2 #8「全版本免费、唯一收费 = AI 按量」）为准。
> 归档文档一律未改（`23`/`24`/`22`/`15`/`18`/`20`/`21`/`10`/`09` 原文零改动）。

## 一、边界（先声明，避免误读）

1. **AI 能力不是新建，是接线**：模型调用走 `plugin-ai` 的 `IAiFacade` 跨插件契约
   （ArchUnit A1/A2 放行 `com.babylog.ai.api..`），**不引任何新 SDK、不自己装配 ChatClient**。
2. **两个端点都只产草稿、不落库**：19 张同步表一行不写、不进 outbox、不发 `bl_notice`。
   正式记录一律由客户端确认后走既有 `/sync/push`（沿用 K4「自动检测永不自动落库」）。
3. **`bl_ai_quota` 是服务端运营表**：不进 `SyncTable` 枚举、不参与 `/sync/changes`、端点不占 `/sync/*`。
4. **V3.0 不做付费入口**（龙哥 2026-11-24 拍板）：超限只返回 `8460`，不导流购买、不新增收费项。
5. **不碰媒体（K2/K3）与通知（K4）底座**：`bl_media_*`、`bl_notice`、`/app/babylog/media/*`、
   `/app/babylog/notice/*`、8420~8427、8440~8443 一行未改。

## 二、端点清单

### App 端（`/app/babylog/ai`，类级 `@SaCheckLogin(type = StpAppUtil.TYPE)`、裸 token）

| 方法 | 路径 | 入站 | 出站 | 额度 | 说明 |
|---|---|---|---|---|---|
| POST | `/parse-record` | `{childId, text}` | `AiRecordDraftVO` | `PARSE_RECORD` | 自然语言 → 结构化草稿，`needConfirm` 恒 true |
| POST | `/weekly-report` | `{childId, weekStart?}` | `AiWeeklyReportVO` | `WEEKLY_REPORT` | 按需生成；缺省 = 最近 7 天（含今天） |

`childId` 必须是当前账号 `visibleUserIds` 家庭可见范围内的宝宝，否则 `8462`（不区分「不存在」与「无权」，避免枚举探测）。

### `AiRecordDraftVO` 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `recordType` | String | 既有 RecordType 白名单（21 项），越界一律 `8461` |
| `startedAt` / `endedAt` | Long（JSON 字符串） | 业务时刻 epoch millis |
| `amountMl` / `durationMinutes` | Integer | 越界值直接丢弃，不「修正到边界」 |
| `note` | String | ≤200 字，落到记录 note |
| `confidence` | Double | 0~1，模型自评；缺省表示模型没给 |
| `needConfirm` | Boolean | 恒 `true`（草稿语义） |
| `quotaRemaining` | Integer | 本次调用后的剩余次数；`-1` = 不限次 |

### `AiWeeklyReportVO` 字段

`childId` / `weekStart` / `weekEnd`（Long→字符串）、`narrative`（AI 文案）、
`stats`（**服务端只读聚合，未经模型**：喂养次数与毫升、亲喂/辅食次数、尿布次数与尿/便、睡眠段数与总分钟、泵奶次数与毫升、最近体重/身高）、
`readableCategories`（本次账号在该宝宝上可读的同步分类）、`quotaRemaining`。

> 数字与文案的边界是刻意的：模型只拿 `stats` 去写 3~5 句解读，**没有编数据的入口**；
> 即使文案很差，硬数字仍可核对（`09` R「三端 UI 文案不夸大能力」在服务端的落点）。

## 三、额度策略（龙哥 2026-11-24 拍板，四项）

| 项 | 拍板结果 | 落点 |
|---|---|---|
| 额度数值 | **设额度 + 超限拒绝**：`PARSE_RECORD` 20 次/天、`WEEKLY_REPORT` 2 次/周 | `application.yml` → `babylog.ai.quota.features.*`（**代码里没有数字**） |
| 计次口径 | **家庭 × 功能**（家庭共用一份） | `bl_ai_quota.scope_key` = `F:<familyId>` / `U:<userId>`（无家庭） |
| 超限行为 | **拒绝**（`8460`），不发模型请求、不降级伪造内容 | 占位失败即抛，模型一次都不调 |
| 付费入口 | **不做**（V3.0 无支付通道；小程序禁支付引导） | 服务端零相关代码 |

### 计次实现要点（范围 A）

- **预占 → 失败回滚**：额度守在模型调用之前（防「并发 N 个都通过检查、然后都真调了模型」= 直接烧钱）；
  AI 未配置 / 解析失败 / 调用异常一律回滚，最终计到的是**真正消耗掉的调用次数**。
- **原子性**：单条条件更新 `SET quota_used = quota_used + 1 WHERE scope_key=? AND feature=? AND quota_used < quota_limit`，
  影响行数 0 即超限；**没有「先 select 再 +1」的裸读改写**。
- **窗口重置**：`reset_at` 到期才归零（条件更新，天然幂等）；`DAILY`=次日 00:00，`WEEKLY`=下周一 00:00（服务端时区）。
- **首建并发**：`(scope_key, feature)` 唯一键兜底，落败方吞 `DuplicateKeyException` 后走同一条占位语句（不重试、不递归）。
- **未配置的功能** = 不限次只计次（兜底方向选「不挡人」；`HANDWRITING_IMPORT` 本节点只留枚举位）。

### Flyway `V2.2.20__bl_ai_quota_scope.sql`

- 加列 `scope_key VARCHAR(64) NOT NULL`，回填 `F:<family_id>`（有 ACTIVE 家庭成员）/ `U:<user_id>`（其余）。
- **为什么不直接加 `family_id` 改唯一键**：无家庭时 `family_id=0` 会让所有单用户撞同一行、
  互相吃额度；MySQL 唯一索引对 `NULL` 又是「各自独立」，同样拦不住并发首建。⇒ 用带命名空间的一列。
- 同 scope + feature 的重复行（家庭合并后可能撞）：保留 id 最大一条，其余**物理删**
  （该表是计次计数器、无审计价值；逻辑删的行仍占唯一键，不能 `deleted=1`）。
- `user_id` 保留为「最后操作人 + 审计」，退出唯一性判定。

## 四、降级语义（范围 C，复用既有码、不新造）

| 场景 | 返回码 | 语义 | 行为 |
|---|---|---|---|
| 无 provider / 无可用 Key（`5020`/`5022` from ai） | **`5020` AI 模型未配置** | 没配好 | 不发伪造内容、不返空草稿；客户端提示「暂不可用」 |
| 配置在但调用不通（`5003` from ai：Key 失效 / 余额 / 限流 / base-url 不可达） | **`5022` 暂无可用 API Key** | 配了但打不通 | 同上；客户端提示「稍后再试」 |
| 模型输出不满足 JSON 约束 / 记录类型越界 | `8461` | 没听懂 | 提示「换个说法或手动录入」，并回滚额度 |
| 额度用完 | `8460` | 超限 | 提示稍后再试或手动录入，**模型一次不调** |

> 边界口径：`5020` = 「未配置」，`5022` = 「已配置但调用不通」。
> `09` R 字面上的「base-url 不可达 → 5020」按此归入 `5022`（它属于「配了但打不通」），
> 好处是客户端能区分「别试了」与「稍后再试」；若要严格按字面合并成 `5020`，改 `AiErrorMapper` 一行即可。

## 五、周报（范围 D）

- **按需生成**（龙哥拍板）：无定时任务、不依赖 xxl-job ⇒ 本节点**零 xxl-job 改动**，也没有任务清单。
- 输入：5 张同步表只读聚合（`bl_feedings` / `bl_diapers` / `bl_sleeps` / `bl_pump_sessions` / `bl_measurements`），
  `user_id IN visibleUserIds` + 单宝宝 + 时间窗；分类读权限被显式减窄时该分类数字为 0 且不进 `readableCategories`。
- 睡眠段按与窗口的重叠分钟计（进行中的一段按「到当前时刻」截断，同今日汇总口径）。
- 周报**不落库**（不写 `bl_notice`、不写同步表）；需要留存由客户端自己决定（K6）。

## 六、错误码（`8460~8463`，`babylog-plugin-biz-api`）

| 码 | 名称 | 说明 |
|---|---|---|
| 8460 | `AI_QUOTA_EXCEEDED` | 家庭（或个人）本窗口额度已用完 |
| 8461 | `AI_DRAFT_UNPARSEABLE` | 模型输出不满足 JSON 约束 / 记录类型不在白名单 |
| 8462 | `AI_CHILD_NOT_VISIBLE` | 宝宝不存在或不在 `visibleUserIds` 内 |
| 8463 | `AI_REPORT_WINDOW_INVALID` | 周报时间窗非法（未来 / 超过一年前） |

段位：`8420~8427` 媒体（K2）、`8440~8443` 通知（K4）；本批取 `8460~8463`，`8450~8459` 与 `8464+` 留空。

## 七、dev 冒烟（脚本放 `/tmp`，不落仓）

脚本：`/tmp/k5_ai_smoke.sh`（`bash -n` 通过；缺 `TOKEN`/`CHILD_ID` 时打印 SKIP 并退出码 2，不伪造结果）。

```bash
BASE=http://127.0.0.1:18080 TOKEN=<app-user 裸 token> CHILD_ID=<宝宝id> bash /tmp/k5_ai_smoke.sh
# 变体：
#   AI_UNCONFIGURED=1  期望 5020（验证无 Key 降级）
#   SKIP_QUOTA=1       跳过连打超限用例（省额度）
```

用例：① 补录返回草稿且 `needConfirm=true`、`recordType` 在白名单；② 补录后 `/sync/changes` 增量 0 条（草稿不落库）；
③ 周报返回 `stats` + `narrative`；④ 连打 `limit+1` 次，最后一次 `8460`。

**前置条件**（本机当前不满足，属环境依赖，不是代码问题）：MySQL 可连、Flyway 跑到 `V2.2.20`、dev 服起在 18080、
`plugin-ai` 已配 provider + 可用 Key（否则只能验证 5020 用例）。

## 八、本节点未做（越权红线）

- 不做 K6 客户端（口述补录 UI / 周报展示 / 育儿知识库问答 UI）；不碰 K7 隐私 md、K8 云同步开关；
- 不预先定 V3.1 手写拍照导入细节（只留 `HANDWRITING_IMPORT` 枚举位）；
- 不动媒体 / 通知底座语义与表结构；不改 19 张同步表语义、不新增第 20 张同步表；
- 不引统计 / 崩溃 / 推送 SDK、不启 plugin-message、权限清单与签名未动；
- 不改任何归档文档原文（`23`/`24`/`22`/`15`/`18`/`20`/`21`/`10`/`09`），只改 `23A` K5 行 + 本仓 PROGRESS/CHANGELOG；
- 未 commit / 未 push。
