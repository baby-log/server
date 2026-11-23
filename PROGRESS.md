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
