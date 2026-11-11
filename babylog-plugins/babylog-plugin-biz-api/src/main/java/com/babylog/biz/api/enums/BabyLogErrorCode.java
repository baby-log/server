package com.babylog.biz.api.enums;

import com.babylog.common.core.enums.error.ErrorCode;

/**
 * babylog 业务域错误码（8xxx 段内 8300-8399 子段）
 *
 * <p>号段背景：8xxx 千位段已被 migration（8000-8099）/ search（8100-8122）/ datainspect（8200-8221）
 * 占用，BabyLog 业务使用其空闲子段 8300-8399（2026-11-11 拍板）。
 *
 * <p>号段分配：
 * <ul>
 *   <li>8300-8319 家庭与成员</li>
 *   <li>8320-8329 宝宝</li>
 *   <li>8340-8359 同步</li>
 *   <li>8360-8369 设备</li>
 *   <li>8380-8389 反馈</li>
 *   <li>8390-8399 保留</li>
 * </ul>
 */
public enum BabyLogErrorCode implements ErrorCode {

    // ---------- 家庭与成员（8300-8319） ----------
    FAMILY_NOT_FOUND(8300, "家庭不存在"),
    INVITE_CODE_INVALID(8301, "邀请码无效或已过期"),
    NOT_FAMILY_MEMBER(8302, "非家庭成员"),
    MEMBER_ALREADY_JOINED(8303, "该用户已在家庭中"),
    OWNER_TRANSFER_FORBIDDEN(8304, "仅家庭创建者可管理成员"),

    // ---------- 宝宝（8320-8329） ----------
    CHILD_NOT_FOUND(8320, "宝宝不存在"),
    CHILD_LIMIT_EXCEEDED(8321, "宝宝数量超出上限"),

    // ---------- 同步（8340-8359） ----------
    SYNC_VERSION_CONFLICT(8340, "同步版本冲突，请先拉取最新数据"),
    SYNC_PAYLOAD_TOO_LARGE(8341, "单次推送记录数超出上限"),
    SYNC_RECORD_INVALID(8342, "推送记录格式非法"),
    SYNC_CATEGORY_DENIED(8343, "无该分类的写权限"),

    // ---------- 设备（8360-8369） ----------
    DEVICE_NOT_FOUND(8360, "设备不存在"),

    // ---------- 反馈（8380-8389） ----------
    FEEDBACK_NOT_FOUND(8380, "反馈不存在"),

    ;

    private final int code;
    private final String msg;

    BabyLogErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }
}
