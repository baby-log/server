package com.babylog.biz.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备信息（登录时可选上报）。
 *
 * <p>{@code deviceUuid} 是 App 首次启动生成并持久化的随机串，服务端据此把「同一台手机的多次登录」
 * 归到 {@code bl_device} 的同一行；不上报则不落设备行（不阻断登录）。
 */
@Data
public class DeviceInfo {

    @Schema(description = "设备唯一标识（App 首次启动生成并持久化）")
    private String deviceUuid;

    @Schema(description = "设备名（型号或用户自定义）")
    private String deviceName;

    @Schema(description = "平台：ANDROID / IOS，缺省 UNKNOWN")
    private String platform;

    @Schema(description = "App 版本（versionName）")
    private String appVersion;
}
