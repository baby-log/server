package com.babylog.biz.notice.rule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.babylog.biz.notice.rule.HeadlessAnomalyRule.EventPoint;
import static com.babylog.biz.notice.rule.HeadlessAnomalyRule.Finding;
import static com.babylog.biz.notice.rule.HeadlessAnomalyRule.T_END;
import static com.babylog.biz.notice.rule.HeadlessAnomalyRule.T_PAUSE;
import static com.babylog.biz.notice.rule.HeadlessAnomalyRule.T_RESUME;
import static com.babylog.biz.notice.rule.HeadlessAnomalyRule.T_SLEEP_START;
import static com.babylog.biz.notice.rule.HeadlessAnomalyRule.T_START;
import static com.babylog.biz.notice.rule.HeadlessAnomalyRule.T_WAKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M39 无头异常两条规则纯函数单测（K4 / M71，契约 {@code 24} ⑦）。
 *
 * <p>V3 前零画面：只构造结构化事件点验证，不接真实采集。
 * 锁死四条语义：两条无头规则命中、配对齐全不伪造异常、PAUSE/RESUME 不改开段、
 * 脏数据宽容 + 命中即合成「单点行」（只有一个时间点、无配对/无结束行）。
 */
class HeadlessAnomalyRuleTest {

    private static final long AT_1330 = 1737001800000L; // 13:30
    private static final long AT_1350 = 1737003000000L; // 13:50

    @Test
    void wakeWithoutSleepIsDetectedAsSinglePoint() {
        List<Finding> findings = HeadlessAnomalyRule.detect(List.of(
                new EventPoint(T_WAKE, AT_1330)));

        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.kind()).isEqualTo(AnomalyKind.WAKE_WITHOUT_SLEEP);
        assertThat(f.pointAt()).isEqualTo(AT_1330);   // 单点行：只有这一个时间点
        assertThat(f.triggerType()).isEqualTo(T_WAKE);
    }

    @Test
    void endWithoutStartIsDetectedAsSinglePoint() {
        List<Finding> findings = HeadlessAnomalyRule.detect(List.of(
                new EventPoint(T_END, AT_1350)));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).kind()).isEqualTo(AnomalyKind.END_WITHOUT_START);
        assertThat(findings.get(0).pointAt()).isEqualTo(AT_1350);
        assertThat(findings.get(0).triggerType()).isEqualTo(T_END);
    }

    @Test
    void bothRulesDetectedIndependently() {
        // 醒来无入睡 + 结束无开始，两条各自合成单点行，不合并、不丢弃
        List<Finding> findings = HeadlessAnomalyRule.detect(List.of(
                new EventPoint(T_WAKE, AT_1330),
                new EventPoint(T_END, AT_1350)));

        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(Finding::kind)
                .containsExactlyInAnyOrder(AnomalyKind.WAKE_WITHOUT_SLEEP, AnomalyKind.END_WITHOUT_START);
        assertThat(findings).extracting(Finding::pointAt)
                .containsExactlyInAnyOrder(AT_1330, AT_1350);
    }

    @Test
    void fullyPairedEventsProduceNoAnomaly() {
        // 配对齐全：不伪造异常（如实记录，不假装完整会话之外的东西）
        List<Finding> findings = HeadlessAnomalyRule.detect(List.of(
                new EventPoint(T_SLEEP_START, AT_1330),
                new EventPoint(T_WAKE, AT_1350),
                new EventPoint(T_START, AT_1330),
                new EventPoint(T_END, AT_1350)));

        assertThat(findings).isEmpty();
    }

    @Test
    void pauseResumeDoNotChangeOpenState() {
        // PAUSE/RESUME 只影响计时段内状态，不改变「是否有未闭合 START」
        List<Finding> findings = HeadlessAnomalyRule.detect(List.of(
                new EventPoint(T_START, AT_1330),
                new EventPoint(T_PAUSE, AT_1330 + 1000L),
                new EventPoint(T_RESUME, AT_1330 + 2000L),
                new EventPoint(T_END, AT_1350)));

        assertThat(findings).isEmpty();
    }

    @Test
    void repeatedOrphanEventsEachProduceSinglePoint() {
        // 两个孤立 WAKE：各自单点行，不合并、不补造配对
        List<Finding> findings = HeadlessAnomalyRule.detect(List.of(
                new EventPoint(T_WAKE, AT_1330),
                new EventPoint(T_WAKE, AT_1350)));

        assertThat(findings).hasSize(2);
        assertThat(findings).allMatch(f -> f.kind() == AnomalyKind.WAKE_WITHOUT_SLEEP);
        assertThat(findings).extracting(Finding::pointAt)
                .containsExactly(AT_1330, AT_1350);
    }

    @Test
    void nullAndDirtyEventPointsAreSkipped() {
        List<Finding> findings = HeadlessAnomalyRule.detect(java.util.Arrays.asList(
                null,
                new EventPoint(null, AT_1330),        // 无类型
                new EventPoint(T_WAKE, null),         // 无时间点
                new EventPoint("UNKNOWN_TYPE", AT_1330), // 未知类型
                new EventPoint(T_END, AT_1350)));     // 唯一合法且无配对

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).kind()).isEqualTo(AnomalyKind.END_WITHOUT_START);
        assertThat(findings.get(0).pointAt()).isEqualTo(AT_1350);
    }

    @Test
    void nullOrEmptyInputIsTolerated() {
        assertThat(HeadlessAnomalyRule.detect(null)).isEmpty();
        assertThat(HeadlessAnomalyRule.detect(List.of())).isEmpty();
    }

    @Test
    void anomalyKindCodesAreStableLiterals() {
        // 与 Mini 契约纯函数逐字一致
        assertThat(AnomalyKind.WAKE_WITHOUT_SLEEP.code()).isEqualTo("WAKE_WITHOUT_SLEEP");
        assertThat(AnomalyKind.END_WITHOUT_START.code()).isEqualTo("END_WITHOUT_START");
        assertThat(AnomalyKind.of("WAKE_WITHOUT_SLEEP")).isEqualTo(AnomalyKind.WAKE_WITHOUT_SLEEP);
        assertThat(AnomalyKind.of("bogus")).isNull();
        assertThat(AnomalyKind.of(null)).isNull();
    }
}
