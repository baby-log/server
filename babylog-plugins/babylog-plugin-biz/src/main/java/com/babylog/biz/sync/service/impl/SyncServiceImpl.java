package com.babylog.biz.sync.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.sync.dto.SyncPushCmd;
import com.babylog.biz.sync.service.SyncService;
import com.babylog.biz.sync.store.SyncRecordStore;
import com.babylog.biz.sync.support.SyncAccessScope;
import com.babylog.biz.sync.support.SyncAccessScopeResolver;
import com.babylog.biz.sync.support.SyncCategory;
import com.babylog.biz.sync.support.SyncCursor;
import com.babylog.biz.sync.support.SyncPermissionGuard;
import com.babylog.biz.sync.support.SyncTable;
import com.babylog.biz.sync.vo.SyncChangesVO;
import com.babylog.biz.sync.vo.SyncPushRejectVO;
import com.babylog.biz.sync.vo.SyncPushResultVO;
import com.babylog.common.core.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 同步编排实现。
 *
 * <p>三块职责，边界写清楚：
 * <ul>
 *   <li><b>拉取</b>：游标推进、跨表归并排序、分类过滤、分页；可见范围与读分类由
 *       {@link SyncAccessScopeResolver} 推导（V2.1 M15 读侧家庭可见：成员拉得到家庭
 *       全部 ACTIVE 成员的数据；无家庭用户集合 = {自己}，行为与 V2.0 零变化）；</li>
 *   <li><b>推送</b>：结构校验 → 服务端管理表拒推（8350）→ 归属校验（家庭维度放行）→
 *       版本门禁 → 写权限（8343）→ 落库，逐条裁决（部分成功）；
 *       写权限判断交给 {@link SyncPermissionGuard}（M16 起拦截已点亮）；</li>
 *   <li><b>协议错误</b>：只有「请求本身不成立」（空载荷、超量、未知表、未知分类）才抛异常整批失败，
 *       单条记录的问题一律进 rejected 明细。</li>
 * </ul>
 *
 * <p><b>游标为什么是双段</b>：{@code server_updated_at} 只有毫秒精度，而 MySQL 的
 * {@code NOW(3)} 在同一条语句内是常量——一次 200 行的推送可能共用同一个毫秒值。
 * 只按时间戳推进，就会在「同一毫秒的行数 &gt; 单页上限」时把后一半永久跳过。
 * 加 id 做 tiebreak 后，分页严格单调，不会漏也不会重复推进。
 */
@Service
public class SyncServiceImpl implements SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncServiceImpl.class);

    /** 单次推送记录数上限（协议约定 200：小到能被一次事务吞下，大到手机端一分钟的攒批够用） */
    static final int MAX_PUSH_RECORDS = 200;
    /** 单页默认记录数 */
    static final int DEFAULT_PULL_LIMIT = 300;
    /** 单页最大记录数（18 表 × 500 行是内存与响应体的上限，再大该走全量初始化通道） */
    static final int MAX_PULL_LIMIT = 500;
    /** bl_products 内置行的 id 预留段上界（App 端 V2.0 迁移口径，见 M4 拍板） */
    private static final long BUILTIN_PRODUCT_ID_MAX = 99L;
    /**
     * 客户端时钟离谱时的保护阈值：超过服务端时间这么多就按服务端时间算。
     * 容差 10 分钟——正常往返误差在秒级，而「把手机 millis 当游标传」通常差几分钟到几小时。
     */
    private static final long CURSOR_FUTURE_TOLERANCE_MILLIS = 10 * 60 * 1000L;

    private final SyncRecordStore store;
    private final SyncPermissionGuard permissionGuard;
    private final SyncAccessScopeResolver accessScopeResolver;

    public SyncServiceImpl(SyncRecordStore store, SyncPermissionGuard permissionGuard,
                           SyncAccessScopeResolver accessScopeResolver) {
        this.store = store;
        this.permissionGuard = permissionGuard;
        this.accessScopeResolver = accessScopeResolver;
    }

    // ==================================================================
    // 拉取
    // ==================================================================

    @Override
    public SyncChangesVO pull(long userId, Long sinceMillis, Long sinceId, String categories, Integer limit) {
        SyncAccessScope scope = accessScopeResolver.resolveAccess(userId);
        Set<SyncCategory> requested = parseCategories(categories);
        // 有效分类 = 客户端请求 ∩ 成员可读分类（无家庭用户可读 = 全开，行为零变化）。
        // 基础设施表不参与分类过滤，任何请求都随行下发（少了它们业务记录连 child_id 都对不上号）。
        Set<SyncCategory> effective = intersect(requested, scope.readableCategories());
        int pageLimit = SyncCursor.normalizeLimit(limit, DEFAULT_PULL_LIMIT, MAX_PULL_LIMIT);
        long serverTime = SyncCursor.now();
        long effectiveSince = SyncCursor.normalizeSince(sinceMillis, serverTime, CURSOR_FUTURE_TOLERANCE_MILLIS);
        long effectiveSinceId = sinceId == null ? 0L : Math.max(0L, sinceId);
        LocalDateTime sinceTime = SyncCursor.toLocalDateTime(effectiveSince);

        List<SyncRecordStore.Row> merged = new ArrayList<>();
        for (SyncTable table : SyncTable.forCategories(effective)) {
            merged.addAll(store.selectChanges(table, scope.visibleUserIds(), sinceTime, effectiveSinceId, pageLimit));
        }
        // 跨表归并：游标是全局的（所有表共用一份 server_updated_at 时间轴），故必须全局排序后再截断
        merged.sort(Comparator.comparingLong(SyncRecordStore.Row::cursorMillis)
                .thenComparingLong(SyncRecordStore.Row::id));
        boolean hasMore = merged.size() >= pageLimit;
        List<SyncRecordStore.Row> page = hasMore ? merged.subList(0, pageLimit) : merged;

        Map<String, List<Map<String, Object>>> changes = new LinkedHashMap<>();
        for (SyncRecordStore.Row row : page) {
            changes.computeIfAbsent(row.table().apiName(), key -> new ArrayList<>()).add(row.data());
        }

        SyncChangesVO vo = new SyncChangesVO();
        vo.setChanges(changes);
        vo.setCount(page.size());
        vo.setHasMore(hasMore);
        vo.setServerTime(serverTime);
        if (page.isEmpty()) {
            // 没有新数据时把入参游标原样退回：否则客户端会把游标清成 0，下次变成全量重拉
            vo.setNextSince(effectiveSince);
            vo.setNextSinceId(effectiveSinceId);
        } else {
            SyncRecordStore.Row last = page.get(page.size() - 1);
            vo.setNextSince(last.cursorMillis());
            vo.setNextSinceId(last.id());
        }
        return vo;
    }

    private static Set<SyncCategory> parseCategories(String raw) {
        try {
            return SyncCategory.parse(raw);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(BabyLogErrorCode.SYNC_CATEGORY_INVALID, e.getMessage());
        }
    }

    /** 请求分类与可读分类取交集；请求为 null（不限分类）时直接用可读分类 */
    private static Set<SyncCategory> intersect(Set<SyncCategory> requested, Set<SyncCategory> readable) {
        if (requested == null) {
            return readable;
        }
        Set<SyncCategory> result = requested.isEmpty()
                ? java.util.EnumSet.noneOf(SyncCategory.class)
                : java.util.EnumSet.copyOf(requested);
        result.retainAll(readable);
        return result;
    }

    // ==================================================================
    // 推送
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncPushResultVO push(long userId, SyncPushCmd cmd) {
        Map<String, List<Map<String, Object>>> changes = cmd == null ? null : cmd.getChanges();
        if (changes == null || changes.isEmpty()) {
            throw new ServiceException(BabyLogErrorCode.SYNC_RECORD_INVALID, "changes 不能为空");
        }
        int total = 0;
        for (Map.Entry<String, List<Map<String, Object>>> entry : changes.entrySet()) {
            // 表标识非法是「请求不成立」，不是单条记录的问题——整批拒绝，避免客户端以为推成功了一半
            requireTable(entry.getKey());
            if (entry.getValue() != null) {
                total += entry.getValue().size();
            }
        }
        if (total == 0) {
            throw new ServiceException(BabyLogErrorCode.SYNC_RECORD_INVALID, "changes 不能为空");
        }
        if (total > MAX_PUSH_RECORDS) {
            throw new ServiceException(BabyLogErrorCode.SYNC_PAYLOAD_TOO_LARGE,
                    "单次推送最多 " + MAX_PUSH_RECORDS + " 条，本次 " + total + " 条");
        }

        // 归属放行（家庭维度）与写拦截共用一份范围：本次 push 只解析一次
        SyncAccessScope scope = accessScopeResolver.resolveAccess(userId);

        SyncPushResultVO result = new SyncPushResultVO();
        result.setTotal(total);
        result.setServerTime(SyncCursor.now());

        for (Map.Entry<String, List<Map<String, Object>>> entry : changes.entrySet()) {
            SyncTable table = requireTable(entry.getKey());
            List<Map<String, Object>> rows = entry.getValue();
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            pushTable(table, userId, scope, rows, result);
        }
        log.info("[BABYLOG][sync] push userId={} device={} total={} upserted={} skipped={} rejected={}",
                userId, cmd == null ? null : cmd.getDeviceUuid(), result.getTotal(),
                result.getUpserted(), result.getSkipped(), result.getRejected().size());
        return result;
    }

    /**
     * 一组（同一张表的）记录：先整体预查归属与版本，再逐条裁决。
     *
     * <p>裁决顺序：id/version 合法性 → 内置产品段（8345）→ 服务端管理表（8350）→
     * 归属（家庭维度放行，范围外 8344 换 ID 重推）→ 版本门禁（skipped）→ 写权限（8343）→ 落库。
     */
    private void pushTable(SyncTable table, long userId, SyncAccessScope scope,
                           List<Map<String, Object>> rows, SyncPushResultVO result) {
        List<Map<String, Object>> valid = new ArrayList<>(rows.size());
        List<Long> ids = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Long id = asLong(row == null ? null : row.get("id"));
            Long version = asLong(row == null ? null : row.get("version"));
            if (id == null || id <= 0) {
                reject(result, table, id, BabyLogErrorCode.SYNC_RECORD_INVALID, "缺少 id（主键必须由客户端生成）");
                continue;
            }
            if (version == null || version < 1) {
                reject(result, table, id, BabyLogErrorCode.SYNC_RECORD_INVALID, "缺少 version（首次推送从 1 开始）");
                continue;
            }
            if (table == SyncTable.PRODUCTS && id <= BUILTIN_PRODUCT_ID_MAX) {
                // 内置产品每台设备各推一份 → 主键必撞；且内置行内容由服务端维护，客户端本就无权改
                reject(result, table, id, BabyLogErrorCode.SYNC_BUILTIN_IMMUTABLE, null);
                continue;
            }
            if (table.isServerManaged()) {
                // 成员行/权限行是服务端管理的只读镜像：这两表的行归属 = 成员自己，归属校验拦不住
                // 整行重推——不拒收等于允许自我改 OWNER / 翻转 canWrite（M16 提权漏洞收口）
                reject(result, table, id, BabyLogErrorCode.SYNC_MEMBER_TABLE_IMMUTABLE, null);
                continue;
            }
            valid.add(row);
            ids.add(id);
        }
        if (valid.isEmpty()) {
            return;
        }

        Map<Long, SyncRecordStore.Existing> existing = store.selectExisting(table, ids);
        for (Map<String, Object> row : valid) {
            long id = asLong(row.get("id"));
            long version = asLong(row.get("version"));
            SyncRecordStore.Existing current = existing.get(id);
            if (current != null && current.userId() != userId
                    && !scope.visibleUserIds().contains(current.userId())) {
                // 行属其他账号且不在可见范围（陌生账号）：换 ID 重推才有意义，所以给专用码，
                // 别让客户端陷入「同一个 ID 无限重推」；家庭内成员的行放行（M16 家庭维度归属）
                reject(result, table, id, BabyLogErrorCode.SYNC_OWNERSHIP_CONFLICT, null);
                continue;
            }
            if (current != null && current.version() >= version) {
                // last-write-wins：库内不旧于推送，服务端优先。重推同一批（幂等重试）走的就是这条路
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            try {
                permissionGuard.checkWrite(table, scope, id, row);
            } catch (ServiceException e) {
                // 越权写（8343）：单条拒绝不拖累同批，与 8345/8342 同款丢弃类语义
                reject(result, table, id, e.getCode(), e.getMessage());
                continue;
            }
            try {
                store.upsert(table, row, userId);
                result.setUpserted(result.getUpserted() + 1);
            } catch (DataIntegrityViolationException e) {
                // 契约层面的脏数据（必填列缺失、超长等）：单条拒绝，不拖累同批
                log.warn("[BABYLOG][sync] 写入被库拒绝 table={} id={} reason={}", table.apiName(), id,
                        e.getMostSpecificCause().getMessage());
                reject(result, table, id, BabyLogErrorCode.SYNC_RECORD_INVALID,
                        "字段不满足库约束：" + e.getMostSpecificCause().getMessage());
            }
        }
    }

    private static SyncTable requireTable(String apiName) {
        return SyncTable.byApiName(apiName)
                .orElseThrow(() -> new ServiceException(BabyLogErrorCode.SYNC_TABLE_UNKNOWN,
                        "未知同步表：" + apiName));
    }

    private static void reject(SyncPushResultVO result, SyncTable table, Long id,
                               BabyLogErrorCode code, String message) {
        result.getRejected().add(SyncPushRejectVO.of(table.apiName(), id, code.getCode(),
                message == null ? code.getMsg() : message));
    }

    /** guard 拋出的业务异常专用：码与文案都来自异常本体（ServiceException 的 message 恒非空） */
    private static void reject(SyncPushResultVO result, SyncTable table, Long id, int code, String message) {
        result.getRejected().add(SyncPushRejectVO.of(table.apiName(), id, code, message));
    }

    /** JSON 数字可能是 Integer/Long/BigDecimal，统一收敛成 Long（雪花 ID 超出 Integer 范围） */
    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
