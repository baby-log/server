package com.babylog.biz.summary.service.impl;

import com.babylog.biz.entity.BlChild;
import com.babylog.biz.entity.BlDiaper;
import com.babylog.biz.entity.BlFeeding;
import com.babylog.biz.entity.BlSleep;
import com.babylog.biz.mapper.BlChildMapper;
import com.babylog.biz.mapper.BlDiaperMapper;
import com.babylog.biz.mapper.BlFeedingMapper;
import com.babylog.biz.mapper.BlSleepMapper;
import com.babylog.biz.summary.vo.TodaySummaryVO;
import com.babylog.biz.sync.support.SyncAccessScope;
import com.babylog.biz.sync.support.SyncAccessScopeResolver;
import com.babylog.biz.sync.support.SyncCategory;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 今日汇总额的单测：聚合算术、权限减窄、最近动态排序截断、进行中睡眠截断。
 * 可见范围的 SQL 过滤（user_id IN / started_at >= dayStart）属 wrapper 组装，由 dev 冒烟覆盖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TodaySummaryServiceImplTest {

    private static final long USER_ID = 1001L;
    private static final long CHILD_ID = 9L;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 单测 JVM 没有 MP 启动引导，lambda wrapper 的列解析需要实体的 TableInfo 缓存（FamilyServiceImplTest 同款）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BlChild.class);
        TableInfoHelper.initTableInfo(assistant, BlFeeding.class);
        TableInfoHelper.initTableInfo(assistant, BlDiaper.class);
        TableInfoHelper.initTableInfo(assistant, BlSleep.class);
    }

    @Mock
    private SyncAccessScopeResolver scopeResolver;
    @Mock
    private BlChildMapper childMapper;
    @Mock
    private BlFeedingMapper feedingMapper;
    @Mock
    private BlDiaperMapper diaperMapper;
    @Mock
    private BlSleepMapper sleepMapper;

    private TodaySummaryServiceImpl service() {
        return new TodaySummaryServiceImpl(scopeResolver, childMapper,
                feedingMapper, diaperMapper, sleepMapper);
    }

    @Test
    void today_aggregatesCountsAndAmounts() {
        when(scopeResolver.resolveAccess(USER_ID)).thenReturn(SyncAccessScope.singleUser(USER_ID));
        when(childMapper.selectList(any())).thenReturn(List.of(child(CHILD_ID)));
        long now = System.currentTimeMillis();
        when(feedingMapper.selectList(any())).thenReturn(List.of(
                feeding("BOTTLE", 120, "ML", now - 7000_000),
                feeding("BOTTLE", 90, "ML", now - 5000_000),
                feeding("BREAST_LEFT", null, null, now - 3000_000),
                feeding("SOLID", 30, "GRAM", now - 1000_000)));
        when(diaperMapper.selectList(any())).thenReturn(List.of(
                diaper("WET", now - 6000_000),
                diaper("BOTH", now - 2000_000)));
        when(sleepMapper.selectList(any())).thenReturn(List.of(
                sleep(now - 7200_000, now - 5400_000)));

        TodaySummaryVO vo = service().today(USER_ID);

        TodaySummaryVO.ChildSummaryVO summary = vo.getChildren().get(0);
        assertThat(summary.getChildId()).isEqualTo(CHILD_ID);

        TodaySummaryVO.FeedingSummaryVO feeding = summary.getFeeding();
        assertThat(feeding.getCount()).isEqualTo(4);
        // 辅食 30 GRAM 不计入毫升合计
        assertThat(feeding.getTotalMl()).isEqualTo(210);
        assertThat(feeding.getBreastCount()).isEqualTo(1);
        assertThat(feeding.getSolidCount()).isEqualTo(1);

        TodaySummaryVO.DiaperSummaryVO diaper = summary.getDiaper();
        assertThat(diaper.getCount()).isEqualTo(2);
        assertThat(diaper.getWet()).isEqualTo(2);
        assertThat(diaper.getSoiled()).isEqualTo(1);

        TodaySummaryVO.SleepSummaryVO sleep = summary.getSleep();
        assertThat(sleep.getCount()).isEqualTo(1);
        assertThat(sleep.getDurationS()).isEqualTo(1800);
        assertThat(sleep.isOngoing()).isFalse();
    }

    @Test
    void today_truncatesOngoingSleepToNow() {
        when(scopeResolver.resolveAccess(USER_ID)).thenReturn(SyncAccessScope.singleUser(USER_ID));
        when(childMapper.selectList(any())).thenReturn(List.of(child(CHILD_ID)));
        long now = System.currentTimeMillis();
        when(feedingMapper.selectList(any())).thenReturn(List.of());
        when(diaperMapper.selectList(any())).thenReturn(List.of());
        // 一小时前入睡、未醒：时长按「到当前时刻」截断
        when(sleepMapper.selectList(any())).thenReturn(List.of(sleep(now - 3600_000, null)));

        TodaySummaryVO.SleepSummaryVO sleep = service().today(USER_ID).getChildren().get(0).getSleep();

        assertThat(sleep.isOngoing()).isTrue();
        assertThat(sleep.getDurationS()).isBetween(3599L, 3605L);
    }

    @Test
    void today_nullifiesCategoryWhenPermissionNarrowed() {
        // 显式权限行把 FEEDING 读权限减窄（can_read=0）：喂养段不查、返回 null，动态流也无喂养条目
        SyncAccessScope narrowed = new SyncAccessScope(55L, "VIEWER", List.of(USER_ID),
                EnumSet.of(SyncCategory.CARE), EnumSet.noneOf(SyncCategory.class));
        when(scopeResolver.resolveAccess(USER_ID)).thenReturn(narrowed);
        when(childMapper.selectList(any())).thenReturn(List.of(child(CHILD_ID)));
        when(diaperMapper.selectList(any())).thenReturn(List.of(diaper("WET", 1000L)));
        when(sleepMapper.selectList(any())).thenReturn(List.of());

        TodaySummaryVO.ChildSummaryVO summary = service().today(USER_ID).getChildren().get(0);

        assertThat(summary.getFeeding()).isNull();
        assertThat(summary.getDiaper()).isNotNull();
        assertThat(summary.getSleep()).isNotNull();
        verify(feedingMapper, never()).selectList(any());
        assertThat(summary.getRecent()).hasSize(1);
        assertThat(summary.getRecent().get(0).getCategory()).isEqualTo("DIAPER");
    }

    @Test
    void today_mergesRecentAcrossCategoriesSortedDescWithLimit() {
        when(scopeResolver.resolveAccess(USER_ID)).thenReturn(SyncAccessScope.singleUser(USER_ID));
        when(childMapper.selectList(any())).thenReturn(List.of(child(CHILD_ID)));
        List<BlFeeding> feedings = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            feedings.add(feeding("BOTTLE", 100, "ML", 2000L + i));
        }
        List<BlDiaper> diapers = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            diapers.add(diaper("WET", 3000L + i));
        }
        when(feedingMapper.selectList(any())).thenReturn(feedings);
        when(diaperMapper.selectList(any())).thenReturn(diapers);
        when(sleepMapper.selectList(any())).thenReturn(List.of(sleep(1000L, 1500L)));

        List<TodaySummaryVO.RecentItemVO> recent = service().today(USER_ID)
                .getChildren().get(0).getRecent();

        // 6 喂养 + 4 尿布 + 1 睡眠 = 11 条，截前 10；倒序：最新的是 3003 的尿布
        assertThat(recent).hasSize(10);
        assertThat(recent.get(0).getCategory()).isEqualTo("DIAPER");
        assertThat(recent.get(0).getTime()).isEqualTo(3003L);
        // 倒序整体成立
        for (int i = 1; i < recent.size(); i++) {
            assertThat(recent.get(i - 1).getTime()).isGreaterThanOrEqualTo(recent.get(i).getTime());
        }
        // 「谁记的」成员显示名直接透传
        assertThat(recent.get(0).getCreatedBy()).isEqualTo("妈妈");
    }

    @Test
    void today_returnsEmptyChildrenWhenNoVisibleChild() {
        when(scopeResolver.resolveAccess(USER_ID)).thenReturn(SyncAccessScope.singleUser(USER_ID));
        when(childMapper.selectList(any())).thenReturn(List.of());

        TodaySummaryVO vo = service().today(USER_ID);

        assertThat(vo.getChildren()).isEmpty();
        assertThat(vo.getDayStart()).isNotNull();
        // 没宝宝也就没有后续查询
        verify(feedingMapper, never()).selectList(any());
    }

    // ---------------- 辅助 ----------------

    private static BlChild child(Long id) {
        BlChild child = new BlChild();
        child.setId(id);
        child.setUserId(USER_ID);
        child.setName("筱筱");
        child.setGender("FEMALE");
        child.setBirthday(1735689600000L);
        child.setArchived(0);
        return child;
    }

    private static BlFeeding feeding(String type, Integer amountMl, String unit, long startedAt) {
        BlFeeding row = new BlFeeding();
        row.setChildId(CHILD_ID);
        row.setUserId(USER_ID);
        row.setType(type);
        row.setAmountMl(amountMl);
        row.setAmountUnit(unit);
        row.setStartedAt(startedAt);
        row.setCreatedBy("妈妈");
        return row;
    }

    private static BlDiaper diaper(String type, long startedAt) {
        BlDiaper row = new BlDiaper();
        row.setChildId(CHILD_ID);
        row.setUserId(USER_ID);
        row.setType(type);
        row.setStartedAt(startedAt);
        row.setCreatedBy("妈妈");
        return row;
    }

    private static BlSleep sleep(Long startedAt, Long endedAt) {
        BlSleep row = new BlSleep();
        row.setChildId(CHILD_ID);
        row.setUserId(USER_ID);
        row.setStartedAt(startedAt);
        row.setEndedAt(endedAt);
        row.setCreatedBy("妈妈");
        return row;
    }
}
