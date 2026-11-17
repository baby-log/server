package com.babylog.biz.sync.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.biz.sync.entity.BlDeviceView;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * bl_device 无逻辑删除视图实体的 Mapper（PivotOS 严格整改 S3）。
 *
 * <p>workerId 号段查询需要「被软删但仍占号」的设备行，而业务实体（BaseDO.@TableLogic）
 * 会把它们从查询路径滤掉。S2 曾用 {@code deleted=0 OR deleted=1} 恒真条件绕过——
 * 属表面绕过，本阶段清除：改用视图实体（遮蔽了 @TableLogic）后，查询语义即「全行」，
 * 条件里一个字都不需要提 deleted。
 */
@Mapper
public interface BlDeviceViewMapper extends BaseMapper<BlDeviceView> {

    /**
     * 该账号已用的最大 workerId（无设备或全部为 null 时返回 -1，方便上游直接 +1）。
     *
     * <p>标准 MyBatis-Plus 查询（LambdaQueryWrapper，零手写 SQL、零字符串列名/表名，
     * 参数全占位符绑定）：按 user_id 精确过滤，deleted 全量可见（视图实体无逻辑删除，
     * 见类注释）；MAX 聚合在 Java 侧求（{@link #maxWorkerIdOf}，忽略 null——与 SQL 聚合
     * {@code MAX} 忽略 NULL 同语义），换来完整契约合规。
     */
    default Integer selectMaxWorkerId(Long userId) {
        List<BlDeviceView> devices = selectList(new LambdaQueryWrapper<BlDeviceView>()
                .select(BlDeviceView::getWorkerId)
                .eq(BlDeviceView::getUserId, userId));
        return maxWorkerIdOf(devices);
    }

    /**
     * Java 侧求最大 workerId（null 忽略、空集返回 -1）——SQL {@code COALESCE(MAX(...), -1)}
     * 的纯函数等价，抽出以便单测直接钉死口径。
     */
    static Integer maxWorkerIdOf(Collection<BlDeviceView> devices) {
        return devices.stream()
                .map(BlDeviceView::getWorkerId)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1);
    }
}
