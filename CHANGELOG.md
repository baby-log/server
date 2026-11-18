# 更新日志

本文件记录 BabyLog 服务端的版本变更，格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。
服务端不单独发版号，变更按全局批次 M 序列与部署批次记录；逐批次实测证据见
[19-V2收口执行档案](../独立产品规划/19-V2收口执行档案-2026-11-18.md) 与本仓
[biz README](babylog-plugins/babylog-plugin-biz/README.md)。

## [Unreleased] — V2 收官（2026-11-19）

> 尚未对外公开发布。按发布节奏，V3 完成前不做任何公开处理；正式服 M32 已部署，测试服/dev 服按批次起服验证。

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

## 未解冻 / 候选（非 V2 缺口）

- 按宝宝粒度权限裁决、refresh 轮换（M20+ 候选）
- 7xxx 错误码重叠、starter-auth `DemoAcceptanceTest` 配置债（框架遗留，不动）
- V3 公开处理（部署解冻与内测包压到 V3 完成）
