package com.babylog.biz.appversion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 版本公告行（管理端） */
@Data
@Schema(description = "App 版本与公告行")
public class AppVersionVO {

    @Schema(description = "记录 id")
    private Long id;

    @Schema(description = "平台：ANDROID/IOS")
    private String platform;

    @Schema(description = "版本号 versionCode")
    private Integer versionCode;

    @Schema(description = "版本名 versionName")
    private String versionName;

    @Schema(description = "强制更新：0/1")
    private Integer forceUpdate;

    @Schema(description = "下载地址")
    private String downloadUrl;

    @Schema(description = "更新说明/公告")
    private String notice;

    @Schema(description = "状态：DRAFT 草稿 / PUBLISHED 已发布")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "最后更新时间")
    private LocalDateTime updateTime;
}
