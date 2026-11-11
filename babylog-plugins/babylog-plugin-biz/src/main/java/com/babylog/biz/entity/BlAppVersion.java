package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.starter.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * App 版本与公告（服务端专属表）：强更/提示更新。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_app_version")
public class BlAppVersion extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 平台：ANDROID/IOS */
    private String platform;

    /** 版本号（versionCode，数值比较用） */
    private Integer versionCode;

    /** 版本名（versionName，展示用） */
    private String versionName;

    /** 强制更新：0/1 */
    private Integer forceUpdate;

    /** 下载地址（直装 apk / 应用市场链接） */
    private String downloadUrl;

    /** 更新说明/公告 */
    private String notice;

    /** 状态：DRAFT/PUBLISHED */
    private String status;
}
