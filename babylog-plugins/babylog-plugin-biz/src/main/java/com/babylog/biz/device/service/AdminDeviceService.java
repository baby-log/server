package com.babylog.biz.device.service;

import com.babylog.biz.device.vo.AdminDeviceOverviewVO;

/**
 * 管理端设备查看（M10）：按账号（用户名/邮箱）查设备、踢出设备。
 *
 * <p>踢出复用 App 端 {@link DeviceService#kick(long, long)} 的全部语义：
 * 置 KICKED + 拦 refresh（8376）+ 重新登录可复活 + workerId 不回收。
 * 管理端与账号本人踢出效果完全一致，同一行同一状态机，不另开一套。
 */
public interface AdminDeviceService {

    /**
     * 按账号查设备总览。
     *
     * @param account 用户名或邮箱（与 App 登录同口径：先按用户名查，查不到再按邮箱查）
     * @return 账号摘要 + 设备列表
     * @throws com.babylog.common.core.exception.ServiceException 8362 账号不存在
     */
    AdminDeviceOverviewVO overview(String account);

    /**
     * 踢出设备（按设备行 id，不需要账号参数——行上带归属）。
     *
     * @throws com.babylog.common.core.exception.ServiceException 8360 设备不存在
     */
    void kick(long deviceId);
}
