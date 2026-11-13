package com.babylog.biz.summary.service.impl;

import com.babylog.biz.entity.BlChild;
import com.babylog.biz.entity.BlDiaper;
import com.babylog.biz.entity.BlFeeding;
import com.babylog.biz.entity.BlSleep;
import com.babylog.biz.mapper.BlChildMapper;
import com.babylog.biz.mapper.BlDiaperMapper;
import com.babylog.biz.mapper.BlFeedingMapper;
import com.babylog.biz.mapper.BlSleepMapper;
import com.babylog.biz.summary.service.TodaySummaryService;
import com.babylog.biz.summary.vo.TodaySummaryVO;
import com.babylog.biz.sync.support.SyncAccessScope;
import com.babylog.biz.sync.support.SyncAccessScopeResolver;
import com.babylog.biz.sync.support.SyncCategory;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 今日汇总聚合实现。
 *
 * <p><b>「今日」的口径</b>：服务端 JVM 时区当天 00:00 起的 epoch millis 窗口，
 * 按 {@code started_at} 过滤（与 App 端「按发生时刻」一致，不看创建/同步时刻——
 * 补录昨天的事不会污染今天）。睡眠跨夜段（昨晚入睡、今天未醒）<b>不算今天</b>：
 * 轻入口要的是「今天发生了什么」，跨夜归因属 App 端统计页的精细口径，不搬过来。
 * 进行中的一段按「到当前时刻」截断计入时长。
 *
 * <p><b>可见范围与权限</b>：复用 {@link SyncAccessScopeResolver}（与 /sync/changes 同一裁决），
 * 不另造第二套判断。分类读权限被显式减窄（bl_member_permissions 行 can_read=0）时，
 * 对应聚合段返回 null——小程序显示「无权限查看」，与同步通道行为一致，不能绕过。
 *
 * <p><b>查询形态</b>：4 张表各一次 {@code user_id IN (visible)} + 时间窗过滤，
 * 内存按 childId 分组聚合。一天全家几十条记录，量级无压力，不为此写聚合 SQL。
 */
@Service
public class TodaySummaryServiceImpl implements TodaySummaryService {

    private static final int RECENT_LIMIT = 10;

    private final SyncAccessScopeResolver scopeResolver;
    private final BlChildMapper childMapper;
    private final BlFeedingMapper feedingMapper;
    private final BlDiaperMapper diaperMapper;
    private final BlSleepMapper sleepMapper;

    public TodaySummaryServiceImpl(SyncAccessScopeResolver scopeResolver,
                                   BlChildMapper childMapper,
                                   BlFeedingMapper feedingMapper,
                                   BlDiaperMapper diaperMapper,
                                   BlSleepMapper sleepMapper) {
        this.scopeResolver = scopeResolver;
        this.childMapper = childMapper;
        this.feedingMapper = feedingMapper;
        this.diaperMapper = diaperMapper;
        this.sleepMapper = sleepMapper;
    }

    @Override
    public TodaySummaryVO today(long userId) {
        SyncAccessScope scope = scopeResolver.resolveAccess(userId);
        long dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        long now = System.currentTimeMillis();

        // 可见宝宝：未归档（archived ≠ 1），按业务创建时刻正序（与 App 端宝宝列表口径一致）
        List<BlChild> children = childMapper.selectList(Wrappers.<BlChild>lambdaQuery()
                .in(BlChild::getUserId, scope.visibleUserIds())
                .and(w -> w.isNull(BlChild::getArchived).or().ne(BlChild::getArchived, 1))
                .orderByAsc(BlChild::getCreatedAt));
        if (children.isEmpty()) {
            TodaySummaryVO vo = new TodaySummaryVO();
            vo.setDayStart(dayStart);
            vo.setChildren(List.of());
            return vo;
        }

        List<Long> visibleUserIds = scope.visibleUserIds();
        List<BlFeeding> feedings = scope.readableCategories().contains(SyncCategory.FEEDING)
                ? feedingMapper.selectList(Wrappers.<BlFeeding>lambdaQuery()
                        .in(BlFeeding::getUserId, visibleUserIds)
                        .ge(BlFeeding::getStartedAt, dayStart))
                : List.of();
        List<BlDiaper> diapers = scope.readableCategories().contains(SyncCategory.CARE)
                ? diaperMapper.selectList(Wrappers.<BlDiaper>lambdaQuery()
                        .in(BlDiaper::getUserId, visibleUserIds)
                        .ge(BlDiaper::getStartedAt, dayStart))
                : List.of();
        List<BlSleep> sleeps = scope.readableCategories().contains(SyncCategory.CARE)
                ? sleepMapper.selectList(Wrappers.<BlSleep>lambdaQuery()
                        .in(BlSleep::getUserId, visibleUserIds)
                        .ge(BlSleep::getStartedAt, dayStart))
                : List.of();

        TodaySummaryVO vo = new TodaySummaryVO();
        vo.setDayStart(dayStart);
        List<TodaySummaryVO.ChildSummaryVO> childSummaries = new ArrayList<>(children.size());
        for (BlChild child : children) {
            childSummaries.add(summarizeChild(child,
                    filterByChild(feedings, child.getId()),
                    filterByChild(diapers, child.getId()),
                    filterByChild(sleeps, child.getId()), now,
                    scope.readableCategories().contains(SyncCategory.FEEDING),
                    scope.readableCategories().contains(SyncCategory.CARE)));
        }
        vo.setChildren(childSummaries);
        return vo;
    }

    private TodaySummaryVO.ChildSummaryVO summarizeChild(BlChild child,
                                                         List<BlFeeding> feedings,
                                                         List<BlDiaper> diapers,
                                                         List<BlSleep> sleeps,
                                                         long now,
                                                         boolean feedingReadable,
                                                         boolean careReadable) {
        TodaySummaryVO.ChildSummaryVO summary = new TodaySummaryVO.ChildSummaryVO();
        summary.setChildId(child.getId());
        summary.setName(child.getName());
        summary.setGender(child.getGender());
        summary.setBirthday(child.getBirthday());

        if (feedingReadable) {
            TodaySummaryVO.FeedingSummaryVO feeding = new TodaySummaryVO.FeedingSummaryVO();
            int totalMl = 0;
            int breast = 0;
            int solid = 0;
            for (BlFeeding row : feedings) {
                if ("BREAST_LEFT".equals(row.getType()) || "BREAST_RIGHT".equals(row.getType())) {
                    breast++;
                } else if ("SOLID".equals(row.getType())) {
                    solid++;
                }
                // 只累计毫升口径（单位 ML 或历史行 null 兜底）；辅食 GRAM/PORTION 不硬加
                if (row.getAmountMl() != null
                        && (row.getAmountUnit() == null || "ML".equals(row.getAmountUnit()))) {
                    totalMl += row.getAmountMl();
                }
            }
            feeding.setCount(feedings.size());
            feeding.setTotalMl(totalMl);
            feeding.setBreastCount(breast);
            feeding.setSolidCount(solid);
            summary.setFeeding(feeding);
        }

        if (careReadable) {
            TodaySummaryVO.DiaperSummaryVO diaper = new TodaySummaryVO.DiaperSummaryVO();
            int wet = 0;
            int soiled = 0;
            for (BlDiaper row : diapers) {
                if ("WET".equals(row.getType()) || "BOTH".equals(row.getType())) {
                    wet++;
                }
                if ("SOILED".equals(row.getType()) || "BOTH".equals(row.getType())) {
                    soiled++;
                }
            }
            diaper.setCount(diapers.size());
            diaper.setWet(wet);
            diaper.setSoiled(soiled);
            summary.setDiaper(diaper);

            TodaySummaryVO.SleepSummaryVO sleep = new TodaySummaryVO.SleepSummaryVO();
            long durationS = 0;
            boolean ongoing = false;
            for (BlSleep row : sleeps) {
                long end = row.getEndedAt() != null ? row.getEndedAt() : now;
                long start = row.getStartedAt() != null ? row.getStartedAt() : end;
                if (row.getEndedAt() == null) {
                    ongoing = true;
                }
                durationS += Math.max(0, (end - start)) / 1000;
            }
            sleep.setCount(sleeps.size());
            sleep.setDurationS(durationS);
            sleep.setOngoing(ongoing);
            summary.setSleep(sleep);
        }

        summary.setRecent(recentItems(feedings, diapers, sleeps));
        return summary;
    }

    /** 三类合并按发生时刻倒序，截前 10 条（只放可读分类，不可读的不进动态流） */
    private static List<TodaySummaryVO.RecentItemVO> recentItems(List<BlFeeding> feedings,
                                                                 List<BlDiaper> diapers,
                                                                 List<BlSleep> sleeps) {
        record Entry(long time, TodaySummaryVO.RecentItemVO item) {
        }
        List<Entry> entries = new ArrayList<>();
        for (BlFeeding row : feedings) {
            TodaySummaryVO.RecentItemVO item = new TodaySummaryVO.RecentItemVO();
            item.setCategory("FEEDING");
            item.setType(row.getType());
            item.setTime(row.getStartedAt());
            item.setAmountMl(mlOf(row));
            item.setCreatedBy(row.getCreatedBy());
            entries.add(new Entry(nvl(row.getStartedAt()), item));
        }
        for (BlDiaper row : diapers) {
            TodaySummaryVO.RecentItemVO item = new TodaySummaryVO.RecentItemVO();
            item.setCategory("DIAPER");
            item.setType(row.getType());
            item.setTime(row.getStartedAt());
            item.setCreatedBy(row.getCreatedBy());
            entries.add(new Entry(nvl(row.getStartedAt()), item));
        }
        for (BlSleep row : sleeps) {
            TodaySummaryVO.RecentItemVO item = new TodaySummaryVO.RecentItemVO();
            item.setCategory("SLEEP");
            item.setType("SLEEP");
            item.setTime(row.getStartedAt());
            item.setCreatedBy(row.getCreatedBy());
            entries.add(new Entry(nvl(row.getStartedAt()), item));
        }
        entries.sort(Comparator.comparingLong(Entry::time).reversed());
        List<TodaySummaryVO.RecentItemVO> result = new ArrayList<>(Math.min(entries.size(), RECENT_LIMIT));
        for (Entry entry : entries.subList(0, Math.min(entries.size(), RECENT_LIMIT))) {
            result.add(entry.item());
        }
        return result;
    }

    private static Integer mlOf(BlFeeding row) {
        if (row.getAmountMl() == null) {
            return null;
        }
        return row.getAmountUnit() == null || "ML".equals(row.getAmountUnit()) ? row.getAmountMl() : null;
    }

    private static long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private static <T> List<T> filterByChild(List<T> rows, Long childId) {
        // 已按可见范围与时间窗过滤，这里只剩按宝宝分桶
        return rows.stream()
                .filter(row -> childId.equals(childIdOf(row)))
                .toList();
    }

    private static Long childIdOf(Object row) {
        if (row instanceof BlFeeding f) {
            return f.getChildId();
        }
        if (row instanceof BlDiaper d) {
            return d.getChildId();
        }
        if (row instanceof BlSleep s) {
            return s.getChildId();
        }
        return null;
    }
}
