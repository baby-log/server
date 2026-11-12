package com.babylog.biz.sync.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 单条被拒记录。
 *
 * <p>推送是「部分成功」语义：一条记录被拒不影响同批其他记录落库。
 * 客户端按 {@link #code} 决定处置：
 * <ul>
 *   <li>{@code 8344}（ID 归属冲突）→ <b>换一个雪花 ID</b> 重新入队，数据内容不变；</li>
 *   <li>{@code 8345}（内置数据）→ 丢弃这条，内置行由服务端下发；</li>
 *   <li>{@code 8342}（格式非法）→ 丢弃并上报日志（属于客户端 bug，重试不会成功）。</li>
 * </ul>
 */
@Data
public class SyncPushRejectVO {

    @Schema(description = "表标识")
    private String table;

    @Schema(description = "记录主键（解析不出时为 null）")
    private Long id;

    @Schema(description = "错误码（8300 段）")
    private int code;

    @Schema(description = "拒绝原因")
    private String message;

    public static SyncPushRejectVO of(String table, Long id, int code, String message) {
        SyncPushRejectVO vo = new SyncPushRejectVO();
        vo.setTable(table);
        vo.setId(id);
        vo.setCode(code);
        vo.setMessage(message);
        return vo;
    }
}
