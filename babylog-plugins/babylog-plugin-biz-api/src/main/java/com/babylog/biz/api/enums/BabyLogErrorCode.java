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
 *   <li>8340-8359 同步（8348/8349 为管理端产品维护占位，语义仍属同步/基础数据域）</li>
 *   <li>8360-8369 设备</li>
 *   <li>8370-8379 认证（邮箱验证码 / 注册 / token，M5 占用）</li>
 *   <li>8380-8389 反馈</li>
 *   <li>8390-8394 版本公告（M10 管理端）</li>
 *   <li>8395-8399 小程序认证（M20，wx.login code 换 app-user 双 token）</li>
 *   <li>8400-8419 电视设备码绑定（M45）</li>
 * </ul>
 */
public enum BabyLogErrorCode implements ErrorCode {

    // ---------- 家庭与成员（8300-8319） ----------
    FAMILY_NOT_FOUND(8300, "家庭不存在"),
    INVITE_CODE_INVALID(8301, "邀请码无效或已过期"),
    NOT_FAMILY_MEMBER(8302, "非家庭成员"),
    MEMBER_ALREADY_JOINED(8303, "该用户已在家庭中"),
    OWNER_TRANSFER_FORBIDDEN(8304, "仅家庭创建者可管理成员"),
    /**
     * OWNER 不能用「退出」代替「解散」（M18）：OWNER 是不可转让的兜底角色（01B 第六节），
     * 直接退出会把家庭留在「有 ACTIVE 成员却无人可管理」的锁死态——要走 POST /family/dissolve。
     */
    OWNER_LEAVE_FORBIDDEN(8305, "家庭创建者不能退出，请先解散家庭"),

    // ---------- 宝宝（8320-8329） ----------
    CHILD_NOT_FOUND(8320, "宝宝不存在"),
    CHILD_LIMIT_EXCEEDED(8321, "宝宝数量超出上限"),

    // ---------- 同步（8340-8359） ----------
    SYNC_VERSION_CONFLICT(8340, "同步版本冲突，请先拉取最新数据"),
    SYNC_PAYLOAD_TOO_LARGE(8341, "单次推送记录数超出上限"),
    SYNC_RECORD_INVALID(8342, "推送记录格式非法"),
    SYNC_CATEGORY_DENIED(8343, "无该分类的写权限"),
    /**
     * 归属冲突：主键已存在且属于<b>其他账号</b>。
     *
     * <p>跨用户雪花 ID 撞车的兜底（概率极低但存在：不同账号复用 workerId + 同毫秒 + 同序列）。
     * 客户端处理方式是<b>换 ID 重新入队</b>——不是重试，重试同一个 ID 永远不会成功。
     */
    SYNC_OWNERSHIP_CONFLICT(8344, "记录 ID 已被其他账号占用，请更换 ID 后重试"),
    /** 内置数据（bl_products 的 1~99 预留段）由服务端下发，客户端推送一律拒绝——否则每台设备都会插一份副本 */
    SYNC_BUILTIN_IMMUTABLE(8345, "内置数据由服务端下发，不接受客户端推送"),
    SYNC_CATEGORY_INVALID(8346, "同步分类标识非法"),
    SYNC_TABLE_UNKNOWN(8347, "同步表标识非法"),
    /** 管理端按 id 操作产品行时的不存在/墓碑态（@TableLogic 滤掉 deleted=1，墓碑正好落这里——守住「软删行不复活」） */
    PRODUCT_NOT_FOUND(8348, "产品不存在"),
    /** 管理端只能维护内置产品（is_builtin=1，id 1~99）；用户自建行走 App 同步通道，管理端不得触碰（base/sync 两通道隔离红线） */
    PRODUCT_NOT_BUILTIN(8349, "仅内置产品可通过管理端维护"),
    /**
     * 服务端管理表拒推（M16）：bl_family_members / bl_member_permissions 是服务端维护的
     * 只读镜像，客户端只从 changes 拉取——8345 内置产品的同款「下发型」语义。
     *
     * <p>为什么必须整表拒收（2026-11-13 龙哥拍板）：这两张表的行归属恰好是
     * {@code user_id = 成员自己}，归属校验（8344）拦不住成员整行重推自己拉回来的行——
     * 不拒收等于允许自我改成 OWNER / 翻转 canWrite，权限裁决权在服务端（01B 红线）形同虚设。
     */
    SYNC_MEMBER_TABLE_IMMUTABLE(8350, "成员与权限行由服务端管理，不接受客户端推送"),

    // ---------- 设备（8360-8369） ----------
    DEVICE_NOT_FOUND(8360, "设备不存在"),
    DEVICE_LIMIT_EXCEEDED(8361, "设备数量已达上限（1024 台），请先移除不用的设备"),
    /** 管理端按账号（用户名/邮箱）解析用户失败——App 端不使用本码 */
    ACCOUNT_NOT_FOUND(8362, "账号不存在"),

    // ---------- 认证（8370-8379） ----------
    /** 验证码错误的可能：输错、已过期、已被用掉、错误次数超限作废——统一一个码，不给撞码者区分信号 */
    EMAIL_CODE_INVALID(8370, "验证码错误或已过期，请重新获取"),
    EMAIL_CODE_TOO_FREQUENT(8371, "验证码发送过于频繁，请稍后再试"),
    EMAIL_DAILY_LIMIT(8372, "今日验证码发送次数已达上限，请明天再试"),
    EMAIL_FORMAT_INVALID(8373, "邮箱格式不正确"),
    MAIL_NOT_CONFIGURED(8374, "邮件服务未配置，暂时无法发送验证码"),
    MAIL_SEND_FAILED(8375, "验证码邮件发送失败，请稍后重试"),
    REFRESH_TOKEN_INVALID(8376, "登录状态已失效，请重新登录"),
    EMAIL_ALREADY_USED(8377, "该邮箱已绑定其他账号"),
    /** 注册时用户名被占用（上游契约抛 2003，biz 侧转译成 8xxx 段，App 只认这一段） */
    USERNAME_ALREADY_USED(8378, "用户名已被占用"),

    // ---------- 反馈（8380-8389） ----------
    FEEDBACK_NOT_FOUND(8380, "反馈不存在"),
    FEEDBACK_CONTENT_INVALID(8381, "反馈内容须为 5-1000 字"),

    // ---------- 版本公告（8390-8394，M10） ----------
    APP_VERSION_NOT_FOUND(8390, "版本记录不存在"),
    /** 已发布（PUBLISHED）的版本锁定不可再改/再发/删除——用户可能已经看到过这版公告 */
    APP_VERSION_NOT_DRAFT(8391, "仅草稿状态的版本可修改/发布/删除"),

    // ---------- 记录类型云端规则（8392-8394，M41） ----------
    RECORD_TYPE_RULE_NOT_FOUND(8392, "记录类型规则不存在"),

    // ---------- 小程序认证（8395-8399，M20） ----------
    /** 小程序应用未配置（babylog.wechat.miniapp.configs.babylog 缺 appid/secret），对齐 8374 MAIL_NOT_CONFIGURED 的先例模式 */
    WX_MINI_NOT_CONFIGURED(8395, "小程序登录暂未配置，请联系管理员"),
    /** code 无效/已使用/微信接口失败——客户端处置统一为「拉起新 code 重新登录」 */
    WX_CODE_INVALID(8396, "微信登录凭证无效或已过期，请重新登录"),
    /** 绑定冲突：openid 已绑定其他账号，或绑定指向不存在的账号（孤儿绑定）——换绑是管理动作，登录路不做 */
    WX_MINI_BOUND_CONFLICT(8397, "该微信已绑定其他账号，请联系管理员"),

    // ---------- 电视设备码绑定（8400-8419，M45） ----------
    /** 不区分输错、过期、已处理，避免把 6 位码状态变成枚举 oracle。 */
    DEVICE_BIND_CODE_INVALID(8400, "设备码无效、已过期或已处理"),
    DEVICE_BIND_RATE_LIMITED(8401, "设备码操作过于频繁，请稍后再试"),
    DEVICE_BIND_CONFIRM_LOCKED(8402, "设备码确认失败次数过多，请稍后再试"),
    /** 一次性领奖：已消费或失去最终消费权时都绝不重复返回 token。 */
    DEVICE_BIND_ALREADY_CLAIMED(8403, "设备登录凭证已领取，请重新申请设备码"),

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
