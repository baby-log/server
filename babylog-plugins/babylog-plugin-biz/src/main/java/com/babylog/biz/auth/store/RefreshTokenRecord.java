package com.babylog.biz.auth.store;

/**
 * refresh token 解析结果。
 *
 * @param userId            归属用户
 * @param deviceUuid        签发时的设备标识（可为空字符串）
 * @param remainingSeconds  剩余有效期（秒），供客户端续期展示，也用于判断是否已过期
 */
public record RefreshTokenRecord(Long userId, String deviceUuid, long remainingSeconds) {
}
