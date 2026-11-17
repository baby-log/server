package com.babylog.biz.device.service;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.sync.mapper.BlDeviceViewMapper;
import com.babylog.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

/**
 * 设备级雪花机器位分配（workerId，0-1023）。
 *
 * <p>为什么按「同用户内 max+1」而不是全局 max+1：workerId 的职责是让<b>同一账号的多台设备</b>
 * 生成不撞的雪花 ID；跨账号撞车由推送时的归属校验兜底（8344，客户端换 ID 重试）。
 * 全局唯一会迫使 workerId 成为稀缺资源（1024 台设备就满），而账号内唯一可以让
 * 每个用户都拥有完整的 0-1023 号段。
 *
 * <p>分配查询<b>不过滤 deleted</b>：软删除的设备仍占着号，复用它的号会直接撞唯一键——
 * 该查询走无逻辑删除的 {@link BlDeviceViewMapper}（视图实体，deleted 全量可见）。
 *
 * <p>并发安全：{@code uk(user_id, worker_id)} 是最终防线——两个设备同时登录拿到同一个
 * 候选值时，后插入的那条会撞唯一键，由调用方回读重试（见 {@code AppAuthServiceImpl#touchDevice}）。
 */
@Component
public class DeviceWorkerIdAllocator {

    /** 雪花 workerId 上界（5 位机器位） */
    public static final int MAX_WORKER_ID = 1023;

    private final BlDeviceViewMapper deviceViewMapper;

    public DeviceWorkerIdAllocator(BlDeviceViewMapper deviceViewMapper) {
        this.deviceViewMapper = deviceViewMapper;
    }

    /**
     * 为该账号分配下一个可用的 workerId。
     *
     * @throws ServiceException 号段用尽（8361）：同账号已有 1024 台设备，属异常使用形态
     */
    public int nextWorkerId(Long userId) {
        Integer max = deviceViewMapper.selectMaxWorkerId(userId);
        int next = (max == null ? -1 : max) + 1;
        if (next > MAX_WORKER_ID) {
            throw new ServiceException(BabyLogErrorCode.DEVICE_LIMIT_EXCEEDED);
        }
        return next;
    }
}
