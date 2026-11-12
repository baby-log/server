package com.babylog.system.facade;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.babylog.common.core.enums.CommonStatusEnum;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.system.api.dto.UserAccountCreateCmd;
import com.babylog.system.api.dto.UserDTO;
import com.babylog.system.api.enums.SystemErrorCode;
import com.babylog.system.api.facade.IUserAccountFacade;
import com.babylog.system.convert.UserConvert;
import com.babylog.system.domain.entity.SysUser;
import com.babylog.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 用户账号门面本地实现（单体形态；微服务形态可整体替换为远程实现） */
@Component
@RequiredArgsConstructor
public class UserAccountLocalFacade implements IUserAccountFacade {

    private final UserService userService;
    private final UserConvert userConvert;

    @Override
    public UserDTO getByEmail(String email) {
        return userConvert.toDto(findByEmail(email));
    }

    @Override
    public boolean existsByUsername(String username) {
        return StringUtils.hasText(username) && userService.getByUsername(username) != null;
    }

    @Override
    public boolean existsByEmail(String email) {
        return findByEmail(email) != null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAccount(UserAccountCreateCmd cmd) {
        if (cmd == null) {
            throw new ServiceException("账号创建参数不能为空");
        }
        if (!StringUtils.hasText(cmd.getUsername())) {
            throw new ServiceException("用户名不能为空");
        }
        if (existsByUsername(cmd.getUsername())) {
            throw new ServiceException(SystemErrorCode.USERNAME_EXISTS);
        }
        if (existsByEmail(cmd.getEmail())) {
            throw new ServiceException(SystemErrorCode.EMAIL_ALREADY_USED);
        }

        SysUser entity = new SysUser();
        entity.setUsername(cmd.getUsername());
        entity.setNickname(StringUtils.hasText(cmd.getNickname()) ? cmd.getNickname() : cmd.getUsername());
        entity.setEmail(emptyIfNull(cmd.getEmail()));
        entity.setMobile(emptyIfNull(cmd.getMobile()));
        String rawPassword = StringUtils.hasText(cmd.getRawPassword())
                ? cmd.getRawPassword() : RandomUtil.randomString(32);
        entity.setPassword(BCrypt.hashpw(rawPassword, BCrypt.gensalt()));
        entity.setStatus(CommonStatusEnum.ENABLED.getValue());
        entity.setDeleted(0);
        // 部门、岗位、租户均保持 null；不调用角色关联逻辑，审计字段由 MetaObjectHandler 填充。
        try {
            userService.save(entity);
            return entity.getId();
        } catch (DuplicateKeyException e) {
            // 先查重只能覆盖常规路径；并发冲突以数据库唯一键为最终防线。
            if (existsByUsername(cmd.getUsername())) {
                throw new ServiceException(SystemErrorCode.USERNAME_EXISTS);
            }
            if (existsByEmail(cmd.getEmail())) {
                throw new ServiceException(SystemErrorCode.EMAIL_ALREADY_USED);
            }
            throw new ServiceException("账号创建失败：数据冲突", e);
        }
    }

    @Override
    public boolean checkPassword(Long userId, String rawPassword) {
        if (userId == null || rawPassword == null) {
            return false;
        }
        SysUser user = userService.getById(userId);
        if (user == null || !StringUtils.hasText(user.getPassword())) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, user.getPassword());
        } catch (RuntimeException e) {
            // Hutool BCrypt 对格式错误、损坏的哈希会抛出运行时异常；契约统一降级为校验失败。
            return false;
        }
    }

    private SysUser findByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return userService.getOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getEmail, email), false);
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
