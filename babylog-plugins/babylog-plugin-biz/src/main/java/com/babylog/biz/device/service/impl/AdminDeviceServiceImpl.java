package com.babylog.biz.device.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.device.service.AdminDeviceService;
import com.babylog.biz.device.service.DeviceService;
import com.babylog.biz.device.vo.AdminDeviceOverviewVO;
import com.babylog.biz.device.vo.DeviceVO;
import com.babylog.biz.entity.BlDevice;
import com.babylog.biz.mapper.BlDeviceMapper;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.system.api.dto.UserDTO;
import com.babylog.system.api.facade.IUserAccountFacade;
import com.babylog.system.api.facade.IUserFacade;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 管理端设备查看实现：账号解析走 system 契约（IUserFacade / IUserAccountFacade），
 * 设备读写复用 M8 的 {@link DeviceService}（bl_device 属 biz 自己的表，直查无妨）。
 */
@Service
public class AdminDeviceServiceImpl implements AdminDeviceService {

    private final IUserFacade userFacade;
    private final IUserAccountFacade userAccountFacade;
    private final DeviceService deviceService;
    private final BlDeviceMapper deviceMapper;

    public AdminDeviceServiceImpl(IUserFacade userFacade,
                                  IUserAccountFacade userAccountFacade,
                                  DeviceService deviceService,
                                  BlDeviceMapper deviceMapper) {
        this.userFacade = userFacade;
        this.userAccountFacade = userAccountFacade;
        this.deviceService = deviceService;
        this.deviceMapper = deviceMapper;
    }

    @Override
    public AdminDeviceOverviewVO overview(String account) {
        UserDTO user = resolveAccount(account);
        AdminDeviceOverviewVO vo = new AdminDeviceOverviewVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setStatus(user.getStatus());
        vo.setDevices(deviceService.list(user.getId()));
        return vo;
    }

    @Override
    public void kick(long deviceId) {
        BlDevice row = deviceMapper.selectById(deviceId);
        if (row == null) {
            throw new ServiceException(BabyLogErrorCode.DEVICE_NOT_FOUND);
        }
        // 复用 M8 语义：归属校验 + KICKED + 拦 refresh + workerId 不回收，效果与本人踢出一致
        deviceService.kick(row.getUserId(), deviceId);
    }

    /**
     * 账号解析：先按用户名精确查，查不到再按邮箱查（trim + 小写归一化，
     * 与 AppAuthServiceImpl 的邮箱口径一致）。都查不到 → 8362，不区分「哪种没查到」。
     */
    private UserDTO resolveAccount(String account) {
        if (!StringUtils.hasText(account)) {
            throw new ServiceException(BabyLogErrorCode.ACCOUNT_NOT_FOUND);
        }
        String trimmed = account.trim();
        UserDTO user = userFacade.getByUsername(trimmed);
        if (user == null) {
            user = userAccountFacade.getByEmail(trimmed.toLowerCase());
        }
        if (user == null) {
            throw new ServiceException(BabyLogErrorCode.ACCOUNT_NOT_FOUND);
        }
        return user;
    }
}
