package com.babylog.biz.sync.mapper;

import com.babylog.biz.sync.entity.BlDeviceView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link BlDeviceViewMapper#maxWorkerIdOf} 的口径钉死（S3：max+1 分配的 Java 侧等价）。
 *
 * <p>对应原 SQL {@code COALESCE(MAX(worker_id), -1)} 的两个边界：MAX 忽略 NULL（老行
 * workerId 为空），空集返回 -1（上游 nextWorkerId 直接 +1）。deleted 语义（软删行仍占号）
 * 由无逻辑删除的视图实体承担（BlDeviceView 遮蔽 BaseDO.@TableLogic，查询天然全行可见，
 * 无任何 deleted 条件），见 mapper 类注释。
 */
class BlDeviceViewMapperTest {

    private static BlDeviceView device(Integer workerId) {
        BlDeviceView device = new BlDeviceView();
        device.setWorkerId(workerId);
        return device;
    }

    @Test
    void 空集返回负一() {
        assertEquals(-1, BlDeviceViewMapper.maxWorkerIdOf(List.of()));
    }

    @Test
    void 全部为空取负一() {
        assertEquals(-1, BlDeviceViewMapper.maxWorkerIdOf(List.of(device(null), device(null))));
    }

    @Test
    void 忽略空值取最大() {
        assertEquals(6, BlDeviceViewMapper.maxWorkerIdOf(List.of(device(null), device(0), device(6), device(2))));
    }

    @Test
    void 单台设备即其号() {
        assertEquals(0, BlDeviceViewMapper.maxWorkerIdOf(List.of(device(0))));
    }
}
