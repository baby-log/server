package com.babylog.biz.sync.store;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.babylog.biz.sync.entity.BlFeedingSyncView;
import com.babylog.biz.sync.mapper.BlAppVersionSyncViewMapper;
import com.babylog.biz.sync.mapper.BlFeedingSyncViewMapper;
import com.babylog.biz.sync.support.BaseDataTable;
import com.babylog.biz.sync.support.SyncCursor;
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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 同步存储类型化实现的<b>真库</b>集成测试（PivotOS 严格整改 S3）。
 *
 * <p>不用 Mock 证明并发正确——H2 内存库 + 真实 MyBatis-Plus 链路（分页插件、
 * 视图实体、LambdaWrapper SQL 生成全走真身），且用 {@link SqlSessionTemplate}
 * 拿到与生产一致的 Mapper 代理（线程安全 + Spring DAO 异常翻译）。
 * 多线程竞争同一条记录的 upsert，断言 last-write-wins 收敛、无重复行、
 * 归属不被改写、墓碑随行下发、双段游标分页稳定、M37 谓词生效。
 *
 * <p>覆盖：唯一键竞争（DuplicateKeyException 回退）、版本门禁、软删除墓碑、
 * amountMl=null 显式落 NULL、pauseEvents 透传、NOT NULL 拒收语义、
 * base 通道 PUBLISHED 谓词 + 墓碑下发。
 */
class SyncStoreH2Test {

    private DataSource dataSource;
    private SqlSessionFactoryHolder factoryHolder;
    private GenericSyncTableHandler<BlFeedingSyncView> handler;

    /** 每个测试一条全新内存库；工厂与模板按测试独立 */
    private static final class SqlSessionFactoryHolder {
        private final org.apache.ibatis.session.SqlSessionFactory factory;
        private final SqlSessionTemplate template;

        SqlSessionFactoryHolder(DataSource ds, MybatisConfiguration configuration) throws Exception {
            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(ds);
            factoryBean.setConfiguration(configuration);
            factoryBean.afterPropertiesSet();
            this.factory = factoryBean.getObject();
            this.template = new SqlSessionTemplate(this.factory);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:syncstore" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        this.dataSource = ds;

        MybatisConfiguration configuration = new MybatisConfiguration();
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        configuration.addInterceptor(interceptor);

        this.factoryHolder = new SqlSessionFactoryHolder(ds, configuration);
        factoryHolder.factory.getConfiguration().addMapper(BlFeedingSyncViewMapper.class);
        factoryHolder.factory.getConfiguration().addMapper(BlAppVersionSyncViewMapper.class);

        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE bl_feedings (" +
                    "id BIGINT NOT NULL PRIMARY KEY," +
                    "child_id BIGINT NOT NULL," +
                    "type VARCHAR(32) NOT NULL," +
                    "amount_ml INT," +
                    "amount_unit VARCHAR(32)," +
                    "duration_s INT," +
                    "product_id BIGINT," +
                    "brand_snapshot VARCHAR(100)," +
                    "spec_snapshot VARCHAR(100)," +
                    "note VARCHAR(500)," +
                    "started_at BIGINT," +
                    // BlFeeding.createdBy 遮蔽 BaseDO.createBy（String 业务口径），测试 DDL 如实镜像
                    "created_by VARCHAR(50)," +
                    "created_at BIGINT," +
                    "updated_at BIGINT," +
                    "device_id VARCHAR(64)," +
                    "in_progress TINYINT NOT NULL DEFAULT 0," +
                    "ended_at BIGINT," +
                    "total_seconds INT," +
                    "active_seconds INT," +
                    "pause_events TEXT," +
                    "user_id BIGINT NOT NULL," +
                    "version BIGINT NOT NULL," +
                    "server_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "create_by VARCHAR(50)," +
                    "create_time TIMESTAMP," +
                    "update_by BIGINT," +
                    "update_time TIMESTAMP," +
                    "deleted TINYINT NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE bl_app_version (" +
                    "id BIGINT NOT NULL PRIMARY KEY," +
                    "platform VARCHAR(32)," +
                    "version_code INT," +
                    "version_name VARCHAR(64)," +
                    "force_update TINYINT," +
                    "download_url VARCHAR(255)," +
                    "notice TEXT," +
                    "status VARCHAR(32)," +
                    "server_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "create_by BIGINT," +
                    "create_time TIMESTAMP," +
                    "update_by BIGINT," +
                    "update_time TIMESTAMP," +
                    "deleted TINYINT NOT NULL DEFAULT 0)");
        }

        this.handler = newFeedingHandler();
    }

    /** 每个调用方拿一条独立线程安全的 Mapper 代理（与生产的注入语义一致） */
    private GenericSyncTableHandler<BlFeedingSyncView> newFeedingHandler() {
        BlFeedingSyncViewMapper mapper = factoryHolder.template.getMapper(BlFeedingSyncViewMapper.class);
        return new GenericSyncTableHandler<>(SyncTable.FEEDINGS, mapper,
                BlFeedingSyncView.class, SyncViewRegistry.syncSpec(SyncTable.FEEDINGS));
    }

    // ------------------------------------------------------------------
    // 载荷工厂与原始读取
    // ------------------------------------------------------------------

    private static Map<String, Object> feedingPayload(long id, long version, Integer amountMl) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", id);
        payload.put("version", version);
        payload.put("childId", 7001L);
        payload.put("type", "BOTTLE");
        payload.put("amountMl", amountMl);
        payload.put("startedAt", 1_700_000_000_000L);
        payload.put("createdAt", 1_700_000_000_000L);
        payload.put("updatedAt", 1_700_000_000_000L);
        payload.put("inProgress", 0);
        return payload;
    }

    private Map<String, Object> readRow(long id) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM bl_feedings WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("行 %s 应存在", id).isTrue();
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    Object value = rs.getObject(i);
                    // H2 的 TEXT 列经 getObject 返回 Clob，归一成字符串再断言
                    if (value instanceof java.sql.Clob clob) {
                        value = clob.getSubString(1, (int) clob.length());
                    }
                    row.put(rs.getMetaData().getColumnName(i).toLowerCase(), value);
                }
                return row;
            }
        }
    }

    private Map<String, Object> readRowUnchecked(long id) {
        try {
            return readRow(id);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private int countRows() throws Exception {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM bl_feedings")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private long readUncheckedCount(long id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM bl_feedings WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // ------------------------------------------------------------------
    // 并发：同 id 多版本竞争，必须收敛到最高版本，单行，归属不被改写
    // ------------------------------------------------------------------

    @Test
    void concurrentUpsertsOfSameIdConvergeToHighestVersion() throws Exception {
        long id = 880001L;
        int threads = 8;
        int versionsPerThread = 6;
        List<Long> allVersions = new ArrayList<>();
        for (long v = 1; v <= threads * versionsPerThread; v++) {
            allVersions.add(v);
        }
        Collections.shuffle(allVersions);
        AtomicInteger cursor = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                GenericSyncTableHandler<BlFeedingSyncView> threadHandler = newFeedingHandler();
                for (int i = 0; i < versionsPerThread; i++) {
                    long v = allVersions.get(cursor.getAndIncrement());
                    // 内容随版本变化：最终行必须来自最高版本那条载荷
                    threadHandler.upsert(feedingPayload(id, v, (int) (1000 + v)), 100L);
                }
                return null;
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        long maxVersion = allVersions.stream().mapToLong(Long::longValue).max().orElseThrow();
        Map<String, Object> row = readRow(id);
        assertThat(countRows()).as("同 id 并发首插不得产生重复行").isEqualTo(1);
        assertThat(row.get("version")).isEqualTo(maxVersion);
        assertThat(((Number) row.get("amount_ml")).longValue()).isEqualTo(1000 + maxVersion);
        assertThat(row.get("user_id")).as("归属 = 首个成功插入者，后续覆盖不改写归属").isEqualTo(100L);
    }

    @Test
    void concurrentCrossUserPushesPreserveFirstWriterOwnership() throws Exception {
        long id = 880002L;
        // 用户 200 先落库 v1；随后用户 300 并发展开 v2..v9 的更新竞争
        handler.upsert(feedingPayload(id, 1L, 100), 200L);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            long v = 2 + t;
            futures.add(pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                newFeedingHandler().upsert(feedingPayload(id, v, (int) (2000 + v)), 300L);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        Map<String, Object> row = readRow(id);
        assertThat(row.get("version")).isEqualTo(9L);
        assertThat(row.get("user_id")).as("更新路径绝不改写归属列").isEqualTo(200L);
        assertThat(((Number) row.get("amount_ml")).longValue()).isEqualTo(2009L);
        // 审计：update_by 记最后一次成功写入者，create_by 仍是首写者
        assertThat(((Number) row.get("update_by")).longValue()).isEqualTo(300L);
    }

    // ------------------------------------------------------------------
    // 版本门禁：旧版本重推不得回退
    // ------------------------------------------------------------------

    @Test
    void stringIdAndVersionFromJsonPayloadAreAccepted() throws Exception {
        // dev 冒烟实证：真实 JSON 链路中 id/version 以字符串形式到达泛型 Map，
        // 存储层门禁必须与服务层 asLong 同口径容忍字符串数字（曾仅认 Number → 1500）
        long id = 880099L;
        Map<String, Object> payload = feedingPayload(id, 1L, 100);
        payload.put("id", String.valueOf(id));
        payload.put("version", "1");
        handler.upsert(payload, 100L);

        Map<String, Object> row = readRow(id);
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(1L);
        assertThat(countRows()).isEqualTo(1);
    }

    @Test
    void staleVersionPushNeverOverwrites() throws Exception {
        long id = 880003L;
        handler.upsert(feedingPayload(id, 5L, 500), 100L);
        // 存储层直接收到旧版本（服务层已跳过；这里验证数据库侧门禁是第二道防线）
        handler.upsert(feedingPayload(id, 3L, 300), 100L);

        Map<String, Object> row = readRow(id);
        assertThat(row.get("version")).isEqualTo(5L);
        assertThat(((Number) row.get("amount_ml")).longValue()).isEqualTo(500L);
        assertThat(countRows()).isEqualTo(1);
    }

    @Test
    void updatePathRefreshesContentAndKeepsCreateAudit() throws Exception {
        long id = 880004L;
        handler.upsert(feedingPayload(id, 1L, 100), 100L);
        Object firstCreateTime = readRow(id).get("create_time");

        handler.upsert(feedingPayload(id, 2L, 200), 100L);

        Map<String, Object> row = readRow(id);
        assertThat(row.get("version")).isEqualTo(2L);
        assertThat(((Number) row.get("amount_ml")).longValue()).isEqualTo(200L);
        assertThat(row.get("create_time")).as("更新路径不触碰 create_time").isEqualTo(firstCreateTime);
        assertThat(row.get("create_by")).as("feedings create_by 是 String 业务口径遮蔽列").isEqualTo("100");
    }

    // ------------------------------------------------------------------
    // 软删除墓碑：deleted=1 必须可读、可再覆盖、可下发
    // ------------------------------------------------------------------

    @Test
    void tombstoneIsReadableOverwriteableAndPulled() {
        long id = 880005L;
        handler.upsert(feedingPayload(id, 1L, 100), 100L);

        Map<String, Object> tombstone = feedingPayload(id, 2L, null);
        tombstone.put("deleted", 1);
        handler.upsert(tombstone, 100L);

        // 1) 库内行就是墓碑（selectExisting 对 deleted 全量可见）
        Map<Long, SyncRecordStore.Existing> existing = handler.selectExisting(List.of(id));
        assertThat(existing).containsKey(id);
        assertThat(existing.get(id).version()).isEqualTo(2L);

        // 2) 墓碑可被更高版本复活（覆盖不区分 deleted 取值）
        Map<String, Object> revive = feedingPayload(id, 3L, 150);
        revive.put("deleted", 0);
        handler.upsert(revive, 100L);
        assertThat(((Number) readRowUnchecked(id).get("deleted")).intValue()).isEqualTo(0);

        // 3) 再立墓碑并验证拉取通道下发 deleted=1（客户端要应用删除）
        Map<String, Object> tombstone2 = feedingPayload(id, 4L, null);
        tombstone2.put("deleted", 1);
        handler.upsert(tombstone2, 100L);
        LocalDateTime since = SyncCursor.toLocalDateTime(0L);
        List<SyncRecordStore.Row> rows = handler.selectChanges(List.of(100L), since, 0L, 100);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).data()).containsEntry("id", id).containsEntry("deleted", 1);
        assertThat(rows.get(0).cursorMillis()).isPositive();
    }

    // ------------------------------------------------------------------
    // 双段游标分页：同毫秒多行靠 id tiebreak，翻页不重不漏
    // ------------------------------------------------------------------

    @Test
    void dualCursorPaginationIsStableAcrossSameMillisecondRows() throws Exception {
        int total = 7;
        for (long i = 1; i <= total; i++) {
            handler.upsert(feedingPayload(880100L + i, 1L, (int) i), 100L);
        }
        // 强制同一游标毫秒：模拟「一次 push 的 N 行共用一个 server_updated_at」
        LocalDateTime sameInstant = LocalDateTime.now().minusMinutes(1).withNano(123_000_000);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE bl_feedings SET server_updated_at = ?")) {
            ps.setTimestamp(1, Timestamp.valueOf(sameInstant));
            ps.executeUpdate();
        }

        List<Long> pulledInOrder = new ArrayList<>();
        LocalDateTime since = SyncCursor.toLocalDateTime(0L);
        long sinceId = 0L;
        Set<Long> seen = new HashSet<>();
        for (int page = 0; page < 5; page++) {
            List<SyncRecordStore.Row> rows = handler.selectChanges(List.of(100L), since, sinceId, 3);
            if (rows.isEmpty()) {
                break;
            }
            for (SyncRecordStore.Row row : rows) {
                assertThat(seen.add(row.id())).as("翻页不得重复：id=%s", row.id()).isTrue();
                pulledInOrder.add(row.id());
            }
            SyncRecordStore.Row last = rows.get(rows.size() - 1);
            since = SyncCursor.toLocalDateTime(last.cursorMillis());
            sinceId = last.id();
            if (rows.size() < 3) {
                break;
            }
        }
        assertThat(pulledInOrder).as("同毫秒 7 行必须不重不漏按 id 升序拉完")
                .containsExactlyElementsOf(LongStream.rangeClosed(880101L, 880107L).boxed().toList());
    }

    @Test
    void pullPredicateExcludesInProgressHalfDoneRows() {
        // M37：进行中计时半成品不下发（服务端兜底谓词 in_progress = 0）
        handler.upsert(feedingPayload(880200L, 1L, 100), 100L);
        Map<String, Object> halfDone = feedingPayload(880201L, 1L, 50);
        halfDone.put("inProgress", 1);
        handler.upsert(halfDone, 100L);

        LocalDateTime since = SyncCursor.toLocalDateTime(0L);
        List<SyncRecordStore.Row> rows = handler.selectChanges(List.of(100L), since, 0L, 100);
        assertThat(rows.stream().map(SyncRecordStore.Row::id)).containsExactly(880200L);
    }

    // ------------------------------------------------------------------
    // 载荷空值语义：amountMl=null 显式落 NULL、pauseEvents 透传、NOT NULL 拒收
    // ------------------------------------------------------------------

    @Test
    void nullAmountMlAndPauseEventsSurviveTheUpsertChain() {
        long id = 880300L;
        Map<String, Object> payload = feedingPayload(id, 1L, null);
        payload.put("pauseEvents", "[{\"t\":\"START\",\"at\":1700000000000}]");
        handler.upsert(payload, 100L);

        Map<String, Object> row = readRowUnchecked(id);
        assertThat(row.get("amount_ml")).as("amountMl=null 必须显式落 NULL（母乳/水无奶量）").isNull();
        assertThat(row.get("pause_events"))
                .isEqualTo("[{\"t\":\"START\",\"at\":1700000000000}]");

        LocalDateTime since = SyncCursor.toLocalDateTime(0L);
        List<SyncRecordStore.Row> rows = handler.selectChanges(List.of(100L), since, 0L, 100);
        assertThat(rows).hasSize(1);
        // 下行载荷的键与值必须原样（amountMl 键在、值为 null）
        assertThat(rows.get(0).data()).containsKey("amountMl").containsEntry("id", id);
        assertThat(rows.get(0).data().get("amountMl")).isNull();
        assertThat(rows.get(0).data().get("pauseEvents"))
                .isEqualTo("[{\"t\":\"START\",\"at\":1700000000000}]");
    }

    @Test
    void explicitNullOnNotNullFlagColumnIsRejectedLikeTheDatabaseDid() {
        Map<String, Object> payload = feedingPayload(880301L, 1L, 100);
        payload.put("inProgress", null);

        assertThatThrownBy(() -> handler.upsert(payload, 100L))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("inProgress");
        assertThat(readUncheckedCount(880301L)).isZero();
    }

    @Test
    void missingPayloadFieldsGetNullNotDefaults() throws Exception {
        // 旧 ODKU 对缺失的可空载荷列显式绑定 NULL；类型化 insert 省略该列，
        // 列默认值必须同为 NULL（迁移脚本里这些列均为 DEFAULT NULL）——钉死这个等价性
        Map<String, Object> sparse = new HashMap<>();
        sparse.put("id", 880302L);
        sparse.put("version", 1L);
        sparse.put("childId", 7001L);
        sparse.put("type", "BOTTLE");
        sparse.put("startedAt", 1_700_000_000_000L);
        sparse.put("inProgress", 0);
        handler.upsert(sparse, 100L);

        Map<String, Object> row = readRow(880302L);
        assertThat(row.get("amount_ml")).isNull();
        assertThat(row.get("note")).isNull();
        assertThat(row.get("pause_events")).isNull();
        // deleted 缺省 0（显式兜底，与旧实现同口径）
        assertThat(((Number) row.get("deleted")).intValue()).isEqualTo(0);
    }

    // ------------------------------------------------------------------
    // base 通道：PUBLISHED 谓词 + 软删除事实下发
    // ------------------------------------------------------------------

    @Test
    void baseChannelFiltersDraftButKeepsTombstones() {
        BlAppVersionSyncViewMapper baseMapper = factoryHolder.template.getMapper(BlAppVersionSyncViewMapper.class);
        GenericBaseTableHandler<com.babylog.biz.sync.entity.BlAppVersionSyncView> baseHandler =
                new GenericBaseTableHandler<>(BaseDataTable.APP_VERSIONS, baseMapper,
                        com.babylog.biz.sync.entity.BlAppVersionSyncView.class,
                        SyncViewRegistry.baseSpec(BaseDataTable.APP_VERSIONS));

        insertVersionRow(881001L, "PUBLISHED", 0);
        insertVersionRow(881002L, "DRAFT", 0);
        insertVersionRow(881003L, "PUBLISHED", 1); // 已下架（软删除）的已发布版本仍要下发墓碑

        LocalDateTime since = SyncCursor.toLocalDateTime(0L);
        List<SyncRecordStore.BaseRow> rows = baseHandler.selectChanges(since, 0L, 100);
        assertThat(rows.stream().map(SyncRecordStore.BaseRow::id))
                .containsExactlyInAnyOrder(881001L, 881003L);
        assertThat(rows.stream().filter(r -> r.id() == 881003L).findFirst().orElseThrow().data())
                .containsEntry("deleted", 1);
        assertThat(rows.stream().filter(r -> r.id() == 881001L).findFirst().orElseThrow().data())
                .containsEntry("status", "PUBLISHED");
    }

    private void insertVersionRow(long id, String status, int deleted) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO bl_app_version (id, platform, version_code, version_name, status, deleted) " +
                             "VALUES (?, 'ANDROID', 100, '2.0.0', ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, status);
            ps.setInt(3, deleted);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
