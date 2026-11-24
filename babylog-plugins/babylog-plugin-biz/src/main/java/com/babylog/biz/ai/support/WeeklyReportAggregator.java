package com.babylog.biz.ai.support;

import com.babylog.biz.ai.vo.AiWeeklyReportVO;
import com.babylog.biz.entity.BlDiaper;
import com.babylog.biz.entity.BlFeeding;
import com.babylog.biz.entity.BlMeasurement;
import com.babylog.biz.entity.BlPumpSession;
import com.babylog.biz.entity.BlSleep;
import com.babylog.biz.mapper.BlDiaperMapper;
import com.babylog.biz.mapper.BlFeedingMapper;
import com.babylog.biz.mapper.BlMeasurementMapper;
import com.babylog.biz.mapper.BlPumpSessionMapper;
import com.babylog.biz.mapper.BlSleepMapper;
import com.babylog.biz.sync.support.SyncAccessScope;
import com.babylog.biz.sync.support.SyncAccessScopeResolver;
import com.babylog.biz.sync.support.SyncCategory;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 育儿周报的只读聚合（K5 / M73，范围 D）。
 *
 * <p><b>只读、只读、只读</b>：5 张同步表各一次 {@code user_id IN (visibleUserIds) + child_id + 时间窗}
 * 查询，内存聚合；<b>不写任何表</b>（周报本身不落库，K6 客户端拿到就展示，需要留存由客户端自己决定）。
 *
 * <p><b>数字不经过模型</b>：模型只拿这份汇总去写文案。这样即使模型胡说，硬数字仍可核对；
 * 反过来说，文案里的每个数都来自这里的统计，模型没有「编数据」的入口。
 *
 * <p><b>权限不绕过</b>：分类读权限被显式减窄（{@code bl_member_permissions.can_read=0}）时
 * 该分类的数字保持 0，且不出现在 {@code readableCategories} 里——与 {@code /sync/changes} 的
 * 可见性裁决同源（都走 {@link SyncAccessScopeResolver}），不另造第二套判断。
 */
@Component
public class WeeklyReportAggregator {

    private final SyncAccessScopeResolver scopeResolver;
    private final BlFeedingMapper feedingMapper;
    private final BlDiaperMapper diaperMapper;
    private final BlSleepMapper sleepMapper;
    private final BlPumpSessionMapper pumpMapper;
    private final BlMeasurementMapper measurementMapper;

    public WeeklyReportAggregator(SyncAccessScopeResolver scopeResolver,
                                  BlFeedingMapper feedingMapper,
                                  BlDiaperMapper diaperMapper,
                                  BlSleepMapper sleepMapper,
                                  BlPumpSessionMapper pumpMapper,
                                  BlMeasurementMapper measurementMapper) {
        this.scopeResolver = scopeResolver;
        this.feedingMapper = feedingMapper;
        this.diaperMapper = diaperMapper;
        this.sleepMapper = sleepMapper;
        this.pumpMapper = pumpMapper;
        this.measurementMapper = measurementMapper;
    }

    /** 聚合结果：硬数字 + 本次账号在该宝宝上可读的分类 */
    public record Aggregation(AiWeeklyReportVO.Stats stats, List<String> readableCategories) {
    }

    /**
     * 聚合一周（半开区间 {@code [from, to)}）。
     *
     * <p>口径与「今日汇总」一致：按<b>业务发生时刻</b>过滤（补录昨天的事不会污染本周），
     * 进行中的睡眠段按「到当前时刻」截断计入。
     */
    public Aggregation aggregate(long userId, long childId, long from, long to) {
        SyncAccessScope scope = scopeResolver.resolveAccess(userId);
        List<Long> visible = scope.visibleUserIds();
        List<String> readable = new ArrayList<>();
        AiWeeklyReportVO.Stats stats = new AiWeeklyReportVO.Stats();

        if (scope.readableCategories().contains(SyncCategory.FEEDING)) {
            readable.add(SyncCategory.FEEDING.name());
            aggregateFeeding(stats, visible, childId, from, to);
            aggregatePump(stats, visible, childId, from, to);
        }
        if (scope.readableCategories().contains(SyncCategory.CARE)) {
            readable.add(SyncCategory.CARE.name());
            aggregateDiaper(stats, visible, childId, from, to);
            aggregateSleep(stats, visible, childId, from, to);
        }
        if (scope.readableCategories().contains(SyncCategory.HEALTH)) {
            readable.add(SyncCategory.HEALTH.name());
            aggregateMeasurement(stats, visible, childId, from, to);
        }
        if (scope.readableCategories().contains(SyncCategory.MEDICAL)) {
            readable.add(SyncCategory.MEDICAL.name());
        }
        return new Aggregation(stats, readable);
    }

    private void aggregateFeeding(AiWeeklyReportVO.Stats stats, List<Long> visible,
                                  long childId, long from, long to) {
        List<BlFeeding> rows = feedingMapper.selectList(Wrappers.<BlFeeding>lambdaQuery()
                .in(BlFeeding::getUserId, visible)
                .eq(BlFeeding::getChildId, childId)
                .ge(BlFeeding::getStartedAt, from)
                .lt(BlFeeding::getStartedAt, to));
        int breast = 0;
        int solid = 0;
        int ml = 0;
        for (BlFeeding row : rows) {
            if ("BREAST_LEFT".equals(row.getType()) || "BREAST_RIGHT".equals(row.getType())) {
                breast++;
            } else if ("SOLID".equals(row.getType())) {
                solid++;
            }
            // 只累计毫升口径（GRAMS/PORTION 之类不硬加，避免把「半碗」当毫升）
            if (row.getAmountMl() != null
                    && (row.getAmountUnit() == null || "ML".equals(row.getAmountUnit()))) {
                ml += row.getAmountMl();
            }
        }
        stats.setFeedingCount(rows.size());
        stats.setBreastSessions(breast);
        stats.setSolidCount(solid);
        stats.setFeedingMl(ml);
    }

    private void aggregatePump(AiWeeklyReportVO.Stats stats, List<Long> visible,
                               long childId, long from, long to) {
        List<BlPumpSession> rows = pumpMapper.selectList(Wrappers.<BlPumpSession>lambdaQuery()
                .in(BlPumpSession::getUserId, visible)
                .eq(BlPumpSession::getChildId, childId)
                .ge(BlPumpSession::getStartedAt, from)
                .lt(BlPumpSession::getStartedAt, to));
        int ml = 0;
        for (BlPumpSession row : rows) {
            if (row.getAmountMl() != null) {
                ml += row.getAmountMl();
            }
        }
        stats.setPumpCount(rows.size());
        stats.setPumpMl(ml);
    }

    private void aggregateDiaper(AiWeeklyReportVO.Stats stats, List<Long> visible,
                                 long childId, long from, long to) {
        List<BlDiaper> rows = diaperMapper.selectList(Wrappers.<BlDiaper>lambdaQuery()
                .in(BlDiaper::getUserId, visible)
                .eq(BlDiaper::getChildId, childId)
                .ge(BlDiaper::getStartedAt, from)
                .lt(BlDiaper::getStartedAt, to));
        int wet = 0;
        int soiled = 0;
        for (BlDiaper row : rows) {
            if ("WET".equals(row.getType()) || "BOTH".equals(row.getType())) {
                wet++;
            }
            if ("SOILED".equals(row.getType()) || "BOTH".equals(row.getType())) {
                soiled++;
            }
        }
        stats.setDiaperCount(rows.size());
        stats.setDiaperWet(wet);
        stats.setDiaperSoiled(soiled);
    }

    /** 睡眠：按与窗口的重叠部分计分钟；进行中的一段按「到当前时刻」截断（同今日汇总口径） */
    private void aggregateSleep(AiWeeklyReportVO.Stats stats, List<Long> visible,
                                long childId, long from, long to) {
        List<BlSleep> rows = sleepMapper.selectList(Wrappers.<BlSleep>lambdaQuery()
                .in(BlSleep::getUserId, visible)
                .eq(BlSleep::getChildId, childId)
                .lt(BlSleep::getStartedAt, to)
                .and(w -> w.isNull(BlSleep::getEndedAt).or().gt(BlSleep::getEndedAt, from)));
        long now = System.currentTimeMillis();
        long totalMs = 0;
        for (BlSleep row : rows) {
            long start = Math.max(from, row.getStartedAt() == null ? from : row.getStartedAt());
            long rawEnd = row.getEndedAt() == null ? Math.min(now, to) : row.getEndedAt();
            long end = Math.min(to, rawEnd);
            if (end > start) {
                totalMs += end - start;
            }
        }
        stats.setSleepCount(rows.size());
        stats.setSleepMinutes((int) (totalMs / 60_000L));
    }

    /** 测量：窗口内最近一次体重/身高（不取窗口外的，避免周报里出现「上周的数字」） */
    private void aggregateMeasurement(AiWeeklyReportVO.Stats stats, List<Long> visible,
                                      long childId, long from, long to) {
        List<BlMeasurement> rows = measurementMapper.selectList(Wrappers.<BlMeasurement>lambdaQuery()
                .in(BlMeasurement::getUserId, visible)
                .eq(BlMeasurement::getChildId, childId)
                .ge(BlMeasurement::getMeasuredAt, from)
                .lt(BlMeasurement::getMeasuredAt, to)
                .orderByDesc(BlMeasurement::getMeasuredAt));
        for (BlMeasurement row : rows) {
            if (stats.getLatestWeight() == null && "WEIGHT".equals(row.getType())) {
                stats.setLatestWeight(row.getValue());
            }
            if (stats.getLatestHeight() == null && "HEIGHT".equals(row.getType())) {
                stats.setLatestHeight(row.getValue());
            }
        }
    }
}
