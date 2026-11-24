package com.babylog.biz.ai.service;

import com.babylog.biz.ai.config.BabyLogAiQuotaProperties;
import com.babylog.biz.ai.enums.AiFeature;
import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.entity.BlAiQuota;
import com.babylog.biz.entity.BlFamilyMember;
import com.babylog.biz.mapper.BlAiQuotaMapper;
import com.babylog.biz.mapper.BlFamilyMemberMapper;
import com.babylog.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 额度计次单测（K5 / M73，范围 A）。
 *
 * <p>覆盖 {@code 23} §五 K5 验证清单里的「配额边界（耗尽 / 重置 / 并发计次不超额）」：
 * <ul>
 *   <li><b>并发不超额</b>：占位是<b>单条条件更新</b>（{@code quota_used < quota_limit}），
 *       影响行数 0 即超限 ⇒ 不存在「先读后写 +1」的窗口（用捕获 wrapper 的 SQL 片段锁死这条设计）；</li>
 *   <li><b>耗尽</b>：条件更新返回 0 ⇒ {@code 8460}，且不再写库；</li>
 *   <li><b>重置</b>：{@code reset_at} 已到期才归零，条件更新天然幂等；窗口按 DAILY/WEEKLY 顺延；</li>
 *   <li><b>回滚</b>：只在 {@code quota_used > 0} 时减，绝不减成负数；</li>
 *   <li><b>家庭口径</b>：有 ACTIVE 家庭成员 ⇒ {@code F:familyId}（家庭共用），无 ⇒ {@code U:userId}。</li>
 * </ul>
 *
 * <p>真并发（两个线程抢同一行）属集成项，本单测不伪造——它锁的是「实现只有一种写法能通过」。
 */
@ExtendWith(MockitoExtension.class)
class AiQuotaServiceTest {

    private static final long USER = 1001L;
    private static final long FAMILY = 9L;

    @Mock
    private BlAiQuotaMapper quotaMapper;
    @Mock
    private BlFamilyMemberMapper memberMapper;

    private BabyLogAiQuotaProperties properties;
    private AiQuotaService service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 单测 JVM 没有 MP 启动引导：LambdaUpdateWrapper.eq/set(SFunction) 会立即解析方法引用
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BlAiQuota.class);
        TableInfoHelper.initTableInfo(assistant, BlFamilyMember.class);
    }

    @BeforeEach
    void setUp() {
        properties = new BabyLogAiQuotaProperties();
        BabyLogAiQuotaProperties.FeatureQuota parse = new BabyLogAiQuotaProperties.FeatureQuota();
        parse.setLimit(20);
        parse.setWindow(BabyLogAiQuotaProperties.Window.DAILY);
        properties.getFeatures().put(AiFeature.PARSE_RECORD.code(), parse);
        service = new AiQuotaService(quotaMapper, memberMapper, properties);
        // 默认：无家庭 ⇒ 个人额度；额度行已存在
        lenient().when(memberMapper.selectList(any())).thenReturn(List.of());
        lenient().when(quotaMapper.selectCount(any())).thenReturn(1L);
    }

    // ==================================================================
    // 1. 占位成功 + 剩余次数
    // ==================================================================

    @Test
    void reserveIncrementsAndReturnsRemaining() {
        when(quotaMapper.update(any(), any())).thenReturn(1);
        BlAiQuota row = new BlAiQuota();
        row.setQuotaUsed(1);
        when(quotaMapper.selectOne(any())).thenReturn(row);

        int remaining = service.reserve(USER, AiFeature.PARSE_RECORD);

        assertThat(remaining).isEqualTo(19); // limit 20 - used 1
        assertThat(service.scopeKeyOf(USER)).isEqualTo("U:1001");
    }

    // ==================================================================
    // 2. 并发不超额：条件更新是唯一防线
    // ==================================================================

    @Test
    void reserveUsesSingleConditionalUpdateSoConcurrentCallsCannotOverrun() {
        when(quotaMapper.selectOne(any())).thenReturn(null);
        List<String> sqls = new ArrayList<>();
        doAnswer(inv -> {
            LambdaUpdateWrapper<BlAiQuota> wrapper = inv.getArgument(1);
            // WHERE 片段在 getSqlSegment()、SET 片段在 getSqlSet()，两段合起来才是完整语义
            sqls.add(wrapper.getSqlSegment() + " || " + wrapper.getSqlSet());
            return 1;
        }).when(quotaMapper).update(any(), any());

        service.reserve(USER, AiFeature.PARSE_RECORD);

        // 占位语句必须自带「还没到上限」的条件——这就是「先查后写」做法的替代品
        assertThat(String.join(" | ", sqls))
                .as("占位必须是带条件的单条更新，否则并发会超额")
                .contains("quota_used < quota_limit")
                .contains("quota_used = quota_used + 1");
    }

    @Test
    void reserveThrowsWhenConditionalUpdateAffectsNoRow() {
        // 第一次 update 是 resetIfDue（返回 1），第二次是占位（返回 0 = 额度已满）
        when(quotaMapper.update(any(), any())).thenReturn(1, 0);

        assertThatThrownBy(() -> service.reserve(USER, AiFeature.PARSE_RECORD))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(BabyLogErrorCode.AI_QUOTA_EXCEEDED.getCode()));
        // 超限后不得再读剩余额度（没有意义，也可能误导客户端显示「0 次可用」）
        verify(quotaMapper, never()).selectOne(any());
    }

    // ==================================================================
    // 3. 重置：到期才归零，窗口顺延
    // ==================================================================

    @Test
    void resetOnlyTouchesRowsWhoseWindowExpired() {
        AtomicReference<String> resetSql = new AtomicReference<>();
        doAnswer(inv -> {
            LambdaUpdateWrapper<BlAiQuota> wrapper = inv.getArgument(1);
            String sql = wrapper.getSqlSegment();
            if (sql.contains("reset_at")) {
                resetSql.set(sql);
            }
            return 1;
        }).when(quotaMapper).update(any(), any());
        when(quotaMapper.selectOne(any())).thenReturn(null);

        service.reserve(USER, AiFeature.PARSE_RECORD);

        assertThat(resetSql.get()).as("归零必须带 reset_at 到期条件，否则每次调用都会清零").contains("reset_at");
    }

    @Test
    void nextResetAtAlignsToNextDayForDailyWindow() {
        BabyLogAiQuotaProperties.FeatureQuota daily = new BabyLogAiQuotaProperties.FeatureQuota();
        daily.setWindow(BabyLogAiQuotaProperties.Window.DAILY);
        LocalDateTime next = AiQuotaService.nextResetAt(daily);
        assertThat(next).isEqualTo(LocalDate.now().plusDays(1).atStartOfDay());
    }

    @Test
    void nextResetAtAlignsToNextMondayForWeeklyWindow() {
        BabyLogAiQuotaProperties.FeatureQuota weekly = new BabyLogAiQuotaProperties.FeatureQuota();
        weekly.setWindow(BabyLogAiQuotaProperties.Window.WEEKLY);
        LocalDateTime next = AiQuotaService.nextResetAt(weekly);
        assertThat(next.getDayOfWeek().getValue()).isEqualTo(1); // 周一
        assertThat(next.getHour()).isZero();
        assertThat(next).isAfter(LocalDateTime.now());
    }

    // ==================================================================
    // 4. 不限次：只计次，不设条件、不返回 0
    // ==================================================================

    @Test
    void unknownFeatureMeansUnlimitedButStillCounts() {
        List<String> sqls = new ArrayList<>();
        doAnswer(inv -> {
            LambdaUpdateWrapper<BlAiQuota> wrapper = inv.getArgument(1);
            sqls.add(wrapper.getSqlSegment() + " || " + wrapper.getSqlSet());
            return 1;
        }).when(quotaMapper).update(any(), any());

        int remaining = service.reserve(USER, AiFeature.HANDWRITING_IMPORT);

        assertThat(remaining).isEqualTo(-1); // -1 = 不限次
        assertThat(String.join(" | ", sqls))
                .contains("quota_used = quota_used + 1")
                .doesNotContain("quota_limit");
        verify(quotaMapper, never()).selectOne(any());
    }

    // ==================================================================
    // 5. 回滚：只在 > 0 时减
    // ==================================================================

    @Test
    void releaseNeverGoesNegative() {
        AtomicReference<String> sql = new AtomicReference<>();
        doAnswer(inv -> {
            sql.set(((LambdaUpdateWrapper<BlAiQuota>) inv.getArgument(1)).getSqlSegment());
            return 1;
        }).when(quotaMapper).update(any(), any());

        service.release(USER, AiFeature.PARSE_RECORD);

        assertThat(sql.get()).contains("quota_used > 0");
    }

    // ==================================================================
    // 6. 家庭口径：一家庭成员共用一份额度
    // ==================================================================

    @Test
    void scopeKeyUsesFamilyWhenMemberIsActive() {
        BlFamilyMember member = new BlFamilyMember();
        member.setFamilyId(FAMILY);
        member.setJoinedAt(1L);
        when(memberMapper.selectList(any())).thenReturn(List.of(member));

        assertThat(service.scopeKeyOf(USER)).isEqualTo("F:9");
    }

    @Test
    void scopeKeyFallsBackToUserWhenNoFamily() {
        when(memberMapper.selectList(any())).thenReturn(List.of());
        assertThat(service.scopeKeyOf(USER)).isEqualTo("U:1001");
    }

    // ==================================================================
    // 7. 首建：并发首建不崩、复用既有行
    // ==================================================================

    @Test
    void firstUseInsertsQuotaRowWithConfiguredLimit() {
        when(quotaMapper.selectCount(any())).thenReturn(0L);
        ArgumentCaptor<BlAiQuota> captor = ArgumentCaptor.forClass(BlAiQuota.class);
        when(quotaMapper.update(any(), any())).thenReturn(1);
        when(quotaMapper.selectOne(any())).thenReturn(null);

        service.reserve(USER, AiFeature.PARSE_RECORD);

        verify(quotaMapper).insert(captor.capture());
        BlAiQuota row = captor.getValue();
        assertThat(row.getScopeKey()).isEqualTo("U:1001");
        assertThat(row.getFeature()).isEqualTo("PARSE_RECORD");
        assertThat(row.getQuotaLimit()).isEqualTo(20);
        assertThat(row.getQuotaUsed()).isZero();
        assertThat(row.getResetAt()).isNotNull();
    }

    @Test
    void duplicateInsertRaceFallsThroughToConditionalUpdate() {
        when(quotaMapper.selectCount(any())).thenReturn(0L);
        when(quotaMapper.insert(any(BlAiQuota.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_scope_feature"));
        when(quotaMapper.update(any(), any())).thenReturn(1);
        when(quotaMapper.selectOne(any())).thenReturn(null);

        int remaining = service.reserve(USER, AiFeature.PARSE_RECORD);

        // 读不到行时按「本次已占位 1 次」给保守值（20-1），不冒充「还有 20 次」
        assertThat(remaining).isEqualTo(19);
        verify(quotaMapper, times(1)).insert(any(BlAiQuota.class)); // 不重试、不递归
    }
}
