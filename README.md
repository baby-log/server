# BabyLog 服务端（babylog-server）

> **BabyLog 育儿记录**的服务端与后台管理系统（Maven 多模块单仓），开发者：[胡伟龙（Alex）](https://www.293242.com/)
>
> 本项目 fork 自开源脚手架 [pivotos-framework（筱筱框架）](https://gitee.com/pivotos/pivotos-framework)（Apache-2.0）并全量改名为 babylog，架构规范与开发约定沿用原脚手架，在此致谢。

BabyLog 服务端是一个**插件化 + AI 原生底座 + 单代码库双部署形态（单体/微服务）**的后端：同一套后端支撑 Android / iOS 原生 App、微信小程序、Android TV 与 PC 运营管理端。

## 当前状态（V2 固定版 V2.4.0，2026-11-19）

| 项 | 结果 |
|---|---|
| 版本 | **产品版本 `V2.4.0`**；Maven 坐标 `3.2.0` 是 PivotOS 框架版本（双轨保留，不改坐标，改了会破坏依赖树） |
| 业务范围 | 认证（app-user / wx-mini-user 双 token）、云同步（双段游标 + 逐条 version 乐观锁）、家庭七端点与权限矩阵、设备管理、快速记录、内置产品 base 通道、18 号契约两列增强（`bl_baths.ended_at` / `bl_sleeps.transitions`） |
| 测试 | `mvn -o test` **56/56 模块 BUILD SUCCESS**（0 failures / 0 errors，1:27）；biz **231/231**；ArchUnit **24 个 testcase 全绿**（A1~A12 / C1~C4） |
| 规范 | PivotOS 硬约束全量生效：禁 `${}` 拼接（A5/A12）、禁 `.last()`（A10 白名单已清空）、SQL 一律 `LambdaQueryWrapper`、DDL/DML 一律 Flyway、雪花 ID 禁自增 |
| 部署 | M32 正式服 `https://baby-api.293242.com` 已部署 M4~M23 全量、正式库清空重灌、验收冒烟 19/19；测试域名 `https://baby-api-test.293242.com` |
| 仓库 | `develop` 分支；gitee（`origin`）+ GitHub 双推；V2 收官 HEAD `8db5d7d` |
| 未解冻 | V3 完成前不做任何公开处理；按宝宝粒度权限裁决、refresh 轮换未做（M20+ 候选，非 V2 缺口） |

> 本仓不设 `PROGRESS.md` / `ACCEPTANCE.md`：服务端的逐批次进度与验收证据统一在
> [19-V2收口执行档案](../独立产品规划/19-V2收口执行档案-2026-11-18.md)（§六 24 条台账 / §七 实测）
> 与本文件下方的「M52 收口」节；不补写历史台账，避免伪造。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 语言 / 运行时 | Java 25（JDK 25，兼容矩阵含 21） |
| 核心框架 | Spring Boot 4.x · Spring Framework 7 |
| ORM | MyBatis-Plus + dynamic-datasource（多数据源/多租户） |
| 认证鉴权 | Sa-Token 多账号体系（sys-user / app-user / wx-mini-user / mind-user） |
| 数据库迁移 | Flyway（脚本随插件 jar 分发，启动自动执行，自动建库建表） |
| AI 能力 | Spring AI 2.0（OpenAI 兼容模式，多供应商动态装配） |
| 向量存储 | Milvus（主） / SimpleVectorStore（零中间件兜底），可插拔 |
| 工作流 | WarmFlow 1.8.x（内置设计器 UI） |
| 任务调度 | XXL-Job 3.x |
| 缓存 / 分布式锁 | Redis + Redisson + Lock4j |
| Excel | EasyExcel 4.x 流式（禁 POI DOM） |
| 序列化 | fastjson2（统一脱敏） |
| 架构守护 | ArchUnit（CI 每次必跑，**A1~A12 / C1~C4 共 24 个 testcase**） |

## 模块结构

根 `pom.xml` 聚合 6 个一级模块，全仓 57 个 `pom.xml`（含各 starter / plugin 的 `-api` 契约包）：

```
babylog-server (groupId=com.babylog)
├── babylog-dependencies        # BOM：全部第三方版本集中声明
├── babylog-commons             # 契约层（禁依赖 Spring，ArchUnit 守护）
│   ├── babylog-common-core     #   R<T> 统一响应 / ServiceException / 分页 / 枚举 / 上下文门面
│   └── babylog-common-api      #   通用 API 契约
├── babylog-starters            # 10 个技术 Starter（条件装配，可插拔）
│   ├── starter-core / -web / -redis / -mybatis / -auth
│   └── starter-tenant / -excel / -job / -ai / -datascope
├── babylog-plugins             # 业务插件（除 monitor 外均拆 -api 契约包 + 实现包）
│   ├── babylog-plugin-biz(-api)   # ★ BabyLog 主业务插件（见下节）
│   ├── plugin-system / -message / -file / -workflow
│   ├── plugin-ai / -ai-coding / -ai-kb / -mind
│   ├── plugin-generator / -docsync / -migration / -monitor
├── babylog-admin-server        # 单体启动器（monolith 形态）
└── babylog-gateway-server      # 微服务形态网关
```

**契约先行**：跨插件调用只允许经 `-api` 包的 Facade 接口；表前缀按域隔离（`sys_/msg_/flow_/gen_/ai_/kb_/bl_`），禁跨前缀联表；ArchUnit A1~A12、C1~C4 规则 CI 强制守护。

## BabyLog 业务层（babylog-plugin-biz）

V2 的全部 BabyLog 业务都在这一对插件里，脚手架模块不改。详细口径见
[biz README](babylog-plugins/babylog-plugin-biz/README.md)。

| 项 | 约定 |
|---|---|
| 包名 | `com.babylog.biz` / `com.babylog.biz.api` |
| 表前缀 | `bl_`；**`bl_` 表不加 `tenant_id`**（多租户必须关） |
| 主键 | 雪花 BIGINT，禁自增 |
| 同步协议 | `GET /sync/changes`（`since + sinceId` 双段游标，`nextSince` 不透明只准原样回传）+ `POST /sync/push`（≤200 条，逐条 version 乐观锁 ODKU） |
| 错误码 | 8300–8399（8340–8359 同步、8360–8369 设备、8370–8379 认证）；**8344 = 换 ID 重推不是重试**；8343/8342/8345/8350 为丢弃类；8350 = family_members/member_permissions 整表拒推 |
| 家庭端点 | `/app/babylog/family/**` 七端点（**无尾斜杠**，带斜杠返回 1004）；加入默认 VIEWER；踢出 REMOVED 靠 scope 只认 ACTIVE；非 OWNER 的 `FamilyVO` 里 `inviteCode` 键直接缺省 |
| 读侧 | `user_id IN (visibleUserIds)`，无家庭 = {自己}；权限行显式行覆盖 + 角色兜底 |
| 内置产品 | id 1~99 只从 base 通道拉、8345 拒推 |
| token | app-user / wx-mini-user 走 `Authorization: <裸值>`，无 Bearer |

**四端必记契约坑**：

1. **布尔列按方向分工**：读 = 整数 `0/1` → Bool（容忍缺键）；**写 / push 必须发整数 `1/0`，绝不发 `true/false`** —— `SyncPropertyAccess` 判「Boolean 与 Integer 不兼容」→ 8342 丢整条行。
2. **JSON 里所有 Long 都是字符串**；**全局 NON_NULL** ⇒ 任何 null 列（含 `endedAt` / `transitions` / `inviteCode`）JSON 键都缺省，四端解析一律容忍缺键。
3. **同步表任何 `updateById` 都必须置空 `serverUpdatedAt`**，否则增量游标不前进（M18 已修，类注释写死）。
4. **push 死锁重试**：`SyncServiceImpl` 用 `TransactionTemplate` 外层最多 3 次重试 + 内层单事务（死锁回滚的是整个事务，语句级重试无意义；同类自调用 `@Transactional` 不走代理）。重试范围只收 `PessimisticLockingFailureException` 与 SQLState `40xxx`。

## 功能特性

- **系统管理**：用户、角色、菜单、部门、岗位、字典、参数、公告、操作日志、登录日志、在线用户、数据权限
- **多租户**：字段 / Schema / 数据源三种隔离模式，行级过滤自动追加
- **消息中心 / 文件存储 / 工作流 / 任务调度 / 监控运维**：沿用脚手架能力
- **AI 对话 / AI Coding / RAG 知识库 / 代码生成器 / BabyLog·智域**：沿用脚手架能力
- **BabyLog 业务**：认证、设备与 base 通道、云同步、家庭共享与权限裁决、管理端内置产品与记录类型规则

## 快速开始

环境要求：JDK 25、Maven 3.9+、MySQL 8.x、Redis 6+（可选：Milvus、XXL-Job、MinIO）

```bash
git clone git@gitee.com:baby-log/server.git
cd server

# 配置全部内联 yml（废除 .env）；dev 默认指向远端 dev 中间件
mvn clean install -DskipTests
java -jar babylog-admin-server/target/babylog-admin-server.jar
```

启动后：后端 API `http://localhost:8080`，超管 `admin / admin123`。
统一响应 `R<T>`（`code=0` 成功）、分页 `pageNum/pageSize`、Token = `Authorization` 头裸值。

## 日常验证命令（每次照抄）

```bash
MVN=~/.m2/wrapper/dists/apache-maven-3.9.16/56ba1f9f/bin/mvn

# 全量 reactor（必须 -o：Druid CreateConnectionThread 遇依赖内网中间件的测试会无限重试挂死）
$MVN -o -f pom.xml test                       # 56/56 模块 BUILD SUCCESS

# biz 单测（不加 -am；改了 biz-api 契约后先单独 install api jar）
$MVN -o -f pom.xml -pl babylog-plugins/babylog-plugin-biz test
$MVN -o -f pom.xml -pl babylog-plugins/babylog-plugin-biz-api install -DskipTests

# 架构门禁
$MVN -o -f pom.xml -pl babylog-admin-server -am \
  -Dtest=P0ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test

# 起 dev 服（AI 会话里有 SERVER__PORT 环境变量会压过 yml，必须 env -u）
env -u SERVER__PORT java -jar babylog-admin-server/target/babylog-admin-server.jar \
  --spring.profiles.active=dev --server.port=18080
```

- 测试分层：13 个依赖 `175.24.176.176` 内网中间件的集成测试已移出默认 `surefire` 门禁（`excludes`），
  覆盖不降级——`SaveBatchClasspathGuardTest` 的 classpath 维度由 `WarmFlowVersionGuardTest#w3` 在默认门禁守住。
- **ArchUnit 规则「写了」≠「在跑」**：必须核对 surefire 报告里出现对应 testcase（A3 曾因注解行落在 `//` 注释里从未执行）；A12 带 `scanned >= 50` 防空转下限。
- Flyway 版本号是**全库全局序列**，写 `ALTER` 前先 grep 已有列；MySQL DDL 不进事务。
- `admin-server` fat jar 有 up-to-date 误跳过问题：删 target jar 重打包，并解出内嵌 biz jar 核对新类。
- 本仓配置含明文凭据的 yml 用 `git update-index --skip-worktree` 隔离（`git ls-files -v` 显示 `S`）；
  生产部署（1Panel / JAR + Nginx）见 [13-D1正服部署执行手册](../独立产品规划/13-D1正服部署执行手册.md)。

## 相关仓库与文档

| 仓库 / 文档 | 说明 |
| --- | --- |
| [BabyLog-Android](../BabyLog-Android/README.md) | 原生 Android 客户端（V2 首发主端） |
| [BabyLog-iOS](../BabyLog-iOS/README.md) | 原生 iOS / iPadOS / macOS 客户端 |
| [BabyLog-Mini](../BabyLog-Mini/README.md) | 微信小程序（全功能主端） |
| [BabyLog-TV](../BabyLog-TV/README.md) | Android TV thin client |
| [ui](../ui/README.md)（同级目录 `../ui`） | PC 管理端（Vue3 + Element Plus，pnpm Monorepo） |
| [独立产品规划/README](../独立产品规划/README.md) | 本地规划文档索引（00~19，非 git） |
| [19-V2收口执行档案](../独立产品规划/19-V2收口执行档案-2026-11-18.md) | V2 24 条台账与全部实测证据 |
| [18-记录增强统一契约](../独立产品规划/18-记录增强统一契约.md) | 四端记录增强链单一事实源 |

上游脚手架相关仓库（pivotos-framework / pivotos-ui / pivotos-app / pivotos-docs / pivotos-docsite）
仍按原 README 记录，本仓已全量改名，不作为 BabyLog 交付依据。

## 维护约定

> 新增 Starter / Plugin / 功能特性时，须同步更新本 README 的「模块结构」「功能特性」两节与在线文档对应章节；
> 改同步契约 / 错误码时，须同步更新 biz README 与四端数据契约节。
> `CHANGELOG.md` 记录版本变更，`PROGRESS.md` / `ACCEPTANCE.md` 不补写历史，统一以 19 号档案为准。

## License

[Apache License 2.0](./LICENSE) · Copyright 2026 [胡伟龙（Alex）](https://www.293242.com/)

凭据约定：本仓库为**私有仓库**，配置全部内联 yml（dev/test 已填值可直接用；prod 凭据直接填 application-prod.yml，不再使用 .env / 环境变量注入）。
