package com.babylog.biz.ai.service;

import com.babylog.biz.ai.config.BabyLogAiQuotaProperties;
import com.babylog.biz.ai.enums.AiFeature;
import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.entity.BlAiQuota;
import com.babylog.biz.entity.BlFamilyMember;
import com.babylog.biz.mapper.BlAiQuotaMapper;
import com.babylog.biz.mapper.BlFamilyMemberMapper;
import com.babylog.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;

/**
 * AI 额度计次（K5 / M73，范围 A）。
 *
 * <p><b>口径（龙哥 2026-11-24 拍板）</b>：按 <b>家庭 × 功能</b> 共享一份免费额度；超限<b>拒绝</b>
 * （{@code 8460}），不发模型请求、不降级伪造内容；V3.0 不做付费入口。
 *
 * <p><b>为什么是「预占 → 失败回滚」而不是「先查后写」</b>：额度守在<b>模型调用之前</b>，
 * 防的是「并发 N 个请求同时通过检查、然后 N 个都真调了模型」——那种超额是直接烧钱。
 * 所以先原子占位，AI 调用失败（未配置 / 解析不出草稿 / 调用异常）再回滚，
 * 最终计到的次数 = 真正消耗掉的模型调用次数。
 *
 * <p><b>原子性</b>：占位是单条条件更新
 * {@code UPDATE ... SET quota_used = quota_used + 1 WHERE scope_key=? AND feature=? AND quota_used < quota_limit}，
 * 影响行数 0 即「超限或行不存在」；不做「先 select 再 +1」的裸读改写（那样并发必超额）。
 *
 * <p><b>行不存在</b>：首次使用插入一行（{@code (scope_key, feature)} 唯一键），
 * 并发首建落败方吞 {@link DuplicateKeyException} 后走同一条占位语句，不重试、不递归。
 *
 * <p><b>K6-S / M76 动态额度（龙哥门禁 C 授权）</b>：三参 {@code reserve(..., limitOverride)}
 * 允许调用方传入参数中心读到的 {@code KB_QA} 上限；非 {@code null} 时用它替代 yml 值，
 * 并在占位前把已存在行的 {@code quota_limit} 同步成新值（运营后台改额度对存量行即时生效）。
 * 两参重载恒传 {@code null} ⇒ K5 的 PARSE_RECORD / WEEKLY_REPORT 行为零变更、既有单测零改动。
 */
@Service
public class AiQuotaService {

    private static final Logger log = LoggerFactory.getLogger(AiQuotaService.class);

    /** 无家庭时的 scope_key 前缀（个人额度，行为与 V2.x 单用户一致） */
    private static final String SCOPE_FAMILY = "F:";
    private static final String SCOPE_USER = "U:";

    private final BlAiQuotaMapper quotaMapper;
    private final BlFamilyMemberMapper memberMapper;
    private final BabyLogAiQuotaProperties properties;

    public AiQuotaService(BlAiQuotaMapper quotaMapper,
                          BlFamilyMemberMapper memberMapper,
                          BabyLogAiQuotaProperties properties) {
        this.quotaMapper = quotaMapper;
        this.memberMapper = memberMapper;
        this.properties = properties;
    }

    /**
     * 预占一次额度（模型调用前）。
     *
     * @return 本次占位后的剩余次数；{@code -1} 表示该功能不限次
     * @throws ServiceException {@code 8460} 额度已用完
     */
    public int reserve(long userId, AiFeature feature) {
        return reserve(userId, feature, null);
    }

    /**
     * 预占一次额度（模型调用前），可覆盖上限。
     *
     * <p>{@code limitOverride} 仅由 K6-S 的 {@code KB_QA} 使用：来自参数中心
     * {@code sys_config} 的实时值（缺失 / 非法由调用方回落 yml，传 {@code null} 即走两参语义）。
     * 传 {@code null} 时本方法与 K5 原实现逐字等价。
     *
     * @return 本次占位后的剩余次数；{@code -1} 表示该功能不限次
     * @throws ServiceException {@code 8460} 额度已用完
     */
    public int reserve(long userId, AiFeature feature, Integer limitOverride) {
        String scopeKey = scopeKeyOf(userId);
        BabyLogAiQuotaProperties.FeatureQuota config = properties.of(feature);
        int limit = limitOverride != null ? limitOverride : (config == null ? 0 : config.getLimit());

        ensureRow(scopeKey, userId, feature, limit, config);
        if (limitOverride != null) {
            syncLimit(scopeKey, feature, limitOverride);
        }
        resetIfDue(scopeKey, feature, config);

        if (limit <= 0) {
            // 不限次：只计次，供后续定价 / 观察真实用量
            quotaMapper.update(null, Wrappers.<BlAiQuota>lambdaUpdate()
                    .eq(BlAiQuota::getScopeKey, scopeKey)
                    .eq(BlAiQuota::getFeature, feature.code())
                    .setSql("quota_used = quota_used + 1"));
            return -1;
        }

        int rows = quotaMapper.update(null, Wrappers.<BlAiQuota>lambdaUpdate()
                .eq(BlAiQuota::getScopeKey, scopeKey)
                .eq(BlAiQuota::getFeature, feature.code())
                .apply("quota_used < quota_limit")
                .setSql("quota_used = quota_used + 1"));
        if (rows == 0) {
            throw new ServiceException(BabyLogErrorCode.AI_QUOTA_EXCEEDED);
        }
        BlAiQuota row = quotaMapper.selectOne(Wrappers.<BlAiQuota>lambdaQuery()
                .eq(BlAiQuota::getScopeKey, scopeKey)
                .eq(BlAiQuota::getFeature, feature.code()));
        if (row == null || row.getQuotaUsed() == null) {
            // 极端并发下读不到刚更新的行：按「本次占位已消耗 1 次」给保守值，不冒充「还有 N 次」
            return Math.max(0, limit - 1);
        }
        return Math.max(0, limit - row.getQuotaUsed());
    }

    /**
     * 回滚一次预占（AI 调用失败 / 草稿解析失败时）。
     *
     * <p>幂等方向安全：{@code quota_used > 0} 才减，绝不减成负数；不做「减到 0 以下就重置」之类的隐式修正。
     */
    public void release(long userId, AiFeature feature) {
        String scopeKey = scopeKeyOf(userId);
        quotaMapper.update(null, Wrappers.<BlAiQuota>lambdaUpdate()
                .eq(BlAiQuota::getScopeKey, scopeKey)
                .eq(BlAiQuota::getFeature, feature.code())
                .apply("quota_used > 0")
                .setSql("quota_used = quota_used - 1"));
    }

    /**
     * 额度归属键：有 ACTIVE 家庭成员行 → 家庭共享；否则 → 个人。
     *
     * <p>与 {@code FamilyAccessScopeResolver} 同口径（status=ACTIVE，MP 自动补 deleted=0）；
     * 多条 ACTIVE 行属脏数据时取 joined_at 最新一条并告警，保持与读侧一致的「不崩」策略。
     */
    String scopeKeyOf(long userId) {
        List<BlFamilyMember> memberships = memberMapper.selectList(Wrappers.<BlFamilyMember>lambdaQuery()
                .eq(BlFamilyMember::getUserId, userId)
                .eq(BlFamilyMember::getStatus, BlFamilyMember.STATUS_ACTIVE));
        if (memberships.isEmpty()) {
            return SCOPE_USER + userId;
        }
        if (memberships.size() > 1) {
            log.warn("[BABYLOG][ai] 用户 {} 存在 {} 条 ACTIVE 成员行（应为 1），额度归属取 joined_at 最新一条",
                    userId, memberships.size());
            memberships.sort(Comparator.comparing(BlFamilyMember::getJoinedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())));
        }
        return SCOPE_FAMILY + memberships.get(memberships.size() - 1).getFamilyId();
    }

    /**
     * 把参数中心的新上限同步到已存在的额度行（K6-S / M76）。
     *
     * <p>为什么必须做：{@code ensureRow} 只在「行不存在」时写 {@code quota_limit}，
     * 运营在后台把额度从 10 改成 20 后，存量行仍停在 10 ⇒ 改了等于没改。
     * 这里用条件更新只动不同的行，重复调用天然幂等；窗口归零仍由 {@code resetIfDue} 负责。
     */
    private void syncLimit(String scopeKey, AiFeature feature, int limit) {
        quotaMapper.update(null, Wrappers.<BlAiQuota>lambdaUpdate()
                .eq(BlAiQuota::getScopeKey, scopeKey)
                .eq(BlAiQuota::getFeature, feature.code())
                .ne(BlAiQuota::getQuotaLimit, limit)
                .set(BlAiQuota::getQuotaLimit, limit));
    }

    /** 首建额度行；并发首建由唯一键兜底，落败方直接复用既有行 */
    private void ensureRow(String scopeKey, long userId, AiFeature feature, int limit,
                           BabyLogAiQuotaProperties.FeatureQuota config) {
        Long count = quotaMapper.selectCount(Wrappers.<BlAiQuota>lambdaQuery()
                .eq(BlAiQuota::getScopeKey, scopeKey)
                .eq(BlAiQuota::getFeature, feature.code()));
        if (count != null && count > 0) {
            return;
        }
        BlAiQuota row = new BlAiQuota();
        row.setUserId(userId);
        row.setScopeKey(scopeKey);
        row.setFeature(feature.code());
        row.setQuotaLimit(limit);
        row.setQuotaUsed(0);
        row.setResetAt(nextResetAt(config));
        try {
            quotaMapper.insert(row);
        } catch (DuplicateKeyException e) {
            // 并发首建：另一请求已插入，本请求直接走后续占位语句，不重试
            log.debug("[BABYLOG][ai] 额度行并发首建，复用既有行 scope={} feature={}", scopeKey, feature.code());
        }
    }

    /** 窗口到期归零（条件更新：只有 reset_at 已到/为空时才动，重复触发天然幂等） */
    private void resetIfDue(String scopeKey, AiFeature feature,
                            BabyLogAiQuotaProperties.FeatureQuota config) {
        LocalDateTime now = LocalDateTime.now();
        quotaMapper.update(null, Wrappers.<BlAiQuota>lambdaUpdate()
                .eq(BlAiQuota::getScopeKey, scopeKey)
                .eq(BlAiQuota::getFeature, feature.code())
                .and(w -> w.isNull(BlAiQuota::getResetAt).or().le(BlAiQuota::getResetAt, now))
                .set(BlAiQuota::getQuotaUsed, 0)
                .set(BlAiQuota::getResetAt, nextResetAt(config)));
    }

    /** 下一个归零时刻：DAILY=次日 00:00，WEEKLY=下周一 00:00（服务端本地时区，与今日汇总同口径） */
    static LocalDateTime nextResetAt(BabyLogAiQuotaProperties.FeatureQuota config) {
        LocalDate today = LocalDate.now();
        BabyLogAiQuotaProperties.Window window =
                config == null || config.getWindow() == null
                        ? BabyLogAiQuotaProperties.Window.DAILY
                        : config.getWindow();
        if (window == BabyLogAiQuotaProperties.Window.WEEKLY) {
            return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay();
        }
        return today.plusDays(1).atStartOfDay();
    }
}
