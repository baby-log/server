package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.starter.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 意见反馈（服务端专属表）：App 提交，后台处理。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_feedback")
public class BlFeedback extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 提交用户 id（可空=匿名） */
    private Long userId;

    /** 联系方式（可空） */
    private String contact;

    /** 反馈内容 */
    private String content;

    /** 处理状态：PENDING/REPLIED/CLOSED */
    private String status;

    /** 回复内容 */
    private String reply;

    /** 回复时刻 */
    private LocalDateTime replyTime;
}
