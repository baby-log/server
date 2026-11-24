package com.babylog.biz.ai.service;

import com.babylog.ai.api.facade.IAiFacade;
import com.babylog.biz.ai.dto.AiParseRecordCmd;
import com.babylog.biz.ai.dto.AiWeeklyReportCmd;
import com.babylog.biz.ai.enums.AiFeature;
import com.babylog.biz.ai.support.AiErrorMapper;
import com.babylog.biz.ai.support.RecordDraftParser;
import com.babylog.biz.ai.support.WeeklyReportAggregator;
import com.babylog.biz.ai.vo.AiRecordDraftVO;
import com.babylog.biz.ai.vo.AiWeeklyReportVO;
import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.entity.BlChild;
import com.babylog.biz.mapper.BlChildMapper;
import com.babylog.biz.sync.support.SyncAccessScopeResolver;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * V3.0 AI 第一批 · 服务端（K5 / M73，范围 B + D）。
 *
 * <p><b>两个能力，共用一条纪律</b>：只产<b>草稿</b>、不落库。
 * 补录返回待确认卡片，周报返回汇总 + 文案；19 张同步表<b>一行不写</b>，
 * 正式记录一律由客户端确认后走既有 {@code /sync/push}（沿用 K4「自动检测永不自动落库」）。
 *
 * <p><b>AI 能力来源</b>：只走 {@code plugin-ai} 的 {@link IAiFacade} 契约
 * （跨插件只碰 api 包，ArchUnit A1/A2 放行 {@code com.babylog.ai.api..}），
 * <b>不引新 SDK、不自己装配 ChatClient</b>。选 {@code dynamicChat} 而不是 {@code internalChat}：
 * 后者在没有 AI 配置时会回落到静态通道「凑出内容」，而补录/周报这种场景
 * <b>拿不到模型就该明说拿不到</b>，绝不能用兜底文案冒充 AI 结果（{@code 09} 会话 R 红线）。
 *
 * <p><b>额度</b>：调用前预占（{@code 8460} 拒绝），失败回滚；计次口径按家庭×功能，
 * 见 {@link AiQuotaService}。数值全在 {@code BabyLogAiQuotaProperties}，代码里没有魔法数字。
 */
@Service
public class BabyLogAiService {

    private static final Logger log = LoggerFactory.getLogger(BabyLogAiService.class);

    /** 口述原文上限（防超长 prompt 烧额度，也防把整篇文章丢给模型） */
    private static final int MAX_TEXT_LENGTH = 500;

    private static final long DAY_MS = 86_400_000L;
    private static final int REPORT_DAYS = 7;
    /** 周报只回看一年，避免一次请求扫全历史 */
    private static final int MAX_LOOKBACK_DAYS = 365;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String PARSE_SYSTEM_PROMPT = """
            你是 BabyLog（育儿记录 App）的录入助手。把家长的一句话解析成一条记录草稿。
            只输出一个 JSON 对象，不要解释、不要 markdown 代码块。
            字段：
            recordType（必填，只能取以下之一）：
            BOTTLE,BREAST_LEFT,BREAST_RIGHT,SOLID,WATER,PUMP,DIAPER,SLEEP,BATH,CORD,SPIT_UP,
            TEMP,WEIGHT,HEIGHT,HEAD,JAUNDICE,SUPPLEMENT,MEDICINE,VACCINE,FOLLOW_UP,VISIT
            startedAt：事件发生时刻的毫秒时间戳（数字）
            endedAt：仅计时类记录（如睡眠、亲喂）的结束时刻，没有就不填
            amountMl：毫升数（整数），仅奶瓶/水等有毫升语义时填
            durationMinutes：时长分钟（整数），仅计时类记录填
            note：不超过 40 字的简述，用家长原话的口吻
            confidence：0 到 1 之间的数字，表示你有多确定
            规则：
            1) 不确定就少填字段，绝不编造时间或数值；
            2) recordType 拿不准时不要猜，宁可输出空对象 {};
            3) 只解析这一条记录，不要拆成多条，不要补充家长没说的内容。
            """;

    private static final String REPORT_SYSTEM_PROMPT = """
            你是育儿记录 App 的周报助手。根据给出的结构化统计，用中文写 3 到 5 句周报解读。
            要求：
            1) 只引用给定的数字，不得编造或推算未给出的数据；
            2) 不做疾病诊断、不给用药建议、不吓唬家长，语气平和具体；
            3) 纯文本输出，不要 markdown、不要标题、不要列表符号。
            """;

    private final IAiFacade aiFacade;
    private final AiQuotaService quotaService;
    private final WeeklyReportAggregator aggregator;
    private final SyncAccessScopeResolver scopeResolver;
    private final BlChildMapper childMapper;

    public BabyLogAiService(IAiFacade aiFacade,
                            AiQuotaService quotaService,
                            WeeklyReportAggregator aggregator,
                            SyncAccessScopeResolver scopeResolver,
                            BlChildMapper childMapper) {
        this.aiFacade = aiFacade;
        this.quotaService = quotaService;
        this.aggregator = aggregator;
        this.scopeResolver = scopeResolver;
        this.childMapper = childMapper;
    }

    // ------------------------------------------------------------------
    // 范围 B：自然语言补录（返回草稿，绝不落库）
    // ------------------------------------------------------------------

    /**
     * 把一句话解析成记录草稿。
     *
     * @throws ServiceException {@code 8460} 额度用完 / {@code 8461} 解析不出 /
     *                          {@code 8462} 宝宝不可见 / {@code 5002} 未配置 / {@code 5022} 调用不通
     */
    public AiRecordDraftVO parseRecord(long userId, AiParseRecordCmd cmd) {
        if (cmd == null || cmd.getChildId() == null) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID);
        }
        String text = cmd.getText() == null ? "" : cmd.getText().trim();
        if (text.isEmpty() || text.length() > MAX_TEXT_LENGTH) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID);
        }
        BlChild child = requireVisibleChild(userId, cmd.getChildId());

        int remaining = quotaService.reserve(userId, AiFeature.PARSE_RECORD);
        try {
            String answer = aiFacade.dynamicChat(PARSE_SYSTEM_PROMPT, buildParseUserPrompt(child, text));
            AiRecordDraftVO draft = RecordDraftParser.parse(answer);
            draft.setQuotaRemaining(remaining);
            return draft;
        } catch (ServiceException e) {
            quotaService.release(userId, AiFeature.PARSE_RECORD);
            throw AiErrorMapper.translate(e);
        } catch (RuntimeException e) {
            quotaService.release(userId, AiFeature.PARSE_RECORD);
            throw e;
        }
    }

    // ------------------------------------------------------------------
    // 范围 D：育儿周报（按需生成；数字由服务端算，模型只写解读）
    // ------------------------------------------------------------------

    /**
     * 生成一周周报。
     *
     * @throws ServiceException {@code 8460} / {@code 8462} / {@code 8463} 时间窗非法 /
     *                          {@code 5002} 未配置 / {@code 5022} 调用不通
     */
    public AiWeeklyReportVO weeklyReport(long userId, AiWeeklyReportCmd cmd) {
        if (cmd == null || cmd.getChildId() == null) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID);
        }
        BlChild child = requireVisibleChild(userId, cmd.getChildId());
        long[] window = resolveWindow(cmd.getWeekStart());

        int remaining = quotaService.reserve(userId, AiFeature.WEEKLY_REPORT);
        try {
            WeeklyReportAggregator.Aggregation aggregation =
                    aggregator.aggregate(userId, cmd.getChildId(), window[0], window[1]);
            String narrative = aiFacade.dynamicChat(REPORT_SYSTEM_PROMPT,
                    buildReportUserPrompt(child, window[0], window[1], aggregation.stats()));

            AiWeeklyReportVO vo = new AiWeeklyReportVO();
            vo.setChildId(cmd.getChildId());
            vo.setWeekStart(window[0]);
            vo.setWeekEnd(window[1]);
            vo.setNarrative(narrative == null ? null : narrative.trim());
            vo.setStats(aggregation.stats());
            vo.setReadableCategories(aggregation.readableCategories());
            vo.setQuotaRemaining(remaining);
            return vo;
        } catch (ServiceException e) {
            quotaService.release(userId, AiFeature.WEEKLY_REPORT);
            throw AiErrorMapper.translate(e);
        } catch (RuntimeException e) {
            quotaService.release(userId, AiFeature.WEEKLY_REPORT);
            throw e;
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /** 宝宝必须在当前账号的 visibleUserIds 家庭可见范围内（与同步读侧同源，不另造判断） */
    private BlChild requireVisibleChild(long userId, long childId) {
        BlChild child = childMapper.selectOne(Wrappers.<BlChild>lambdaQuery()
                .eq(BlChild::getId, childId)
                .in(BlChild::getUserId, scopeResolver.resolveAccess(userId).visibleUserIds()));
        if (child == null) {
            throw new ServiceException(BabyLogErrorCode.AI_CHILD_NOT_VISIBLE);
        }
        return child;
    }

    /**
     * 解析周报时间窗，返回 {@code [from, to)}。
     *
     * <p>缺省 = 最近 7 天（含今天）；指定时对齐到该时刻所在自然日的 00:00，窗口固定 7 天。
     * 只接受「不晚于现在、不回看超过一年」的起点，其余 {@code 8463}——不给「未来周报」留入口。
     */
    static long[] resolveWindow(Long weekStart) {
        ZoneId zone = ZoneId.systemDefault();
        long now = System.currentTimeMillis();
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        if (weekStart == null) {
            long from = today.minusDays(REPORT_DAYS - 1L).atStartOfDay(zone).toInstant().toEpochMilli();
            long to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            return new long[]{from, to};
        }
        LocalDate startDay = Instant.ofEpochMilli(weekStart).atZone(zone).toLocalDate();
        long from = startDay.atStartOfDay(zone).toInstant().toEpochMilli();
        long to = startDay.plusDays(REPORT_DAYS).atStartOfDay(zone).toInstant().toEpochMilli();
        if (from > now || from < today.minusDays(MAX_LOOKBACK_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()) {
            throw new ServiceException(BabyLogErrorCode.AI_REPORT_WINDOW_INVALID);
        }
        return new long[]{from, to};
    }

    private static String buildParseUserPrompt(BlChild child, String text) {
        ZoneId zone = ZoneId.systemDefault();
        String now = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).format(DAY_FMT);
        return "当前时间：" + now + "（时区 " + zone.getId() + "）\n"
                + "宝宝：" + safeName(child) + "\n"
                + "家长原话：" + text;
    }

    private static String buildReportUserPrompt(BlChild child, long from, long to,
                                                AiWeeklyReportVO.Stats stats) {
        ZoneId zone = ZoneId.systemDefault();
        String fromText = Instant.ofEpochMilli(from).atZone(zone).format(DAY_FMT);
        String toText = Instant.ofEpochMilli(to).atZone(zone).format(DAY_FMT);
        return "统计窗口：" + fromText + " ~ " + toText + "\n"
                + "宝宝：" + safeName(child) + "\n"
                + "结构化统计：\n"
                + "喂养次数=" + stats.getFeedingCount()
                + "，喂养毫升=" + stats.getFeedingMl()
                + "，亲喂次数=" + stats.getBreastSessions()
                + "，辅食次数=" + stats.getSolidCount() + "\n"
                + "尿布次数=" + stats.getDiaperCount()
                + "（尿 " + stats.getDiaperWet() + " / 便 " + stats.getDiaperSoiled() + "）\n"
                + "睡眠段数=" + stats.getSleepCount() + "，睡眠总分钟=" + stats.getSleepMinutes() + "\n"
                + "泵奶次数=" + stats.getPumpCount() + "，泵奶毫升=" + stats.getPumpMl() + "\n"
                + (stats.getLatestWeight() == null ? "" : "最近体重=" + stats.getLatestWeight() + "kg\n")
                + (stats.getLatestHeight() == null ? "" : "最近身高=" + stats.getLatestHeight() + "cm\n")
                + "请只基于以上数字写周报。";
    }

    private static String safeName(BlChild child) {
        if (child == null || child.getName() == null || child.getName().isBlank()) {
            return "宝宝";
        }
        return child.getName();
    }
}
