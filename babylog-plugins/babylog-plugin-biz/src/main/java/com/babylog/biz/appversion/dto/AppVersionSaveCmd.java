package com.babylog.biz.appversion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 版本新增/编辑请求（管理端）。status 不在请求体里——
 * DRAFT 由服务端建行时盖，PUBLISHED 只能走发布端点（单向）。
 */
@Data
public class AppVersionSaveCmd {

    @Schema(description = "平台：ANDROID/IOS")
    @NotBlank(message = "平台不能为空")
    @Pattern(regexp = "ANDROID|IOS", message = "平台须为 ANDROID 或 IOS")
    private String platform;

    @Schema(description = "版本号 versionCode（数值比较用）")
    @NotNull(message = "versionCode 不能为空")
    @Min(value = 1, message = "versionCode 须为正整数")
    private Integer versionCode;

    @Schema(description = "版本名 versionName（展示用）")
    @NotBlank(message = "versionName 不能为空")
    @Size(max = 32, message = "versionName 最长 32 字")
    private String versionName;

    @Schema(description = "强制更新：0 否 1 是")
    @NotNull(message = "forceUpdate 不能为空")
    @Min(value = 0, message = "forceUpdate 只允许 0/1")
    @Max(value = 1, message = "forceUpdate 只允许 0/1")
    private Integer forceUpdate;

    @Schema(description = "下载地址（直装 apk / 应用市场链接）")
    @Size(max = 500, message = "下载地址最长 500 字")
    private String downloadUrl;

    @Schema(description = "更新说明/公告")
    @Size(max = 1000, message = "更新说明最长 1000 字")
    private String notice;
}
