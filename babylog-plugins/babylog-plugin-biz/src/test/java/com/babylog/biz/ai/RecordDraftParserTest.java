package com.babylog.biz.ai;

import com.babylog.biz.ai.support.RecordDraftParser;
import com.babylog.biz.ai.vo.AiRecordDraftVO;
import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI 草稿 JSON 输出约束解析单测（K5 / M73，范围 B）。
 *
 * <p>锁三件事：
 * <ol>
 *   <li><b>脏输出收得住</b>：markdown 围栏、前后解释文字、数字写成字符串，模型怎么歪都能解析；</li>
 *   <li><b>白名单是硬边界</b>：不在既有 {@code RecordType} 注册表里的类型一律 {@code 8461}，
 *       不给「云端新增记录类型」留后门（{@code 22} §7.2 #6）；</li>
 *   <li><b>不替用户编数字</b>：越界的毫升/时长直接丢字段，不做「修正到边界」，
 *       免得草稿里出现家长没说过的话。</li>
 * </ol>
 */
class RecordDraftParserTest {

    @Test
    void parsesFencedJsonWithSurroundingProse() {
        String raw = """
                好的，我帮你整理成一条记录：
                ```json
                {"recordType":"bottle","startedAt":1737001800000,"amountMl":120,
                 "note":"10点喂了120ml奶粉","confidence":0.92}
                ```
                请确认后保存。
                """;

        AiRecordDraftVO draft = RecordDraftParser.parse(raw);

        assertThat(draft.getRecordType()).isEqualTo("BOTTLE"); // 小写归一到大写白名单
        assertThat(draft.getStartedAt()).isEqualTo(1737001800000L);
        assertThat(draft.getAmountMl()).isEqualTo(120);
        assertThat(draft.getConfidence()).isEqualTo(0.92);
        assertThat(draft.getNeedConfirm()).isTrue();           // 恒为草稿
    }

    @Test
    void acceptsNumbersWrittenAsStrings() {
        String raw = "{\"recordType\":\"SLEEP\",\"startedAt\":\"1737001800000\","
                + "\"endedAt\":\"1737005400000\",\"durationMinutes\":\"60\"}";

        AiRecordDraftVO draft = RecordDraftParser.parse(raw);

        assertThat(draft.getStartedAt()).isEqualTo(1737001800000L);
        assertThat(draft.getEndedAt()).isEqualTo(1737005400000L);
        assertThat(draft.getDurationMinutes()).isEqualTo(60);
    }

    @Test
    void rejectsRecordTypeOutsideWhitelist() {
        String raw = "{\"recordType\":\"CRY\",\"startedAt\":1737001800000}";

        assertThatThrownBy(() -> RecordDraftParser.parse(raw))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(BabyLogErrorCode.AI_DRAFT_UNPARSEABLE.getCode()));
    }

    @Test
    void rejectsNonJsonAndEmptyOutput() {
        assertThatThrownBy(() -> RecordDraftParser.parse("抱歉，我不太明白你的意思"))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> RecordDraftParser.parse("{}"))
                .as("空对象没有 recordType，不能当草稿返回")
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> RecordDraftParser.parse(null))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void dropsOutOfRangeNumbersInsteadOfClamping() {
        String raw = "{\"recordType\":\"BOTTLE\",\"amountMl\":99999,\"durationMinutes\":-1}";

        AiRecordDraftVO draft = RecordDraftParser.parse(raw);

        assertThat(draft.getAmountMl()).as("越界毫升直接丢，不修正成上限").isNull();
        assertThat(draft.getDurationMinutes()).isNull();
    }

    @Test
    void keepsNoteCaseAndTruncatesLongText() {
        String longNote = "A".repeat(300);
        String raw = "{\"recordType\":\"DIAPER\",\"note\":\"" + longNote + "\"}";

        AiRecordDraftVO draft = RecordDraftParser.parse(raw);

        assertThat(draft.getNote()).hasSize(200);
        assertThat(draft.getNote()).startsWith("AAA"); // 没被大写化处理成别的东西（原文就是大写）
    }
}
