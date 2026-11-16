package com.babylog.biz.devicebind.store;

import com.babylog.biz.auth.dto.DeviceInfo;

/**
 * Redis 中的申请记录。approvedBy 用字符串穿过 Lua cjson，避免 63 位 userId
 * 被 Lua number 的 double 表示截断；原始 userCode/deviceCode 永不进入本对象。
 */
public record DeviceBindRecord(String status, DeviceInfo device, String approvedBy) {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String DENIED = "DENIED";
    public static final String CONSUMED = "CONSUMED";

    public static DeviceBindRecord pending(DeviceInfo device) {
        return new DeviceBindRecord(PENDING, device, null);
    }
}
