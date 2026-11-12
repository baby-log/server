package com.babylog.biz.auth.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

/**
 * {@link SysUserAccountDao} 的绑定顺序测试。
 *
 * <p>存在理由很具体：注册链路的单测 Mock 掉了 DAO，永远发现不了「列顺序与参数顺序错位」——
 * 这个问题在 dev 端点联调时才暴露（注册成功、登录恒 2001，因为 password 列里存的是 email）。
 * 所以这里直接对着 JdbcTemplate 的入参断言顺序，把契约钉死。
 */
@ExtendWith(MockitoExtension.class)
class SysUserAccountDaoTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void insert_bindsPasswordBeforeEmail() {
        SysUserAccountDao dao = new SysUserAccountDao(jdbcTemplate);

        dao.insert("mom", "妈妈", "mom@example.com", "$2a$10$fakehash");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), args.capture());

        // 列顺序：id, username, nickname, password, email, ...
        assertThat(sql.getValue()).contains("(id, username, nickname, password, email,");
        Object[] values = args.getValue();
        assertThat(values).hasSize(7);
        assertThat(values[1]).isEqualTo("mom");
        assertThat(values[2]).isEqualTo("妈妈");
        assertThat(values[3]).isEqualTo("$2a$10$fakehash");   // password 位
        assertThat(values[4]).isEqualTo("mom@example.com");    // email 位
    }

    @Test
    void findByEmail_returnsEmptyWhenNoRow() {
        org.mockito.Mockito.when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(java.util.List.of());

        assertThat(new SysUserAccountDao(jdbcTemplate).findByEmail("ghost@example.com")).isEmpty();
    }
}
