package com.babylog.biz.sync.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 批量推送请求。
 *
 * <p>结构是「按表分组」而不是「扁平列表 + 每行带 table 字段」：与拉取响应同构，
 * 客户端可以把本地 Room 的待同步队列直接按表聚合后发出，服务端也能一表一次预查。
 *
 * <p>每行的键是 <b>camelCase 属性名</b>（与 Room 实体字段同名，如 {@code startedAt}），
 * 服务端只取该表登记过的载荷列（业务列 + {@code id} + {@code version} + {@code deleted}），
 * 其余键（含 {@code userId} / {@code serverUpdatedAt} / 审计四件套）<b>一律忽略</b>——
 * 归属与审计由服务端按 token 盖章，客户端传什么都不作数。
 */
@Data
public class SyncPushCmd {

    @Schema(description = "设备唯一标识（可选，仅用于服务端日志归因）")
    private String deviceUuid;

    @NotEmpty(message = "changes 不能为空")
    @Schema(description = "表标识 → 记录数组；表标识与 App 端 Room 表名逐字一致（feedings/diapers/...）")
    private Map<String, List<Map<String, Object>>> changes;
}
