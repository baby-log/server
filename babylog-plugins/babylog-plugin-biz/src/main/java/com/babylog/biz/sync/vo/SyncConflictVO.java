package com.babylog.biz.sync.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * M53：版本冲突明细。
 *
 * <p>last-write-wins 的裁决语义保持不变（库内 version 不低于推送 version 时服务端优先、
 * 计入 skipped，幂等重推仍安全），但不再「静默跳过」：把冲突行回给客户端，
 * 让各端可以提示「这条记录已在另一台设备修改，本机显示的是云端版本」。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncConflictVO {

    @Schema(description = "同步表标识")
    private String table;

    @Schema(description = "记录 id（JSON 里按字符串承载）")
    private String id;

    @Schema(description = "服务端当前版本号")
    private long serverVersion;

    @Schema(description = "客户端推送的版本号")
    private long clientVersion;
}
