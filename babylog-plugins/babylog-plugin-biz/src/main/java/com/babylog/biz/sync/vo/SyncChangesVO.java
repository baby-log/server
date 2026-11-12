package com.babylog.biz.sync.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 增量拉取结果。
 *
 * <p>游标口径：客户端「原样回传」{@link #nextSince} + {@link #nextSinceId} 即接着拉，
 * 传 {@code since=0} 即从头全量拉。<b>不要把 nextSince 当时间戳用</b>（见 {@code SyncCursor} 注释）。
 */
@Data
public class SyncChangesVO {

    @Schema(description = "表标识 → 变更记录数组（含 deleted=1 的软删除行）")
    private Map<String, List<Map<String, Object>>> changes;

    @Schema(description = "下一页游标：本页最后一条的 server_updated_at（服务端编码，原样回传）")
    private long nextSince;

    @Schema(description = "下一页游标 tiebreak：本页最后一条的 id（同毫秒分页用，原样回传）")
    private long nextSinceId;

    @Schema(description = "是否还有更多（本页已满，建议立刻再拉一次）")
    private boolean hasMore;

    @Schema(description = "本页记录数（各表合计）")
    private int count;

    @Schema(description = "服务端当前时间（同一套编码，供客户端估算时钟漂移）")
    private long serverTime;
}
