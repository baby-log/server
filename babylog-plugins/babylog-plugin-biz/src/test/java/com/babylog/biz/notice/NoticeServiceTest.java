package com.babylog.biz.notice;

import com.babylog.biz.notice.dto.NoticeConfirmCmd;
import com.babylog.biz.notice.dto.NoticeEventCmd;
import com.babylog.biz.notice.entity.BlNotice;
import com.babylog.biz.notice.mapper.BlNoticeMapper;
import com.babylog.biz.notice.rule.AnomalyKind;
import com.babylog.biz.notice.rule.HeadlessAnomalyRule;
import com.babylog.biz.notice.service.NoticeService;
import com.babylog.biz.notice.vo.NoticeChangesVO;
import com.babylog.biz.notice.vo.NoticeConfirmVO;
import com.babylog.biz.notice.vo.NoticeVO;
import com.babylog.biz.sync.support.SyncAccessScope;
import com.babylog.biz.sync.support.SyncAccessScopeResolver;
import com.babylog.biz.sync.support.SyncCategory;
import com.babylog.biz.sync.support.SyncCursor;
import com.babylog.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内通知服务单测（K4 / M71，契约 {@code 24} ⑤ ⑥ ⑦）。
 *
 * <p>覆盖 {@code 23} §五 K4 验证清单的服务端可单测部分：
 * <ul>
 *   <li>13:30 detected / 13:50 confirm ⇒ {@code businessTime}=13:30（时间口径）；</li>
 *   <li>两名成员同时确认仅一人成功、后到者出 conflicts 拉取（version CAS）；</li>
 *   <li>无头事件如实落库 + 异常标记 + 合成单点行（醒来无入睡 / 结束无开始）；</li>
 *   <li>通知按 visibleUserIds 家庭读侧下发、非家庭成员不可见；</li>
 *   <li>增量拉「记游标 → 增量拉」（双段游标，since=0 全量看不见 bug）；</li>
 *   <li>构造事件刚落库 serverUpdatedAt 未回填 ⇒ VO 键缺省、不输出误导性 "0"。</li>
 * </ul>
 *
 * <p>正式记录（客户端 /sync/push）与 dev 冒烟属集成/环境项，不在本单测伪造（见四问报告）。
 */
@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    private static final long USER_A = 1001L;
    private static final long USER_B = 2002L;
    private static final long OUTSIDER = 9999L;
    private static final long CHILD = 7L;
    private static final long NOTICE_ID = 500L;
    private static final long DETECTED_1330 = 1737001800000L; // 13:30
    private static final long CONFIRMED_1350 = 1737003000000L; // 13:50

    @Mock
    private BlNoticeMapper mapper;
    @Mock
    private SyncAccessScopeResolver scopeResolver;

    private NoticeService service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 单测 JVM 没有 MP 启动引导：confirm 的 LambdaUpdateWrapper.set(SFunction) / pull 的
        // LambdaQueryWrapper 都会立即解析方法引用，需实体 TableInfo 缓存，否则抛
        // "can not find lambda cache for this entity"（与 AdminProductServiceImplTest 同款）。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BlNotice.class);
    }

    @BeforeEach
    void setUp() {
        service = new NoticeService(mapper, scopeResolver);
        // 默认家庭可见范围 {A, B}；lenient 允许「参数非法在进 scopeOf 前就抛错」的用例不因未用桩而红
        lenient().when(scopeResolver.resolveAccess(anyLong()))
                .thenReturn(familyScope(List.of(USER_A, USER_B)));
    }

    private static SyncAccessScope familyScope(List<Long> visible) {
        return new SyncAccessScope(9L, "OWNER", visible,
                EnumSet.allOf(SyncCategory.class), EnumSet.allOf(SyncCategory.class));
    }

    // ==================================================================
    // 1. 构造事件：PENDING_CONFIRM 待确认事件
    // ==================================================================

    @Test
    void constructPendingStampsUserIdDetectedAtAndDefaults() {
        ArgumentCaptor<BlNotice> captor = ArgumentCaptor.forClass(BlNotice.class);

        List<NoticeVO> result = service.construct(USER_A, pendingCmd());

        assertThat(result).hasSize(1);
        NoticeVO vo = result.get(0);
        assertThat(vo.getNoticeType()).isEqualTo(NoticeService.TYPE_PENDING);
        assertThat(vo.getUserId()).isEqualTo(USER_A);      // 恒按 token 盖章
        assertThat(vo.getChildId()).isEqualTo(CHILD);
        assertThat(vo.getDetectedAt()).isEqualTo(DETECTED_1330);
        assertThat(vo.getConfirmedAt()).isNull();          // 未确认
        assertThat(vo.getIsConfirmed()).isZero();
        assertThat(vo.getIsHeadless()).isZero();
        assertThat(vo.getIsAbnormal()).isZero();
        assertThat(vo.getServerUpdatedAt()).isNull();      // 刚落库未回填 ⇒ 缺省键，不输出 "0"
        verify(mapper).insert(captor.capture());
        BlNotice row = captor.getValue();
        assertThat(row.getVersion()).isEqualTo(1L);        // version 从 1 起（CAS 期望值）
        assertThat(row.getDeleted()).isZero();
        assertThat(row.getServerUpdatedAt()).isNull();     // 交给 DB DEFAULT/ON UPDATE
        assertThat(row.getCreatedAt()).isNotNull();
    }

    @Test
    void constructPendingRejectsMissingDetectedAtOrRecordType() {
        NoticeEventCmd noTime = pendingCmd();
        noTime.setDetectedAt(null);
        assertInvalid(noTime, 8443);

        NoticeEventCmd noType = pendingCmd();
        noType.setRecordType("  ");
        assertInvalid(noType, 8443);
        verify(mapper, never()).insert(any(BlNotice.class));
    }

    @Test
    void constructRejectsNullCmdMissingChildOrUnknownType() {
        assertInvalid(null, 8443);

        NoticeEventCmd noChild = pendingCmd();
        noChild.setChildId(null);
        assertInvalid(noChild, 8443);

        NoticeEventCmd badType = pendingCmd();
        badType.setNoticeType("SOMETHING_ELSE");
        assertInvalid(badType, 8443);
        verify(mapper, never()).insert(any(BlNotice.class));
    }

    private void assertInvalid(NoticeEventCmd cmd, int code) {
        assertThatThrownBy(() -> service.construct(USER_A, cmd))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(code));
    }

    private static NoticeEventCmd pendingCmd() {
        NoticeEventCmd cmd = new NoticeEventCmd();
        cmd.setNoticeType(NoticeService.TYPE_PENDING);
        cmd.setChildId(CHILD);
        cmd.setDetectedAt(DETECTED_1330);
        cmd.setRecordType("FEEDING");
        cmd.setTitle("  检测到一次喂养  ");
        return cmd;
    }

    // ==================================================================
    // 2. 构造事件：HEADLESS_ANOMALY 无头异常（如实记录 + 单点行 + 异常标记）
    // ==================================================================

    @Test
    void constructHeadlessSynthesizesSinglePointRowsWithAbnormalMarkers() {
        NoticeEventCmd cmd = headlessCmd(List.of(
                event(HeadlessAnomalyRule.T_WAKE, DETECTED_1330),      // 醒来无入睡
                event(HeadlessAnomalyRule.T_END, CONFIRMED_1350)));     // 结束无开始
        ArgumentCaptor<BlNotice> captor = ArgumentCaptor.forClass(BlNotice.class);

        List<NoticeVO> result = service.construct(USER_A, cmd);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(NoticeVO::getAnomalyKind)
                .containsExactlyInAnyOrder(
                        AnomalyKind.WAKE_WITHOUT_SLEEP.code(),
                        AnomalyKind.END_WITHOUT_START.code());
        assertThat(result).allSatisfy(vo -> {
            assertThat(vo.getNoticeType()).isEqualTo(NoticeService.TYPE_HEADLESS);
            assertThat(vo.getIsHeadless()).isEqualTo(1);
            assertThat(vo.getIsAbnormal()).isEqualTo(1);
            assertThat(vo.getUserId()).isEqualTo(USER_A);
            assertThat(vo.getIsConfirmed()).isZero();
            // 合成单点行：只有一个时间点 eventAt，绝无 detectedAt（配对业务时间）/confirmedAt
            assertThat(vo.getEventAt()).isNotNull();
            assertThat(vo.getDetectedAt()).isNull();
            assertThat(vo.getConfirmedAt()).isNull();
        });
        verify(mapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(row -> {
            assertThat(row.getEventAt()).isNotNull();   // 单时间点
            assertThat(row.getDetectedAt()).isNull();  // 不复用同 ID 收尾 / 不造配对
            assertThat(row.getConfirmedAt()).isNull(); // 不造第二条结束行
            assertThat(row.getAnomalyKind()).isNotBlank();
        });
        assertThat(captor.getAllValues()).extracting(BlNotice::getEventAt)
                .containsExactlyInAnyOrder(DETECTED_1330, CONFIRMED_1350);
    }

    @Test
    void constructHeadlessFullyPairedReturnsEmptyNoInsert() {
        NoticeEventCmd cmd = headlessCmd(List.of(
                event(HeadlessAnomalyRule.T_SLEEP_START, DETECTED_1330),
                event(HeadlessAnomalyRule.T_WAKE, CONFIRMED_1350),
                event(HeadlessAnomalyRule.T_START, DETECTED_1330),
                event(HeadlessAnomalyRule.T_END, CONFIRMED_1350)));

        List<NoticeVO> result = service.construct(USER_A, cmd);

        assertThat(result).isEmpty();  // 配对齐全 ⇒ 不伪造异常
        verify(mapper, never()).insert(any(BlNotice.class));
    }

    @Test
    void constructHeadlessRejectsEmptyEvents() {
        assertInvalid(headlessCmd(List.of()), 8443);
        assertInvalid(headlessCmd(null), 8443);
        verify(mapper, never()).insert(any(BlNotice.class));
    }

    private static NoticeEventCmd headlessCmd(List<NoticeEventCmd.HeadlessAnomalyRuleEventPoint> events) {
        NoticeEventCmd cmd = new NoticeEventCmd();
        cmd.setNoticeType(NoticeService.TYPE_HEADLESS);
        cmd.setChildId(CHILD);
        cmd.setEvents(events);
        return cmd;
    }

    private static NoticeEventCmd.HeadlessAnomalyRuleEventPoint event(String type, Long at) {
        NoticeEventCmd.HeadlessAnomalyRuleEventPoint p = new NoticeEventCmd.HeadlessAnomalyRuleEventPoint();
        p.setType(type);
        p.setAt(at);
        return p;
    }

    // ==================================================================
    // 3. 单人确认：13:30 / 13:50 时间口径 + version CAS + conflicts
    // ==================================================================

    @Test
    void firstConfirmerWinsAndBusinessTimeEqualsDetectedAt() {
        BlNotice row = pendingRow(1L, USER_A);
        when(mapper.selectById(NOTICE_ID)).thenReturn(row);
        doReturn(1).when(mapper).update(any(), any());

        NoticeConfirmVO vo = service.confirm(USER_A, NOTICE_ID, confirmCmd(1L, CONFIRMED_1350));

        assertThat(vo.getConfirmed()).isEqualTo(1);
        assertThat(vo.getBusinessTime()).isEqualTo(DETECTED_1330); // 13:30，不是 13:50
        assertThat(vo.getDetectedAt()).isEqualTo(DETECTED_1330);
        assertThat(vo.getConfirmedAt()).isEqualTo(CONFIRMED_1350);  // 仅审计
        assertThat(vo.getConfirmedBy()).isEqualTo(USER_A);          // 身份恒从 token 取
        assertThat(vo.getVersion()).isEqualTo(2L);                  // 先到者推进 version
        assertThat(vo.getConflicts()).isEmpty();

        // CAS WHERE 条件锁死：version 匹配 + 未确认 + 未删除
        ArgumentCaptor<LambdaUpdateWrapper<BlNotice>> cas = casCaptor();
        verify(mapper).update(any(), cas.capture());
        String segment = cas.getValue().getSqlSegment();
        assertThat(segment).contains("version", "is_confirmed", "deleted");
    }

    @Test
    void secondConfirmerGetsConflictAndDequeuesToPull() {
        BlNotice row = pendingRow(1L, USER_A);
        BlNotice latest = pendingRow(2L, USER_A); // 先到者已推进到 2
        latest.setIsConfirmed(1);
        when(mapper.selectById(NOTICE_ID)).thenReturn(row, latest);
        doReturn(0).when(mapper).update(any(), any()); // CAS 落空

        NoticeConfirmVO vo = service.confirm(USER_B, NOTICE_ID, confirmCmd(1L, CONFIRMED_1350));

        assertThat(vo.getConfirmed()).isZero();
        assertThat(vo.getBusinessTime()).isNull();   // 未抢到，不下发 businessTime
        assertThat(vo.getVersion()).isEqualTo(2L);  // 服务端现状
        assertThat(vo.getConflicts()).hasSize(1);
        assertThat(vo.getConflicts().get(0).getTable()).isEqualTo("bl_notice");
        assertThat(vo.getConflicts().get(0).getId()).isEqualTo(String.valueOf(NOTICE_ID));
        assertThat(vo.getConflicts().get(0).getServerVersion()).isEqualTo(2L);
        assertThat(vo.getConflicts().get(0).getClientVersion()).isEqualTo(1L);
    }

    @Test
    void twoMembersConfirmSequentiallyOnlyOneSucceeds() {
        // 模拟两名成员几乎同时确认：DB 单行 CAS 只放行第一个（affected=1），第二个 affected=0
        BlNotice row = pendingRow(1L, USER_A);
        BlNotice latest = pendingRow(2L, USER_A);
        latest.setIsConfirmed(1);
        when(mapper.selectById(NOTICE_ID)).thenReturn(row, row, latest);
        doReturn(1, 0).when(mapper).update(any(), any());

        NoticeConfirmVO first = service.confirm(USER_A, NOTICE_ID, confirmCmd(1L, CONFIRMED_1350));
        NoticeConfirmVO second = service.confirm(USER_B, NOTICE_ID, confirmCmd(1L, CONFIRMED_1350));

        assertThat(first.getConfirmed()).isEqualTo(1);
        assertThat(first.getBusinessTime()).isEqualTo(DETECTED_1330);
        assertThat(first.getConflicts()).isEmpty();
        assertThat(second.getConfirmed()).isZero();
        assertThat(second.getConflicts()).hasSize(1);   // 后到者出队 + 触发拉取
    }

    @Test
    void confirmOutsideFamilyScopeThrows8440WithoutUpdate() {
        BlNotice row = pendingRow(1L, OUTSIDER); // 归属非家庭成员
        when(mapper.selectById(NOTICE_ID)).thenReturn(row);

        assertThatThrownBy(() -> service.confirm(USER_A, NOTICE_ID, confirmCmd(1L, CONFIRMED_1350)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(8440));
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void confirmRejectsInvalidVersionBeforeAnyDbCall() {
        assertThatThrownBy(() -> service.confirm(USER_A, NOTICE_ID, confirmCmd(null, null)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(8442));
        assertThatThrownBy(() -> service.confirm(USER_A, NOTICE_ID, confirmCmd(0L, null)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(8442));
        verify(mapper, never()).selectById(anyLong());
    }

    @Test
    void confirmRejectsNonPendingNotice() {
        BlNotice row = pendingRow(1L, USER_A);
        row.setNoticeType(NoticeService.TYPE_HEADLESS); // 异常消息不接受确认
        when(mapper.selectById(NOTICE_ID)).thenReturn(row);

        assertThatThrownBy(() -> service.confirm(USER_A, NOTICE_ID, confirmCmd(1L, CONFIRMED_1350)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(8441));
        verify(mapper, never()).update(any(), any());
    }

    private static NoticeConfirmCmd confirmCmd(Long version, Long confirmedAt) {
        NoticeConfirmCmd cmd = new NoticeConfirmCmd();
        cmd.setVersion(version);
        cmd.setConfirmedAt(confirmedAt);
        cmd.setConfirmedByLabel("客户端显示名（不可信）");
        return cmd;
    }

    private static BlNotice pendingRow(long version, long ownerUserId) {
        BlNotice row = new BlNotice();
        row.setId(NOTICE_ID);
        row.setChildId(CHILD);
        row.setUserId(ownerUserId);
        row.setNoticeType(NoticeService.TYPE_PENDING);
        row.setTitle("待确认");
        row.setRecordType("FEEDING");
        row.setDetectedAt(DETECTED_1330);
        row.setIsConfirmed(0);
        row.setIsHeadless(0);
        row.setIsAbnormal(0);
        row.setVersion(version);
        row.setDeleted(0);
        row.setCreatedAt(DETECTED_1330);
        return row;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<LambdaUpdateWrapper<BlNotice>> casCaptor() {
        return ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
    }

    // ==================================================================
    // 4. 增量拉：家庭读侧口径 + 双段游标「记游标 → 增量拉」
    // ==================================================================

    @Test
    void pullFiltersByVisibleUserIdsOnly() {
        // 家庭可见 {A,B}：wrapper 的 IN 参数恰好是这两个，绝不含 OUTSIDER
        AtomicReference<LambdaQueryWrapper<BlNotice>> captured = new AtomicReference<>();
        captureSelectPage(captured, pageOf(List.of(noticeRow(1L, USER_A, at(1000L)))));

        service.pull(USER_A, 0L, 0L, 200);

        LambdaQueryWrapper<BlNotice> wrapper = captured.get();
        assertThat(wrapper.getSqlSegment()).contains("IN");   // user_id IN (...)
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(USER_A, USER_B)
                .doesNotContain(OUTSIDER);
    }

    @Test
    void singleUserScopeExcludesOtherFamilyRows() {
        // 无家庭单用户：可见集合只有自己，SQL 只按自己过滤 ⇒ 别人行不可见
        when(scopeResolver.resolveAccess(OUTSIDER)).thenReturn(SyncAccessScope.singleUser(OUTSIDER));
        AtomicReference<LambdaQueryWrapper<BlNotice>> captured = new AtomicReference<>();
        captureSelectPage(captured, pageOf(List.of()));

        NoticeChangesVO vo = service.pull(OUTSIDER, 0L, 0L, 200);

        assertThat(vo.getNotices()).isEmpty();
        captured.get().getSqlSegment(); // 触发 MP 惰性参数绑定，getParamNameValuePairs 才有值
        assertThat(captured.get().getParamNameValuePairs().values())
                .contains(OUTSIDER)
                .doesNotContain(USER_A, USER_B);  // 非家庭成员不可见
    }

    @Test
    void emptyScopeReturnsEmptyAndEchoesCursor() {
        when(scopeResolver.resolveAccess(USER_A)).thenReturn(familyScope(List.of()));

        NoticeChangesVO vo = service.pull(USER_A, 555L, 42L, 200);

        assertThat(vo.getNotices()).isEmpty();
        assertThat(vo.getHasMore()).isZero();
        assertThat(vo.getNextSince()).isEqualTo(555L);   // 原样回传
        assertThat(vo.getNextSinceId()).isEqualTo(42L);
        verify(mapper, never()).selectPage(any(), any()); // 空 scope 不查库
    }

    @Test
    void pullReturnsDoubleSegmentCursorAndHasMore() {
        // limit=2 → 查 3 行；返回 3 行 ⇒ hasMore=1，页码裁成 2 行，游标取第 2 行
        BlNotice r1 = noticeRow(1L, USER_A, at(1000L));
        BlNotice r2 = noticeRow(2L, USER_A, at(2000L));
        BlNotice r3 = noticeRow(3L, USER_B, at(3000L));
        captureSelectPage(new AtomicReference<>(), pageOf(List.of(r1, r2, r3)));

        NoticeChangesVO vo = service.pull(USER_A, 0L, 0L, 2);

        assertThat(vo.getNotices()).hasSize(2);
        assertThat(vo.getHasMore()).isEqualTo(1);
        assertThat(vo.getNextSince()).isEqualTo(SyncCursor.toMillis(at(2000L)));
        assertThat(vo.getNextSinceId()).isEqualTo(2L);
        assertThat(vo.getServerTime()).isNotNull();
    }

    @Test
    void rememberCursorThenIncrementalPull() {
        // 「记游标 → 增量拉」：第一次拿到游标，第二次原样回传，服务端解回同一个 LocalDateTime
        // 用于 WHERE（since=0 全量看不见游标 bug 的回归锁）
        BlNotice r1 = noticeRow(1L, USER_A, at(5000L));
        captureSelectPage(new AtomicReference<>(), pageOf(List.of(r1)));
        NoticeChangesVO first = service.pull(USER_A, 0L, 0L, 200);
        long cursor = first.getNextSince();
        long cursorId = first.getNextSinceId();

        AtomicReference<LambdaQueryWrapper<BlNotice>> secondWrapper = new AtomicReference<>();
        captureSelectPage(secondWrapper, pageOf(List.of()));

        NoticeChangesVO second = service.pull(USER_A, cursor, cursorId, 200);

        assertThat(cursor).isEqualTo(SyncCursor.toMillis(at(5000L)));
        assertThat(cursorId).isEqualTo(1L);
        assertThat(second.getNotices()).isEmpty();
        // 第二次查询的游标参数是第一次返回的 nextSince 解回的时间，不是 0/epoch
        LocalDateTime expected = SyncCursor.toLocalDateTime(cursor);
        secondWrapper.get().getSqlSegment(); // 触发 MP 惰性参数绑定
        assertThat(secondWrapper.get().getParamNameValuePairs().values()).contains(expected);
    }

    private static LocalDateTime at(long millis) {
        return SyncCursor.toLocalDateTime(millis);
    }

    private static BlNotice noticeRow(long id, long ownerUserId, LocalDateTime serverUpdatedAt) {
        BlNotice row = pendingRow(1L, ownerUserId);
        row.setId(id);
        row.setServerUpdatedAt(serverUpdatedAt);
        return row;
    }

    private static Page<BlNotice> pageOf(List<BlNotice> rows) {
        Page<BlNotice> page = new Page<>(1, 200);
        page.setRecords(rows);
        page.setTotal(rows.size());
        return page;
    }

    private void captureSelectPage(AtomicReference<LambdaQueryWrapper<BlNotice>> captured, Page<BlNotice> page) {
        doAnswer(inv -> {
            captured.set(inv.getArgument(1));
            return page;
        }).when(mapper).selectPage(ArgumentMatchers.<Page<BlNotice>>any(), any());
    }
}
