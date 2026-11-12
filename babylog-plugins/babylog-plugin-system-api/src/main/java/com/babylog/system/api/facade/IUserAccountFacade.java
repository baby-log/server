package com.babylog.system.api.facade;

import com.babylog.system.api.dto.UserAccountCreateCmd;
import com.babylog.system.api.dto.UserDTO;

/**
 * 用户账号写契约
 *
 * <p>供注册、首次登录建档等跨 Plugin 场景使用；与只读的 {@link IUserFacade} 分离。
 * 方法不依赖登录上下文，密码哈希不会传出 system 插件边界。
 */
public interface IUserAccountFacade {

    /**
     * 按邮箱查账号（调用方负责 trim + 小写归一化）
     *
     * @param email 邮箱
     * @return 用户 DTO，不存在或邮箱为空白时返回 null
     */
    UserDTO getByEmail(String email);

    /**
     * 用户名是否已被占用（含已停用但未删除的账号）
     *
     * @param username 用户名
     * @return 已占用返回 true
     */
    boolean existsByUsername(String username);

    /**
     * 邮箱是否已被绑定
     *
     * @param email 邮箱
     * @return 已绑定返回 true；null、空串或空白一律返回 false
     */
    boolean existsByEmail(String email);

    /**
     * 创建账号
     *
     * @param cmd 创建命令
     * @return 新用户 ID
     * @throws com.babylog.common.core.exception.ServiceException 用户名冲突时为
     *         {@code USERNAME_EXISTS}，邮箱冲突时为 {@code EMAIL_ALREADY_USED}
     */
    Long createAccount(UserAccountCreateCmd cmd);

    /**
     * 校验密码
     *
     * @param userId 用户 ID
     * @param rawPassword 原始密码
     * @return 校验通过返回 true；账号不存在、无密码或哈希非法一律返回 false
     */
    boolean checkPassword(Long userId, String rawPassword);
}
