package com.babylog.biz.auth.store;

import java.time.Duration;
import java.util.Optional;

/**
 * refresh token 存储：签发、解析、作废。
 *
 * <p>refresh token 是服务端生成的随机串，**只在 Redis 存 SHA-256 哈希**——
 * 即便 Redis 快照泄漏也换不出可用凭证（与密码同思路，但不需要慢哈希：
 * 原像空间足够大，暴力不可行）。
 */
public interface RefreshTokenStore {

    /** 签发：生成随机串、存哈希、返回明文 */
    String issue(Long userId, String deviceUuid, Duration ttl);

    /** 解析：返回记录（含剩余有效期），无效/过期返回 empty */
    Optional<RefreshTokenRecord> resolve(String tokenValue);

    /** 作废（退出登录、改密等） */
    void revoke(String tokenValue);
}
