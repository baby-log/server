package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.starter.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 设备（服务端专属表）：设备绑定、推送 token、换机与踢出。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_device")
public class BlDevice extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 归属用户 id */
    private Long userId;

    /** 设备名（App 端上报的型号/自定义名） */
    private String deviceName;

    /** 平台：ANDROID/IOS */
    private String platform;

    /** App 版本（versionName） */
    private String appVersion;

    /** 推送 token（V2.0 可空，接入推送后回填） */
    private String pushToken;

    /** 状态：ACTIVE/KICKED */
    private String status;

    /** 最后活跃时刻 */
    private LocalDateTime lastActiveAt;
}
