package com.babylog.biz.devicebind.store;

import java.time.Duration;
import java.util.Optional;

/** 参数中的 codeHash/ipHash 均已散列，实现不得接收或落存原始码、原始 IP。 */
public interface DeviceBindStore {

    boolean create(String userCodeHash, String deviceCodeHash, DeviceBindRecord record, Duration ttl);

    Optional<String> resolveDeviceHash(String userCodeHash);

    boolean approve(String userCodeHash, String deviceCodeHash, long userId);

    boolean deny(String userCodeHash, String deviceCodeHash);

    DeviceBindClaim beginClaim(String deviceCodeHash, String claimToken, Duration claimTtl);

    boolean finishClaim(String deviceCodeHash, String claimToken, String approvedBy);

    void releaseClaim(String deviceCodeHash, String claimToken);

    long increaseApplyCount(String ipHash, Duration window);

    boolean acquirePollSlot(String deviceCodeHash, Duration interval);

    long confirmUserFailures(long userId);

    long confirmIpFailures(String ipHash);

    void increaseConfirmFailures(long userId, String ipHash, Duration window);

    void clearConfirmFailures(long userId, String ipHash);
}
