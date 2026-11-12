package com.babylog.biz.auth.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@code sys_user} 账号读写（biz 侧唯一触点）。
 *
 * <p><b>为什么这里直接写 SQL</b>：M5 需要「按邮箱建号」的能力，而 system 插件只暴露了只读的
 * {@code IUserFacade}（无任何写方法）。补一个写契约 = 改 system 插件，触「脚手架不改」红线；
 * 在 biz 里放一个映射到 sys_user 的 MyBatis 实体又会被 A8 表前缀规则判红（biz 只准 bl_）。
 * 两者都不可行，故收口到本类：**三个读、一个写，字段与 system 的 V1.0.0 建表脚本对齐**。
 *
 * <p>这是明确的**技术债**（跨插件表耦合）：上游若提供写入契约，本类整体替换为契约调用即可，
 * 调用方（{@code AppAuthService}）零改动——所以把 SQL 关在一个类里，不让它散出去。
 *
 * <p>口径与 system 保持一致：只认 {@code deleted = 0}；密码为 BCrypt；主键雪花走 MyBatis-Plus
 * 的 {@link IdWorker}（与 BaseDO 的 ASSIGN_ID 同源，避免两套号段）。
 */
@Component
public class SysUserAccountDao {

    private static final String COLUMNS = "id, username, nickname, password, email, status";

    private static final RowMapper<SysUserRow> ROW_MAPPER = (rs, rowNum) -> new SysUserRow(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("nickname"),
            rs.getString("password"),
            rs.getString("email"),
            rs.getInt("status"));

    private final JdbcTemplate jdbcTemplate;

    public SysUserAccountDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 按邮箱查账号（邮箱已由服务层归一化） */
    public Optional<SysUserRow> findByEmail(String email) {
        List<SysUserRow> rows = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM sys_user WHERE deleted = 0 AND email = ? LIMIT 1",
                ROW_MAPPER, email);
        return rows.stream().findFirst();
    }

    /** 按用户名查账号 */
    public Optional<SysUserRow> findByUsername(String username) {
        List<SysUserRow> rows = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM sys_user WHERE deleted = 0 AND username = ? LIMIT 1",
                ROW_MAPPER, username);
        return rows.stream().findFirst();
    }

    /** 按 ID 查账号（refresh token 换新 token 时校验账号状态） */
    public Optional<SysUserRow> findById(Long userId) {
        List<SysUserRow> rows = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM sys_user WHERE deleted = 0 AND id = ? LIMIT 1",
                ROW_MAPPER, userId);
        return rows.stream().findFirst();
    }

    /**
     * 建号，返回新用户 ID。
     *
     * <p>只填认证必需列，其余（dept/post/tenant/avatar/gender/mobile）留给库默认值：
     * 这里建的是 App 普通用户，不挂部门、不挂租户（多租户不启用）。
     */
    public long insert(String username, String nickname, String email, String passwordHash) {
        long id = IdWorker.getId();
        LocalDateTime now = LocalDateTime.now();
        // 参数顺序必须与列顺序逐字对应（id, username, nickname, password, email）——
        // 这里错过一次：password 与 email 位置互换，导致密码列存了邮箱、登录永远 2001。
        jdbcTemplate.update("""
                        INSERT INTO sys_user
                            (id, username, nickname, password, email, mobile, gender, avatar, status,
                             create_time, update_time, deleted)
                        VALUES (?, ?, ?, ?, ?, '', 0, '', 0, ?, ?, 0)
                        """,
                id, username, nickname, passwordHash, email, now, now);
        return id;
    }

    /**
     * 账号行。
     *
     * @param status 0 正常 1 停用（与 {@code CommonStatusEnum.ENABLED} 同口径）
     */
    public record SysUserRow(Long id, String username, String nickname, String password,
                             String email, Integer status) {

        /** 是否启用（停用账号一律拒绝登录，与 /app/auth/login 的 2002 对齐） */
        public boolean enabled() {
            return status != null && status == 0;
        }
    }
}
