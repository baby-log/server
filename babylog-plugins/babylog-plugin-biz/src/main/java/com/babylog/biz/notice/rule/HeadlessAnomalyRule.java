package com.babylog.biz.notice.rule;

import java.util.ArrayList;
import java.util.List;

/**
 * M39 无头异常两条规则（K4 / M71 接收侧，契约 {@code 24} ⑦）。
 *
 * <p>这是<b>纯函数规则引擎</b>：V3 前无真实采集（K9 才出图），K4 只构造结构化事件点验证。
 * 输入是一组「检测事件点」（只有类型 + 单个时间点，零画面、零字节），输出命中的无头异常
 * 及其合成单点行。<b>不接摄像头/麦克风、不定义 {@code bl_alert_rule} 阈值外的东西、不实现 K9 采集</b>。
 *
 * <p><b>两条规则（{@code 22} §二-10 / {@code 24} ⑦-1）</b>：
 * <ol>
 *   <li>{@code WAKE_WITHOUT_SLEEP} 醒来无入睡：出现 WAKE（醒来）但此前无配对 SLEEP_START（入睡）；</li>
 *   <li>{@code END_WITHOUT_START} 结束无开始：出现 END（结束）但此前无配对 START（开始）。</li>
 * </ol>
 *
 * <p><b>如实记录（{@code 24} ⑦-2 / ⑦-3）</b>：命中即合成 <b>单点行</b>——
 * {@link Finding#pointAt()} 只有一个时间点，非计时行、无 {@code endedAt}、无 pauseEvents、
 * 无配对；不丢弃、不伪造配对、不假装完整会话，<b>不复用 {@code 15} §二 同 ID 收尾、
 * 不造第二条结束行</b>（这是对同 ID 收尾契约的扩展而非改写）。
 *
 * <p>这是接收侧判定：K9 采集侧将来把检测事件点喂进来即可，规则引擎语义不漂移。
 */
public final class HeadlessAnomalyRule {

    /** 检测事件点类型（构造事件用；与采集侧 K9 对接时逐字沿用） */
    public static final String T_SLEEP_START = "SLEEP_START";
    public static final String T_WAKE = "WAKE";
    public static final String T_START = "START";
    public static final String T_END = "END";
    public static final String T_PAUSE = "PAUSE";
    public static final String T_RESUME = "RESUME";

    private HeadlessAnomalyRule() {
    }

    /** 构造的检测事件点：{@code type} 见 T_* 常量，{@code at} 是单个时间点（epoch millis，字符串入站转 Long） */
    public record EventPoint(String type, Long at) {
    }

    /**
     * 命中的无头异常 + 合成单点行：
     * {@code kind} 分类、{@code pointAt} 单点时间（= 触发事件的 at）、{@code triggerType} 触发事件类型。
     * 只有单个时间点，绝无配对/结束行。
     */
    public record Finding(AnomalyKind kind, Long pointAt, String triggerType) {
    }

    /**
     * 判定一组检测事件点里的无头异常（纯函数，无 IO、无状态，可单测锁死）。
     *
     * <p>配对语义（对齐 {@code 15} §二 计时事件流 START/PAUSE/RESUME/END 与睡眠入睡/醒来）：
     * <ul>
     *   <li>睡眠链：SLEEP_START 开段，WAKE 闭段；见到 WAKE 而当前无未闭合 SLEEP_START ⇒ 醒来无入睡；</li>
     *   <li>计时链：START 开段，END 闭段；见到 END 而当前无未闭合 START ⇒ 结束无开始；</li>
     *   <li>PAUSE/RESUME 只影响计时段内状态，不改变「是否有未闭合 START」；</li>
     *   <li>at 为 null 的事件点按「无时间点」跳过（脏数据宽容，不抛异常，同 {@code 15} §2.3-5）。</li>
     * </ul>
     *
     * <p>每个无配对事件各自合成一个单点行（不合并、不丢弃、不补造配对），
     * 保证「全体成员可见消息」按事件逐条下发。
     */
    public static List<Finding> detect(List<EventPoint> events) {
        List<Finding> findings = new ArrayList<>();
        if (events == null || events.isEmpty()) {
            return findings;
        }
        boolean sleepOpen = false;
        boolean timerOpen = false;
        for (EventPoint event : events) {
            if (event == null || event.type() == null || event.at() == null) {
                continue;
            }
            String type = event.type();
            switch (type) {
                case T_SLEEP_START -> sleepOpen = true;
                case T_WAKE -> {
                    if (!sleepOpen) {
                        findings.add(new Finding(AnomalyKind.WAKE_WITHOUT_SLEEP, event.at(), type));
                    } else {
                        sleepOpen = false;
                    }
                }
                case T_START -> timerOpen = true;
                case T_END -> {
                    if (!timerOpen) {
                        findings.add(new Finding(AnomalyKind.END_WITHOUT_START, event.at(), type));
                    } else {
                        timerOpen = false;
                    }
                }
                case T_PAUSE, T_RESUME -> {
                    // 段内状态，不改变是否开段；零配对语义
                }
                default -> {
                    // 未知事件类型忽略（脏数据宽容，不抛异常）
                }
            }
        }
        return findings;
    }
}
