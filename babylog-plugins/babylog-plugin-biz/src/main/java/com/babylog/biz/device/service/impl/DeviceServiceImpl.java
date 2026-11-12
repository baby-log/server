package com.babylog.biz.device.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.device.service.DeviceService;
import com.babylog.biz.device.vo.DeviceVO;
import com.babylog.biz.entity.BlDevice;
import com.babylog.biz.mapper.BlDeviceMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.babylog.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 设备管理实现。
 *
 * <p>查询不过滤 {@code status}：KICKED 行也要回给 App，用户才知道「哪台被移除了、
 * 哪些还在」；物理删行只发生在未来的「彻底清理」端点（等 workerId 回收方案一起议）。
 */
@Service
public class DeviceServiceImpl implements DeviceService {

    private final BlDeviceMapper deviceMapper;

    public DeviceServiceImpl(BlDeviceMapper deviceMapper) {
        this.deviceMapper = deviceMapper;
    }

    @Override
    public List<DeviceVO> list(long userId) {
        return deviceMapper.selectList(Wrappers.<BlDevice>lambdaQuery()
                        .eq(BlDevice::getUserId, userId)
                        .orderByDesc(BlDevice::getLastActiveAt))
                .stream()
                .map(DeviceServiceImpl::toVo)
                .toList();
    }

    @Override
    public void kick(long userId, long deviceId) {
        BlDevice row = deviceMapper.selectById(deviceId);
        // 归属校验：id 猜别人的行不给任何信号，统一 8360
        if (row == null || row.getUserId() == null || row.getUserId() != userId) {
            throw new ServiceException(BabyLogErrorCode.DEVICE_NOT_FOUND);
        }
        row.setStatus(BlDevice.STATUS_KICKED);
        deviceMapper.updateById(row);
    }

    private static DeviceVO toVo(BlDevice row) {
        DeviceVO vo = new DeviceVO();
        vo.setId(row.getId());
        vo.setDeviceUuid(row.getDeviceUuid());
        vo.setDeviceName(row.getDeviceName());
        vo.setPlatform(row.getPlatform());
        vo.setAppVersion(row.getAppVersion());
        vo.setStatus(row.getStatus());
        vo.setWorkerId(row.getWorkerId());
        vo.setLastActiveAt(row.getLastActiveAt() == null
                ? null : java.sql.Timestamp.valueOf(row.getLastActiveAt()).getTime());
        return vo;
    }
}
