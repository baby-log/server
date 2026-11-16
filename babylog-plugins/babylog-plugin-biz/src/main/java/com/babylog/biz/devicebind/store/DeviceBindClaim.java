package com.babylog.biz.devicebind.store;

/** 原子领奖结果；BUSY 对外仍是 pending，CONSUMED 对外是 8403。 */
public record DeviceBindClaim(Outcome outcome, DeviceBindRecord record) {

    public enum Outcome {
        PENDING,
        APPROVED,
        DENIED,
        EXPIRED,
        CONSUMED,
        BUSY
    }
}
