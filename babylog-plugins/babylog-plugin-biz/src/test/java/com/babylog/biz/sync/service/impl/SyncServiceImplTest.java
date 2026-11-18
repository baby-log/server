package com.babylog.biz.sync.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.sync.dto.SyncPushCmd;
import com.babylog.biz.sync.store.SyncRecordStore;
import com.babylog.biz.sync.support.SyncAccessScope;
import com.babylog.biz.sync.support.SyncAccessScopeResolver;
import com.babylog.biz.sync.support.SyncCursor;
import com.babylog.biz.sync.support.SyncPermissionGuard;
import com.babylog.biz.sync.support.SyncTable;
import com.babylog.biz.sync.vo.SyncChangesVO;
import com.babylog.biz.sync.vo.SyncPushRejectVO;
import com.babylog.biz.sync.vo.SyncPushResultVO;
import com.babylog.common.core.enums.error.ErrorCode;
import com.babylog.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 同步编排单测：游标边界、版本门禁、归属校验、批量上限、部分成功语义。
 *
 * <p>存储层与权限钩子都是 Mock——本类只验「裁决逻辑」，SQL 正确性由 dev curl 联调兜底。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SyncServiceImplTest {

    private static final long USER_ID = 1001L;
    private static final long OTHER_USER_ID = 2002L;

    @Mock
    private SyncRecordStore store;
    @Mock
    private SyncPermissionGuard permissionGuard;
    @Mock
    private SyncAccessScopeResolver accessScopeResolver;

    private SyncServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SyncServiceImpl(store, permissionGuard, accessScopeResolver, noopTransactionManager());
        // M15 回归锁的基线场景：无家庭用户 = 可见集合只有自己 + 读写全开（行为与 V2.0 零变化）
        org.mockito.Mockito.when(accessScopeResolver.resolveAccess(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(inv -> SyncAccessScope.singleUser(inv.getArgument(0)));
    }

    /**
     * 无事务的 {@link PlatformTransactionManager}：单测里 {@code push} 的事务模板直接同步执行回调，
     * 不做任何 begin/commit——本类全部走 Mock 存储，本来就没有真事务可开。
     */
    private static org.springframework.transaction.PlatformTransactionManager noopTransactionManager() {
        return new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
                // no-op
            }

            @Override
            protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {
                // no-op
            }

            @Override
            protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {
                // no-op
            }
        };
    }

    // ==================================================================
    // 死锁重试（M52）
    // ==================================================================

    @Test
    void push_retriesOnce_whenFirstAttemptDeadlocks() {
        org.mockito.Mockito.when(store.selectExisting(eq(SyncTable.SLEEPS), any()))
                .thenThrow(new org.springframework.dao.DeadlockLoserDataAccessException("死锁", null))
                .thenReturn(Map.of());
        SyncPushCmd cmd = cmd(SyncTable.SLEEPS.apiName(), List.of(payload(1L, 1)));

        SyncPushResultVO result = service.push(USER_ID, cmd);

        assertThat(result.getUpserted()).isEqualTo(1);
        verify(store, times(2)).selectExisting(eq(SyncTable.SLEEPS), any());
    }

    @Test
    void push_doesNotRetry_whenFailureIsNotLockRelated() {
        org.mockito.Mockito.when(store.selectExisting(eq(SyncTable.SLEEPS), any()))
                .thenThrow(new IllegalStateException("库连不上不是死锁，不该重试"));
        SyncPushCmd cmd = cmd(SyncTable.SLEEPS.apiName(), List.of(payload(1L, 1)));

        assertThatThrownBy(() -> service.push(USER_ID, cmd))
                .isInstanceOf(IllegalStateException.class);
        verify(store, times(1)).selectExisting(eq(SyncTable.SLEEPS), any());
    }

    @Test
    void push_givesUpAfterMaxAttempts_whenDeadlockPersists() {
        org.mockito.Mockito.when(store.selectExisting(eq(SyncTable.SLEEPS), any()))
                .thenThrow(new org.springframework.dao.DeadlockLoserDataAccessException("死锁", null));
        SyncPushCmd cmd = cmd(SyncTable.SLEEPS.apiName(), List.of(payload(1L, 1)));

        assertThatThrownBy(() -> service.push(USER_ID, cmd))
                .isInstanceOf(org.springframework.dao.DeadlockLoserDataAccessException.class);
        verify(store, times(3)).selectExisting(eq(SyncTable.SLEEPS), any());
    }

    // ==================================================================
    // 拉取
    // ==================================================================

    @Test
    void pull_withoutCursorQueriesEveryTableAndEchoesCursor() {
        SyncChangesVO vo = service.pull(USER_ID, 0L, null, null, null);

        assertThat(vo.getChanges()).isEmpty();
        assertThat(vo.getCount()).isZero();
        assertThat(vo.isHasMore()).isFalse();
        // 没有新数据时不能把游标清成 0，否则客户端下次变成全量重拉
        assertThat(vo.getNextSince()).isZero();
        assertThat(vo.getNextSinceId()).isZero();
        verify(store, times(18)).selectChanges(any(), eq(List.of(USER_ID)), any(), eq(0L),
                eq(SyncServiceImpl.DEFAULT_PULL_LIMIT));
    }

    @Test
    void pull_sameMillisecondRowsAreOrderedByIdAndCursorTakesLastOne() {
        // 同一毫秒两条（MySQL 的 NOW(3) 在一条语句内是常量，一次 push 的多行会共用毫秒值）
        storeReturns(SyncTable.SLEEPS,
                row(SyncTable.SLEEPS, 300L, 9_000L),
                row(SyncTable.SLEEPS, 200L, 9_000L));

        SyncChangesVO vo = service.pull(USER_ID, 8_000L, 0L, "CARE", 10);

        assertThat(vo.getCount()).isEqualTo(2);
        assertThat(vo.isHasMore()).isFalse();
        assertThat(vo.getNextSince()).isEqualTo(9_000L);
        // 游标第二段取的是本页最后一条（id 大者），下一页从它之后继续
        assertThat(vo.getNextSinceId()).isEqualTo(300L);
        assertThat(vo.getChanges().get("sleeps")).extracting(row -> row.get("id"))
                .containsExactly(200L, 300L);
    }

    @Test
    void pull_fullPageReportsHasMoreAndNextPageUsesBothCursorSegments() {
        List<SyncRecordStore.Row> twelve = IntStream.rangeClosed(1, 12)
                .mapToObj(i -> row(SyncTable.DIAPERS, i, 5_000L))
                .toList();
        storeReturns(SyncTable.DIAPERS, twelve.toArray(new SyncRecordStore.Row[0]));

        SyncChangesVO vo = service.pull(USER_ID, 0L, 0L, "CARE", 10);

        assertThat(vo.getCount()).isEqualTo(10);
        assertThat(vo.isHasMore()).isTrue();
        assertThat(vo.getNextSinceId()).isEqualTo(10L);

        // 客户端拿 nextSince/nextSinceId 回来，服务端必须把两段都带进查询
        service.pull(USER_ID, vo.getNextSince(), vo.getNextSinceId(), "CARE", 10);
        verify(store).selectChanges(eq(SyncTable.DIAPERS), eq(List.of(USER_ID)),
                eq(SyncCursor.toLocalDateTime(5_000L)), eq(10L), eq(10));
    }

    @Test
    void pull_categoryFilterSkipsOtherCategories() {
        service.pull(USER_ID, 0L, null, "FEEDING", null);

        verify(store).selectChanges(eq(SyncTable.FEEDINGS), any(), any(), anyLong(), anyInt());
        verify(store).selectChanges(eq(SyncTable.CHILDREN), any(), any(), anyLong(), anyInt());
        verify(store, never()).selectChanges(eq(SyncTable.DIAPERS), any(), any(), anyLong(), anyInt());
        verify(store, never()).selectChanges(eq(SyncTable.VISITS), any(), any(), anyLong(), anyInt());
    }

    @Test
    void pull_rejectsUnknownCategory() {
        assertBizError(() -> service.pull(USER_ID, 0L, null, "FEEDING,WEIRD", null),
                BabyLogErrorCode.SYNC_CATEGORY_INVALID);
    }

    @Test
    void pull_clampsLimitToMaximum() {
        service.pull(USER_ID, 0L, null, null, 9_999);

        verify(store).selectChanges(eq(SyncTable.FEEDINGS), any(), any(), anyLong(),
                eq(SyncServiceImpl.MAX_PULL_LIMIT));
    }

    @Test
    void pull_clampsAbsurdFutureCursorToServerTime() {
        long bogus = SyncCursor.now() + 86_400_000L;

        SyncChangesVO vo = service.pull(USER_ID, bogus, null, null, null);

        // 客户端误把自己的手机时钟当游标传：按服务端时间处理，否则会永远拉不到数据
        assertThat(vo.getNextSince()).isLessThan(bogus);
    }

    @Test
    void pull_familyMemberSeesFamilyScopeAndReadFilterApplies() {
        // M15 读侧家庭可见：VIEWER 成员可见集合 = 家庭全部 ACTIVE 成员；
        // 可读分类被收窄到 FEEDING（当前实际矩阵恒为全开，这里锁的是管道语义）
        org.mockito.Mockito.when(accessScopeResolver.resolveAccess(USER_ID))
                .thenReturn(new SyncAccessScope(9L, "VIEWER",
                        List.of(USER_ID, OTHER_USER_ID),
                        java.util.EnumSet.of(com.babylog.biz.sync.support.SyncCategory.FEEDING),
                        java.util.EnumSet.noneOf(com.babylog.biz.sync.support.SyncCategory.class)));

        service.pull(USER_ID, 0L, null, null, null);

        verify(store).selectChanges(eq(SyncTable.FEEDINGS), eq(List.of(USER_ID, OTHER_USER_ID)),
                any(), anyLong(), anyInt());
        verify(store, never()).selectChanges(eq(SyncTable.DIAPERS), any(), any(), anyLong(), anyInt());
        verify(store, never()).selectChanges(eq(SyncTable.VISITS), any(), any(), anyLong(), anyInt());
    }

    // ==================================================================
    // 推送：协议级错误（整批失败）
    // ==================================================================

    @Test
    void push_rejectsEmptyPayload() {
        SyncPushCmd cmd = new SyncPushCmd();
        cmd.setChanges(Map.of());

        assertBizError(() -> service.push(USER_ID, cmd), BabyLogErrorCode.SYNC_RECORD_INVALID);
    }

    @Test
    void push_rejectsBatchOverLimit() {
        List<Map<String, Object>> rows = IntStream.rangeClosed(1, SyncServiceImpl.MAX_PUSH_RECORDS + 1)
                .mapToObj(i -> payload((long) i, 1))
                .toList();

        assertBizError(() -> service.push(USER_ID, cmd("feedings", rows)),
                BabyLogErrorCode.SYNC_PAYLOAD_TOO_LARGE);
    }

    @Test
    void push_rejectsUnknownTable() {
        assertBizError(() -> service.push(USER_ID, cmd("sys_user", List.of(payload(1L, 1)))),
                BabyLogErrorCode.SYNC_TABLE_UNKNOWN);
    }

    // ==================================================================
    // 推送：逐条裁决（部分成功）
    // ==================================================================

    @Test
    void push_rejectsRecordWithoutIdOrVersion() {
        Map<String, Object> withoutId = new HashMap<>();
        withoutId.put("version", 1);
        Map<String, Object> withoutVersion = payload(2L, 0);
        withoutVersion.remove("version");

        SyncPushResultVO result = service.push(USER_ID,
                cmd("feedings", List.of(withoutId, withoutVersion)));

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getUpserted()).isZero();
        assertThat(result.getRejected()).extracting(SyncPushRejectVO::getCode)
                .containsExactly(BabyLogErrorCode.SYNC_RECORD_INVALID.getCode(),
                        BabyLogErrorCode.SYNC_RECORD_INVALID.getCode());
        verify(store, never()).upsert(any(), any(), anyLong());
    }

    @Test
    void push_rejectsRecordOwnedByAnotherAccount() {
        storeHasExisting(SyncTable.FEEDINGS, 88L, new SyncRecordStore.Existing(OTHER_USER_ID, 1));

        SyncPushResultVO result = service.push(USER_ID, cmd("feedings", List.of(payload(88L, 5))));

        // 跨用户雪花撞车：给 8344 让客户端换 ID，重推同一个 ID 永远不会成功
        assertThat(result.getRejected()).singleElement()
                .satisfies(reject -> {
                    assertThat(reject.getCode()).isEqualTo(BabyLogErrorCode.SYNC_OWNERSHIP_CONFLICT.getCode());
                    assertThat(reject.getId()).isEqualTo(88L);
                    assertThat(reject.getTable()).isEqualTo("feedings");
                    assertThat(reject.getMessage()).contains("更换 ID");
                });
        assertThat(result.getUpserted()).isZero();
        verify(store, never()).upsert(any(), any(), anyLong());
    }

    @Test
    void push_familyMemberCanEditRowOwnedByVisibleFamilyMember() {
        // M16 家庭维度归属放行：行属 OTHER（可见范围内的家庭成员）→ 不再 8344，按新值放行更新
        org.mockito.Mockito.when(accessScopeResolver.resolveAccess(USER_ID))
                .thenReturn(new SyncAccessScope(9L, "CAREGIVER",
                        List.of(USER_ID, OTHER_USER_ID),
                        java.util.EnumSet.allOf(com.babylog.biz.sync.support.SyncCategory.class),
                        java.util.EnumSet.of(com.babylog.biz.sync.support.SyncCategory.FEEDING,
                                com.babylog.biz.sync.support.SyncCategory.CARE)));
        storeHasExisting(SyncTable.FEEDINGS, 88L, new SyncRecordStore.Existing(OTHER_USER_ID, 3));

        SyncPushResultVO result = service.push(USER_ID, cmd("feedings", List.of(payload(88L, 5))));

        assertThat(result.getRejected()).isEmpty();
        assertThat(result.getUpserted()).isEqualTo(1);
        verify(store).upsert(eq(SyncTable.FEEDINGS), any(), eq(USER_ID));
    }

    @Test
    void push_rejectsServerManagedMemberAndPermissionTables() {
        // M16 提权漏洞收口：这两表的行归属 = 成员自己，归属校验拦不住整行重推自我改
        // OWNER / 翻转 canWrite → 整表拒收 8350（同 8345 内置产品的下发型语义）
        SyncPushResultVO members = service.push(USER_ID,
                cmd("family_members", List.of(payload(1L, 1))));
        SyncPushResultVO permissions = service.push(USER_ID,
                cmd("member_permissions", List.of(payload(2L, 1))));

        assertThat(members.getRejected()).singleElement()
                .satisfies(reject -> assertThat(reject.getCode())
                        .isEqualTo(BabyLogErrorCode.SYNC_MEMBER_TABLE_IMMUTABLE.getCode()));
        assertThat(permissions.getRejected()).singleElement()
                .satisfies(reject -> assertThat(reject.getCode())
                        .isEqualTo(BabyLogErrorCode.SYNC_MEMBER_TABLE_IMMUTABLE.getCode()));
        assertThat(members.getUpserted()).isZero();
        assertThat(permissions.getUpserted()).isZero();
        verify(store, never()).upsert(eq(SyncTable.FAMILY_MEMBERS), any(), anyLong());
        verify(store, never()).upsert(eq(SyncTable.MEMBER_PERMISSIONS), any(), anyLong());
    }

    @Test
    void push_guardRejectionIsCollectedPerRecordNotBatchFailure() {
        // M16 写拦截：VIEWER 推 CARE 越权 → guard 抛 8343，收进单条 rejected，不拖累同批另一条
        doThrow(new ServiceException(BabyLogErrorCode.SYNC_CATEGORY_DENIED))
                .when(permissionGuard).checkWrite(eq(SyncTable.DIAPERS), any(SyncAccessScope.class),
                eq(10L), any());

        SyncPushResultVO result = service.push(USER_ID,
                cmd("diapers", List.of(payload(10L, 1), payload(11L, 1))));

        assertThat(result.getRejected()).singleElement()
                .satisfies(reject -> {
                    assertThat(reject.getCode()).isEqualTo(BabyLogErrorCode.SYNC_CATEGORY_DENIED.getCode());
                    assertThat(reject.getId()).isEqualTo(10L);
                    assertThat(reject.getMessage()).contains("写权限");
                });
        assertThat(result.getUpserted()).isEqualTo(1);
        assertThat(result.getTotal()).isEqualTo(2);
        verify(store).upsert(eq(SyncTable.DIAPERS), any(), eq(USER_ID));
    }

    @Test
    void push_skipsWhenServerVersionIsNotOlder() {
        // 幂等重试：同一批推第二次，库内版本已追平 → 跳过而不是报错
        storeHasExisting(SyncTable.FEEDINGS, 88L, new SyncRecordStore.Existing(USER_ID, 5));

        SyncPushResultVO result = service.push(USER_ID, cmd("feedings", List.of(payload(88L, 5))));

        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getUpserted()).isZero();
        assertThat(result.getRejected()).isEmpty();
        verify(store, never()).upsert(any(), any(), anyLong());
    }

    @Test
    void push_upsertsWhenIncomingVersionIsNewer() {
        storeHasExisting(SyncTable.FEEDINGS, 88L, new SyncRecordStore.Existing(USER_ID, 3));

        SyncPushResultVO result = service.push(USER_ID, cmd("feedings", List.of(payload(88L, 4))));

        assertThat(result.getUpserted()).isEqualTo(1);
        assertThat(result.getSkipped()).isZero();
        assertThat(result.getRejected()).isEmpty();
        verify(store).upsert(eq(SyncTable.FEEDINGS), any(), eq(USER_ID));
        // 写权限钩子必须每条都被调用（V2.1 换真实现时不用回头找插桩点）
        verify(permissionGuard).checkWrite(eq(SyncTable.FEEDINGS), any(SyncAccessScope.class), eq(88L), any());
    }

    @Test
    void push_insertsRecordThatDoesNotExistYet() {
        SyncPushResultVO result = service.push(USER_ID, cmd("diapers", List.of(payload(77L, 1))));

        assertThat(result.getUpserted()).isEqualTo(1);
        verify(store).upsert(eq(SyncTable.DIAPERS), any(), eq(USER_ID));
    }

    @Test
    void push_rejectsBuiltinProducts() {
        // 1~99 是内置产品预留段：每台设备各推一份必撞主键，且内置内容由服务端维护
        SyncPushResultVO result = service.push(USER_ID, cmd("products", List.of(payload(7L, 1))));

        assertThat(result.getRejected()).singleElement()
                .extracting(SyncPushRejectVO::getCode)
                .isEqualTo(BabyLogErrorCode.SYNC_BUILTIN_IMMUTABLE.getCode());
        verify(store, never()).upsert(any(), any(), anyLong());
    }

    @Test
    void push_isolatesRecordRejectedByDatabaseConstraints() {
        doThrow(new DataIntegrityViolationException("Column 'child_id' cannot be null"))
                .doNothing()
                .when(store).upsert(eq(SyncTable.FEEDINGS), any(), eq(USER_ID));

        SyncPushResultVO result = service.push(USER_ID,
                cmd("feedings", List.of(payload(10L, 1), payload(11L, 1))));

        assertThat(result.getUpserted()).isEqualTo(1);
        assertThat(result.getRejected()).singleElement()
                .satisfies(reject -> {
                    assertThat(reject.getCode()).isEqualTo(BabyLogErrorCode.SYNC_RECORD_INVALID.getCode());
                    assertThat(reject.getId()).isEqualTo(10L);
                    assertThat(reject.getMessage()).contains("child_id");
                });
    }

    @Test
    void push_countsAllRecordsInBatch() {
        SyncPushResultVO result = service.push(USER_ID,
                cmd("feedings", List.of(payload(1L, 1), payload(2L, 1))));

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getUpserted()).isEqualTo(2);
        assertThat(result.getServerTime()).isPositive();
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    private void storeReturns(SyncTable table, SyncRecordStore.Row... rows) {
        org.mockito.Mockito.when(store.selectChanges(eq(table), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(rows));
    }

    private void storeHasExisting(SyncTable table, long id, SyncRecordStore.Existing existing) {
        org.mockito.Mockito.when(store.selectExisting(eq(table), any()))
                .thenReturn(Map.of(id, existing));
    }

    private static SyncRecordStore.Row row(SyncTable table, long id, long cursorMillis) {
        return new SyncRecordStore.Row(table, id, cursorMillis, payload(id, 1));
    }

    private static Map<String, Object> payload(Long id, int version) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("version", version);
        row.put("deleted", 0);
        return row;
    }

    private static SyncPushCmd cmd(String table, List<Map<String, Object>> rows) {
        SyncPushCmd cmd = new SyncPushCmd();
        cmd.setDeviceUuid("device-1");
        cmd.setChanges(new HashMap<>(Map.of(table, new ArrayList<>(rows))));
        return cmd;
    }

    private static void assertBizError(org.junit.jupiter.api.function.Executable action, ErrorCode expected) {
        assertThatThrownBy(action::execute)
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(expected.getCode()));
    }
}
