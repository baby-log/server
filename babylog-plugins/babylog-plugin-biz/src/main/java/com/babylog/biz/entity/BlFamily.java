package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.starter.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 家庭（服务端专属表，App 端无对应表——App 端只有 family_key 常量，V2.1 换真实 family id）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_family")
public class BlFamily extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 家庭名称 */
    private String name;

    /** 邀请码（唯一，创建时生成，可重置） */
    private String inviteCode;

    /** 创建者用户 id（sys_user.id） */
    private Long ownerUserId;

    /** 状态：ACTIVE/DISSOLVED */
    private String status;
}
