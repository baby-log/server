package com.babylog.biz.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备列表行（App 端「设备管理」用）。
 *
 * <p>deviceUuid 给全量：能看到这份列表的只有账号本人，而 App 端要靠它识别
 * 「哪台是当前设备」（本机 deviceUuid 与行匹配即标记），掩码会让这个功能失效。
 */
@Data
@Schema(description = "账号下的一台已登记设备")
public class DeviceVO {

    @Schema(description = "设备行 id（踢出端点用）")
    private Long id;

    @Schema(description = "设备唯一标识（App 首次启动生成）")
    private String deviceUuid;

    @Schema(description = "设备名（App 上报的型号/自定义名）")
    private String deviceName;

    @Schema(description = "平台：ANDROID/IOS")
    private String platform;

    @Schema(description = "App 版本（versionName）")
    private String appVersion;

    @Schema(description = "状态：ACTIVE 活跃 / KICKED 已被移除")
    private String status;

    @Schema(description = "设备级雪花 workerId（0-1023）")
    private Integer workerId;

    @Schema(description = "最后活跃时刻（epoch millis）")
    private Long lastActiveAt;
}
