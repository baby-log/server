package com.babylog.biz.sync.store;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.babylog.biz.sync.entity.BlProductSyncView;
import com.babylog.biz.sync.mapper.BlProductSyncViewMapper;
import com.babylog.biz.sync.support.SyncTable;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.DataIntegrityViolationException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M67-H · products.sort_order 跨版本兼容（真库集成测试）。
 *
 * <p>背景：{@code bl_products.sort_order} 是 V2.2.17 新增的 {@code INT NOT NULL DEFAULT 0} 列。
 * M67 之前的 App 版本，products 载荷里<b>没有 sortOrder 这个键</b>。同步存储对「缺键」的既有
 * 契约是「按 null 写入」（对齐旧 ODKU 显式绑定 NULL），于是：
 * <ul>
 *   <li>更新路径 → {@code SET sort_order = NULL} 撞 NOT NULL → 整条 8342 拒收，
 *       表现为老用户「改了产品名没生效」；</li>
 *   <li>插入路径 → MyBatis-Plus 的 NOT_NULL 策略省略该列，落列 DEFAULT 0（侥幸通过）。</li>
 * </ul>
 *
 * <p>修法不是「缺键补 0」（会把后台设的排序静默清零），而是在注册表上显式登记
 * {@code preserveOnAbsentProperties = {sortOrder}}：缺键时既不写实体、也不进 SET。
 * 本类在 H2 上钉死四条行为：改名保留排序、首插落默认、显式 null 仍被库拒、
 * 未登记列维持「缺键置 NULL」的原有契约（防止修法扩散成全局语义变更）。
 */
class SyncStoreProductCompatH2Test {

    private DataSource dataSource;
    private SqlSessionTemplate template;
    private GenericSyncTableHandler<BlProductSyncView> handler;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:productcompat" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        this.dataSource = ds;

        MybatisConfiguration configuration = new MybatisConfiguration();
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        configuration.addInterceptor(interceptor);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(ds);
        factoryBean.setConfiguration(configuration);
        factoryBean.afterPropertiesSet();
        org.apache.ibatis.session.SqlSessionFactory factory = factoryBean.getObject();
        factory.getConfiguration().addMapper(BlProductSyncViewMapper.class);
        this.template = new SqlSessionTemplate(factory);

        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            // 镜像 V2.2.4 bl_products + V2.2.7 user_id + V2.2.17 sort_order
            st.execute("CREATE TABLE bl_products (" +
                    "id BIGINT NOT NULL PRIMARY KEY," +
                    "category VARCHAR(32) NOT NULL," +
                    "brand VARCHAR(100) NOT NULL," +
                    "name VARCHAR(50) NOT NULL," +
                    "spec VARCHAR(100)," +
                    "default_dose VARCHAR(100)," +
                    // 被修的那一列：MySQL 里是 INT(11) NOT NULL DEFAULT 0
                    "sort_order INT NOT NULL DEFAULT 0," +
                    "last_used_at BIGINT," +
                    "enabled TINYINT NOT NULL DEFAULT 1," +
                    "is_builtin TINYINT NOT NULL DEFAULT 0," +
                    "note VARCHAR(500)," +
                    "icon_path VARCHAR(255)," +
                    "created_at BIGINT," +
                    "updated_at BIGINT," +
                    "device_id VARCHAR(64)," +
                    "user_id BIGINT NOT NULL," +
                    "version BIGINT NOT NULL," +
                    "server_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "create_by BIGINT," +
                    "create_time TIMESTAMP," +
                    "update_by BIGINT," +
                    "update_time TIMESTAMP," +
                    "deleted TINYINT NOT NULL DEFAULT 0)");
        }

        BlProductSyncViewMapper mapper = template.getMapper(BlProductSyncViewMapper.class);
        this.handler = new GenericSyncTableHandler<>(SyncTable.PRODUCTS, mapper,
                BlProductSyncView.class, SyncViewRegistry.syncSpec(SyncTable.PRODUCTS));
    }

    @Test
    void oldClientPayloadWithoutSortOrderKeepsServerSortOrder() throws Exception {
        long id = 880501L;
        // 新客户端 / 后台先落一行，排序 50
        Map<String, Object> fresh = legacyShapePayload(id, 1L, "爱他美卓萃");
        fresh.put("sortOrder", 50);
        handler.upsert(fresh, 100L);
        assertThat(readInt(id, "sort_order")).isEqualTo(50);

        // 老版本 App 改名（载荷里根本没有 sortOrder 键），version +1 覆盖
        handler.upsert(legacyShapePayload(id, 2L, "爱他美卓萃 新版"), 100L);

        assertThat(readString(id, "name")).isEqualTo("爱他美卓萃 新版");
        // 关键断言：排序保留，不被清 0，也没有整条拒收
        assertThat(readInt(id, "sort_order")).isEqualTo(50);
        assertThat(readLong(id, "version")).isEqualTo(2L);
    }

    @Test
    void oldClientInsertWithoutSortOrderFallsBackToColumnDefault() throws Exception {
        long id = 880502L;
        handler.upsert(legacyShapePayload(id, 1L, "自制米糊"), 100L);

        assertThat(readString(id, "name")).isEqualTo("自制米糊");
        // 插入路径靠列 DEFAULT 0：老客户端不参与排序，正是期望语义
        assertThat(readInt(id, "sort_order")).isZero();
        assertThat(readLong(id, "user_id")).isEqualTo(100L);
    }

    @Test
    void explicitNullSortOrderIsStillRejectedInsteadOfSilentlyKept() throws Exception {
        long id = 880503L;
        handler.upsert(legacyShapePayload(id, 1L, "旧名"), 100L);

        // 显式 null（键存在、值为 null）与「缺键」不同：不放宽，仍由库拒绝 → 上层 8342
        Map<String, Object> explicitNull = legacyShapePayload(id, 2L, "改名");
        explicitNull.put("sortOrder", null);

        assertThatThrownBy(() -> handler.upsert(explicitNull, 100L))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 整条被拒：名称也没落库
        assertThat(readString(id, "name")).isEqualTo("旧名");
        assertThat(readInt(id, "sort_order")).isZero();
    }

    @Test
    void nonRegisteredMissingKeysStillWriteNullAsBefore() throws Exception {
        long id = 880504L;
        Map<String, Object> withSpec = legacyShapePayload(id, 1L, "米糊");
        withSpec.put("spec", "500g");
        handler.upsert(withSpec, 100L);
        assertThat(readString(id, "spec")).isEqualTo("500g");

        // spec 未登记 preserveOnAbsent → 维持既有契约：缺键按 null 写入
        Map<String, Object> specAbsent = legacyShapePayload(id, 2L, "米糊");
        specAbsent.remove("spec");
        handler.upsert(specAbsent, 100L);
        assertThat(readString(id, "spec")).isNull();
    }

    /** M67 之前 App 的真实 products 载荷形态：有全部老列，唯独没有 sortOrder */
    private static Map<String, Object> legacyShapePayload(long id, long version, String name) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", id);
        payload.put("version", version);
        payload.put("category", "FORMULA");
        payload.put("brand", "爱他美");
        payload.put("name", name);
        payload.put("spec", "800g");
        payload.put("defaultDose", "30ml/次");
        payload.put("enabled", 1);
        payload.put("isBuiltin", 0);
        payload.put("createdAt", 1_700_000_000_000L);
        payload.put("updatedAt", 1_700_000_000_000L);
        payload.put("deleted", 0);
        return payload;
    }

    private Map<String, Object> readRow(long id) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM bl_products WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("行 %s 应存在", id).isTrue();
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    row.put(rs.getMetaData().getColumnName(i).toLowerCase(), rs.getObject(i));
                }
                return row;
            }
        }
    }

    private int readInt(long id, String column) throws Exception {
        return ((Number) readRow(id).get(column)).intValue();
    }

    private long readLong(long id, String column) throws Exception {
        return ((Number) readRow(id).get(column)).longValue();
    }

    private String readString(long id, String column) throws Exception {
        Object value = readRow(id).get(column);
        return value == null ? null : value.toString();
    }
}
