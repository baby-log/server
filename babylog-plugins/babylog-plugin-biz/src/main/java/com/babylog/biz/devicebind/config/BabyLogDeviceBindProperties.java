package com.babylog.biz.devicebind.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 设备码绑定策略；默认值即可用，无需在环境 yml 中保存任何密钥。 */
@Data
@ConfigurationProperties(prefix = "babylog.device-bind")
public class BabyLogDeviceBindProperties {

    /** 申请与已领奖记录的总 TTL（秒）。 */
    private long codeTtlSeconds = 600L;

    /** 每来源 IP 每分钟允许申请的个数。 */
    private int applyPerIpPerMinute = 5;

    /** 同一 deviceCode 的轮询最小间隔（秒）。 */
    private int pollIntervalSeconds = 5;

    /** 同一登录账号在失败窗口内允许的确认失败次数。 */
    private int confirmFailUserLimit = 5;

    /** 同一来源 IP 在失败窗口内允许的确认失败次数。 */
    private int confirmFailIpLimit = 20;

    /** 确认失败计数窗口（秒）。 */
    private long confirmFailWindowSeconds = 600L;

    /** 单次领奖独占锁 TTL（秒）。 */
    private long claimTtlSeconds = 30L;
}
