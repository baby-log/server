package com.babylog.biz.device.vo;

import com.babylog.biz.device.vo.DeviceVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 按账号查询的设备总览（管理端）：账号摘要 + 该账号全部设备（含已移除） */
@Data
@Schema(description = "账号设备总览（管理端）")
public class AdminDeviceOverviewVO {

    @Schema(description = "用户 id")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "邮箱（可空）")
    private String email;

    @Schema(description = "账号状态：0 正常 1 停用")
    private Integer status;

    @Schema(description = "该账号全部设备（含 KICKED，按最后活跃倒序）")
    private List<DeviceVO> devices;
}
