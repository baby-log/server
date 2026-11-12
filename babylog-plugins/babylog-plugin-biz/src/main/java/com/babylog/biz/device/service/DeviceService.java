package com.babylog.biz.device.service;

import com.babylog.biz.device.vo.DeviceVO;

import java.util.List;

/**
 * 设备管理（App 端）：列出本账号设备、踢出。
 *
 * <p>踢出的产品语义（V2.0 口径）：
 * <ul>
 *   <li>只拦 <b>refresh</b>（见 {@code AppAuthServiceImpl#refresh} 的 KICKED 拦截）——
 *       access token 最多再活 2 小时，之后设备被顶下线；</li>
 *   <li><b>重新登录可复活</b>：登录本来就要账号密码，能通过就说明是本人（或拿到了凭证），
 *       设备行回到 ACTIVE 并换发新 refresh。踢出防的是「旧 refresh 长期续命」，不是永久拉黑；</li>
 *   <li>行不物理删、workerId 不回收：软删行仍占着 {@code uk(user_id, worker_id)}，
 *       回收会把「重新登录」变成唯一键冲突 500（见 {@code DeviceWorkerIdAllocator}）。</li>
 * </ul>
 */
public interface DeviceService {

    /** 本账号全部设备（含 KICKED），按最后活跃倒序 */
    List<DeviceVO> list(long userId);

    /**
     * 踢出设备。
     *
     * @throws com.babylog.common.core.exception.ServiceException 8360：设备不存在或不属于该账号
     */
    void kick(long userId, long deviceId);
}
