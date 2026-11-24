package com.babylog.biz.notice.service;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.notice.dto.NoticeConfirmCmd;
import com.babylog.biz.notice.dto.NoticeEventCmd;
import com.babylog.biz.notice.entity.BlNotice;
import com.babylog.biz.notice.mapper.BlNoticeMapper;
import com.babylog.biz.notice.rule.AnomalyKind;
import com.babylog.biz.notice.rule.HeadlessAnomalyRule;
import com.babylog.biz.notice.vo.NoticeChangesVO;
import com.babylog.biz.notice.vo.NoticeConflictVO;
import com.babylog.biz.notice.vo.NoticeConfirmVO;
import com.babylog.biz.notice.vo.NoticeVO;
import com.babylog.biz.sync.support.SyncAccessScopeResolver;
import com.babylog.biz.sync.support.SyncCursor;
import com.babylog.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 站内通知服务（K4 / M71，契约 {@code 24} ⑤ 站内通知 / ⑥ 单人确认 / ⑦ 无头异常）。
 *
 * <p><b>接收侧闭环</b>：V3 前无真实采集（K9 才出图），本服务只消费「构造事件」——
 * 结构化事件点（类型 + 时间点，零画面、零字节），产出通知 / 确认 / 时间 / 异常全链路，
 * 不接摄像头/麦克风、不做画面、不实现 K9 采集、不定义 {@code bl_alert_rule} 阈值外的东西。
 *
 * <p><b>服务端专用表</b>：{@code bl_notice} 不进 {@code SyncTable} 枚举、不参与
 * {@code /sync/changes}，走 {@code /app/babylog/notice/*} 独立端点；四端不得往
 * Room / SwiftData / outbox / 19 张同步表里塞通知行。
 *
 * <p><b>读侧家庭口径</b>：所有拉取按 {@code visibleUserIds} 过滤；非家庭成员不可见。
 * 双段游标 {@code since(server_updated_at millis)+sinceId(id)} 与 {@code /sync/changes} 同款，
 * 冒烟走「记游标 → 增量拉」，{@code since=0} 全量看不见游标 bug（{@code 23} §2.2-13）。
 * 确认后 {@code server_updated_at ON UPDATE} 自动刷新游标 → 后到者增量拉能拉到「已确认态」。
 *
 * <p><b>单人确认（{@code 24} ⑥）</b>：单行原子 CAS（{@code WHERE version=期望值 AND
 * is_confirmed=0}），先到者赢、后到者收 conflicts 出队拉取；复用 M53 conflicts 结构
 * （{@code 13} §1 #6），<b>不是往 19 张同步表塞行</b>。落正式记录用 {@code detectedAt}
 * （{@code businessTime}），正式记录由客户端走既有 {@code /sync/push} 落库，服务端零代写
 * （自动检测永不自动落库保持）。
 *
 * <p><b>无头异常（{@code 24} ⑦）</b>：{@link HeadlessAnomalyRule} 判定「醒来无入睡 /
 * 结束无开始」两条，命中逐条合成单点行（只有 {@code eventAt} 一个时间点）+ 异常标记 +
 * 异常站内消息，全体成员可见；不伪造配对、不假装完整会话、不复用同 ID 收尾、不造第二条结束行。
 *
 * <p>{@code user_id} 恒按 token 盖章（必填语义列），{@code version} 从 1 起，
 * 出站 Long 全局 {@code WriteLongAsString}、TINYINT 布尔 Integer 0/1、null 键缺省。
 */
@Service
public class NoticeService {

    /** 通知类型：待确认事件（24 ⑥）/ 无头异常（24 ⑦） */
    public static final String TYPE_PENDING = "PENDING_CONFIRM";
    public static final String TYPE_HEADLESS = "HEADLESS_ANOMALY";

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 200;

    private final BlNoticeMapper mapper;
    private final SyncAccessScopeResolver scopeResolver;

    public NoticeService(BlNoticeMapper mapper, SyncAccessScopeResolver scopeResolver) {
        this.mapper = mapper;
        this.scopeResolver = scopeResolver;
    }

    // ------------------------------------------------------------------
    // 1. 构造事件（K4 接收侧验证用；K9 采集侧将来复用同款事件点语义）
    // ------------------------------------------------------------------

    /**
     * 构造检测事件并落通知（<b>只写 {@code bl_notice} 服务端专用表</b>，不写正式记录、不碰同步表）。
     *
     * <p>{@code PENDING_CONFIRM}：落一条待确认事件通知（detectedAt + recordType）；
     * {@code HEADLESS_ANOMALY}：经规则引擎判定两条无头规则，逐条合成单点行 + 异常标记 + 异常消息。
     * 构造事件都有配对（无无头命中）时如实返回空列表——不伪造异常。
     */
    public List<NoticeVO> construct(long userId, NoticeEventCmd cmd) {
        if (cmd == null || cmd.getChildId() == null || cmd.getNoticeType() == null) {
            throw new ServiceException(BabyLogErrorCode.NOTICE_EVENT_INVALID);
        }
        String type = cmd.getNoticeType();
        if (TYPE_PENDING.equals(type)) {
            return List.of(insertPending(userId, cmd));
        }
        if (TYPE_HEADLESS.equals(type)) {
            return insertHeadless(userId, cmd);
        }
        throw new ServiceException(BabyLogErrorCode.NOTICE_EVENT_INVALID);
    }

    private NoticeVO insertPending(long userId, NoticeEventCmd cmd) {
        if (cmd.getDetectedAt() == null || blank(cmd.getRecordType())) {
            throw new ServiceException(BabyLogErrorCode.NOTICE_EVENT_INVALID);
        }
        BlNotice row = base(userId, cmd.getChildId(), TYPE_PENDING);
        row.setTitle(blank(cmd.getTitle()) ? "有一条待确认的记录" : trim(cmd.getTitle()));
        row.setRecordType(trim(cmd.getRecordType()));
        row.setDetectedAt(cmd.getDetectedAt());
        row.setIsConfirmed(0);
        row.setIsHeadless(0);
        row.setIsAbnormal(0);
        mapper.insert(row);
        return toVO(row);
    }

    private List<NoticeVO> insertHeadless(long userId, NoticeEventCmd cmd) {
        if (cmd.getEvents() == null || cmd.getEvents().isEmpty()) {
            throw new ServiceException(BabyLogErrorCode.NOTICE_EVENT_INVALID);
        }
        List<HeadlessAnomalyRule.EventPoint> points = new ArrayList<>();
        for (NoticeEventCmd.HeadlessAnomalyRuleEventPoint e : cmd.getEvents()) {
            if (e != null) {
                points.add(new HeadlessAnomalyRule.EventPoint(e.getType(), e.getAt()));
            }
        }
        List<HeadlessAnomalyRule.Finding> findings = HeadlessAnomalyRule.detect(points);
        List<NoticeVO> result = new ArrayList<>();
        for (HeadlessAnomalyRule.Finding finding : findings) {
            BlNotice row = base(userId, cmd.getChildId(), TYPE_HEADLESS);
            AnomalyKind kind = finding.kind();
            row.setTitle("检测到无配对记录（" + kind.label() + "）");
            row.setAnomalyKind(kind.code());
            // 合成单点行：只有 eventAt 一个时间点；无 endedAt / 无 pauseEvents / 无配对
            row.setEventAt(finding.pointAt());
            row.setIsHeadless(1);
            row.setIsAbnormal(1);
            row.setIsConfirmed(0);
            mapper.insert(row);
            result.add(toVO(row));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 2. 增量拉取（按 visibleUserIds 家庭读侧口径 + 双段游标）
    // ------------------------------------------------------------------

    /**
     * 拉取通知：{@code user_id IN visibleUserIds}（非家庭成员不可见），
     * 双段游标 {@code (server_updated_at > since) OR (= since AND id > sinceId)}，
     * 按 {@code server_updated_at, id} 升序，取 {@code limit+1} 判 {@code hasMore}。
     *
     * <p>{@code nextSince}/{@code nextSinceId} 取本页最后一行的游标，客户端原样回传。
     */
    public NoticeChangesVO pull(long userId, Long sinceMillis, Long sinceId, Integer limit) {
        NoticeChangesVO vo = new NoticeChangesVO();
        vo.setServerTime(SyncCursor.now());
        Set<Long> scope = scopeOf(userId);
        if (scope.isEmpty()) {
            vo.setNotices(List.of());
            vo.setNextSince(sinceMillis == null ? 0L : sinceMillis);
            vo.setNextSinceId(sinceId == null ? 0L : sinceId);
            vo.setHasMore(0);
            return vo;
        }
        long since = sinceMillis == null ? 0L : Math.max(0L, sinceMillis);
        long sinceIdVal = sinceId == null ? 0L : Math.max(0L, sinceId);
        int lim = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        LocalDateTime sinceLdt = SyncCursor.toLocalDateTime(since);

        LambdaQueryWrapper<BlNotice> wrapper = new LambdaQueryWrapper<BlNotice>()
                .in(BlNotice::getUserId, scope)
                .and(w -> w.gt(BlNotice::getServerUpdatedAt, sinceLdt)
                        .or(o -> o.eq(BlNotice::getServerUpdatedAt, sinceLdt)
                                .gt(BlNotice::getId, sinceIdVal)))
                .orderByAsc(BlNotice::getServerUpdatedAt)
                .orderByAsc(BlNotice::getId);
        List<BlNotice> rows = mapper.selectPage(new Page<>(1, lim + 1L, false), wrapper).getRecords();

        boolean hasMore = rows.size() > lim;
        List<BlNotice> page = hasMore ? rows.subList(0, lim) : rows;
        List<NoticeVO> notices = new ArrayList<>();
        for (BlNotice row : page) {
            notices.add(toVO(row));
        }
        vo.setNotices(notices);
        vo.setHasMore(hasMore ? 1 : 0);
        if (page.isEmpty()) {
            vo.setNextSince(since);
            vo.setNextSinceId(sinceIdVal);
        } else {
            BlNotice last = page.get(page.size() - 1);
            vo.setNextSince(SyncCursor.toMillis(last.getServerUpdatedAt()));
            vo.setNextSinceId(last.getId());
        }
        return vo;
    }

    // ------------------------------------------------------------------
    // 3. 单人确认原子状态机（version 乐观锁 CAS + conflicts）
    // ------------------------------------------------------------------

    /**
     * 单人确认：两名成员同时确认同一待确认事件，服务端单行原子 CAS
     * （{@code UPDATE ... WHERE id=? AND version=? AND is_confirmed=0}）只放行一个。
     *
     * <p>先到者：{@code confirmed=1}、{@code businessTime=detectedAt}（13:30）、version 推进、
     * {@code server_updated_at ON UPDATE} 自动刷新游标；后到者：{@code confirmed=0} +
     * conflicts（结构对齐 M53），其确认帧出队并触发拉取，看到已确认态。
     *
     * <p><b>正式记录不在此落库</b>：返回 {@code businessTime} 交客户端走 {@code /sync/push}；
     * 自动检测永不自动落库保持（{@code 22} §7.2）。
     */
    public NoticeConfirmVO confirm(long userId, Long id, NoticeConfirmCmd cmd) {
        if (id == null || cmd == null || cmd.getVersion() == null || cmd.getVersion() <= 0) {
            throw new ServiceException(BabyLogErrorCode.NOTICE_CONFIRM_VERSION_INVALID);
        }
        long clientVersion = cmd.getVersion();
        BlNotice current = requireVisible(userId, id);
        if (!TYPE_PENDING.equals(current.getNoticeType())) {
            // 无头异常等非待确认类型不接受确认（防误确认异常消息）
            throw new ServiceException(BabyLogErrorCode.NOTICE_ALREADY_CONFIRMED);
        }
        long confirmedAt = cmd.getConfirmedAt() != null ? cmd.getConfirmedAt() : System.currentTimeMillis();

        // 单行原子 CAS：version 匹配 + 未确认 + 未删除 才放行（先到者赢）
        LambdaUpdateWrapper<BlNotice> cas = new LambdaUpdateWrapper<BlNotice>()
                .eq(BlNotice::getId, id)
                .eq(BlNotice::getVersion, clientVersion)
                .eq(BlNotice::getIsConfirmed, 0)
                .eq(BlNotice::getDeleted, 0)
                .set(BlNotice::getIsConfirmed, 1)
                .set(BlNotice::getConfirmedAt, confirmedAt)
                .set(BlNotice::getConfirmedBy, userId)
                .set(BlNotice::getVersion, clientVersion + 1);
        int affected = mapper.update(null, cas);

        NoticeConfirmVO vo = new NoticeConfirmVO();
        vo.setDetectedAt(current.getDetectedAt());
        vo.setConfirmedAt(confirmedAt);
        if (affected == 1) {
            // 先到者赢
            vo.setConfirmed(1);
            vo.setBusinessTime(current.getDetectedAt()); // 13:30，正式记录业务时间
            vo.setConfirmedBy(userId);
            vo.setVersion(clientVersion + 1);
        } else {
            // 后到者：读库现状，出 conflicts → 出队 + 触发拉取
            BlNotice latest = mapper.selectById(id);
            long serverVersion = latest != null && latest.getVersion() != null
                    ? latest.getVersion() : clientVersion + 1;
            vo.setConfirmed(0);
            vo.setVersion(serverVersion);
            List<NoticeConflictVO> conflicts = new ArrayList<>();
            conflicts.add(new NoticeConflictVO("bl_notice", String.valueOf(id),
                    serverVersion, clientVersion));
            vo.setConflicts(conflicts);
        }
        return vo;
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private BlNotice requireVisible(long userId, long id) {
        BlNotice row = mapper.selectById(id);
        if (row == null || !scopeOf(userId).contains(row.getUserId())) {
            // 不存在与无权访问不区分，避免枚举探测（同家庭读侧口径）
            throw new ServiceException(BabyLogErrorCode.NOTICE_NOT_FOUND);
        }
        return row;
    }

    private Set<Long> scopeOf(long userId) {
        List<Long> visible = scopeResolver.resolveAccess(userId).visibleUserIds();
        return visible == null ? Set.of() : new HashSet<>(visible);
    }

    private static BlNotice base(long userId, Long childId, String noticeType) {
        long now = System.currentTimeMillis();
        BlNotice row = new BlNotice();
        row.setNoticeType(noticeType);
        row.setChildId(childId);
        row.setUserId(userId);          // 必填语义列，恒按 token 盖章
        row.setVersion(1L);
        row.setDeleted(0);
        row.setCreatedAt(now);
        // serverUpdatedAt 交给 DB DEFAULT/ON UPDATE 自动维护（业务代码不赋值，游标才推进）
        return row;
    }

    private static NoticeVO toVO(BlNotice row) {
        NoticeVO vo = new NoticeVO();
        vo.setId(row.getId());
        vo.setChildId(row.getChildId());
        vo.setUserId(row.getUserId());
        vo.setNoticeType(row.getNoticeType());
        vo.setTitle(row.getTitle());
        vo.setRecordType(row.getRecordType());
        vo.setDetectedAt(row.getDetectedAt());
        vo.setConfirmedAt(row.getConfirmedAt());
        vo.setConfirmedBy(row.getConfirmedBy());
        vo.setEventAt(row.getEventAt());
        vo.setAnomalyKind(row.getAnomalyKind());
        vo.setIsHeadless(row.getIsHeadless());
        vo.setIsConfirmed(row.getIsConfirmed());
        vo.setIsAbnormal(row.getIsAbnormal());
        vo.setVersion(row.getVersion());
        vo.setCreatedAt(row.getCreatedAt());
        // 构造事件刚落库时 serverUpdatedAt 由 DB DEFAULT 维护、实体尚未回填；
        // 此时保持 null 键缺省（全局 NON_NULL），不输出误导性的 "0"。
        // 真实增量拉阶段由 DB 读回必有值，正常输出 epoch millis 字符串。
        if (row.getServerUpdatedAt() != null) {
            vo.setServerUpdatedAt(SyncCursor.toMillis(row.getServerUpdatedAt()));
        }
        return vo;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
