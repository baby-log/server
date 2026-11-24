package com.babylog.biz.ai.support;

import com.babylog.biz.ai.vo.AiRecordDraftVO;
import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Locale;
import java.util.Set;

/**
 * 自然语言补录的 JSON 输出约束解析（K5 / M73，范围 B）。
 *
 * <p><b>只产草稿，永不落库</b>：解析结果是一张「待确认卡片」的数据，调用方拿到后直接返回给客户端；
 * 正式落库由用户确认后走既有 {@code /sync/push}（沿用 K4「自动检测永不自动落库」同款纪律）。
 * 本类不注入任何 Mapper，物理上不可能写库。
 *
 * <p><b>记录类型白名单 = 既有 {@code RecordType} 注册表</b>（与 {@code ExportTableSpecs.RECORD_TYPE}
 * 同一份集合，逐字一致）。模型编出一个新类型一律判失败，不给「云端新增记录类型」（{@code 22} §7.2 #6）。
 *
 * <p><b>容错边界</b>：模型常见的三种脏输出都收得住——包了 ```json 围栏、前后带解释文字、
 * 数字写成字符串。收不住的一律 {@code 8461}，绝不返回半成品草稿让客户端去猜。
 */
public final class RecordDraftParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 既有记录类型白名单（21 项，与 {@code ExportTableSpecs.RECORD_TYPE} 的 key 集合逐字对齐）。
     * 新增记录类型必须先改 App 端注册表与导出列，不能靠 AI 先行。
     */
    private static final Set<String> ALLOWED_RECORD_TYPES = Set.of(
            "BOTTLE", "BREAST_LEFT", "BREAST_RIGHT", "SOLID", "WATER", "PUMP",
            "DIAPER", "SLEEP", "BATH", "CORD", "SPIT_UP",
            "TEMP", "WEIGHT", "HEIGHT", "HEAD", "JAUNDICE", "SUPPLEMENT",
            "MEDICINE", "VACCINE", "FOLLOW_UP", "VISIT");

    private RecordDraftParser() {
    }

    /**
     * 解析模型输出为草稿。
     *
     * @param raw 模型返回原文（可能带 markdown 围栏或前后解释文字）
     * @return 通过白名单与取值范围校验的草稿（{@code needConfirm=true}）
     * @throws ServiceException {@code 8461} 不可解析 / 类型不在白名单 / 关键字段缺失
     */
    public static AiRecordDraftVO parse(String raw) {
        String json = extractJson(raw);
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new ServiceException(BabyLogErrorCode.AI_DRAFT_UNPARSEABLE);
        }
        if (node == null || !node.isObject()) {
            throw new ServiceException(BabyLogErrorCode.AI_DRAFT_UNPARSEABLE);
        }

        String recordType = upperText(node, "recordType");
        if (recordType == null || !ALLOWED_RECORD_TYPES.contains(recordType)) {
            throw new ServiceException(BabyLogErrorCode.AI_DRAFT_UNPARSEABLE);
        }

        AiRecordDraftVO vo = new AiRecordDraftVO();
        vo.setRecordType(recordType);
        vo.setStartedAt(epochMillis(node, "startedAt"));
        vo.setEndedAt(epochMillis(node, "endedAt"));
        vo.setAmountMl(intValue(node, "amountMl", 0, 5000));
        vo.setDurationMinutes(intValue(node, "durationMinutes", 0, 24 * 60));
        vo.setNote(trimToNull(rawText(node, "note"), 200));
        vo.setConfidence(doubleValue(node, "confidence"));
        vo.setNeedConfirm(Boolean.TRUE);
        return vo;
    }

    /** 剥掉 markdown 围栏与前后解释文字，取第一个 { 到最后一个 } 之间的内容 */
    private static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ServiceException(BabyLogErrorCode.AI_DRAFT_UNPARSEABLE);
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ServiceException(BabyLogErrorCode.AI_DRAFT_UNPARSEABLE);
        }
        return raw.substring(start, end + 1);
    }

    private static String rawText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText("").trim();
        return text.isEmpty() ? null : text;
    }

    /** 枚举类字段（记录类型）统一大写后再比对白名单 */
    private static String upperText(JsonNode node, String field) {
        String raw = rawText(node, field);
        return raw == null ? null : raw.toUpperCase(Locale.ROOT);
    }

    /** epoch millis；模型把 Long 写成字符串也能收（前端语义一致：Long 一律字符串） */
    private static Long epochMillis(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asLong();
        }
        String text = value.asText("").trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intValue(JsonNode node, String field, int min, int max) {
        Long value = epochMillis(node, field);
        if (value == null) {
            return null;
        }
        // 越界值不「修正」成边界，直接丢弃——宁可少一个字段，也不替用户编一个数字
        if (value < min || value > max) {
            return null;
        }
        return value.intValue();
    }

    private static Double doubleValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        double parsed = value.isNumber() ? value.asDouble() : Double.parseDouble(value.asText("0"));
        if (Double.isNaN(parsed) || parsed < 0 || parsed > 1) {
            return null;
        }
        return parsed;
    }

    private static String trimToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
