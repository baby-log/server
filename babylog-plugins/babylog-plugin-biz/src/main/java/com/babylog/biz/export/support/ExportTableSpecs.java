package com.babylog.biz.export.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Android 会话 G 的 18 表导出规格（Room schema v11 + ExportTables.kt）服务端镜像。
 * 列清单同时约束 JSON 与 CSV，服务端 user_id/family_id/remote_key 等专属列不会混入恢复文件。
 * M47（18 号契约 §七-6）：镜像补 baths.ended_at / sleeps.transitions（v11 新列，
 * 与 Android 端 ExportTables.kt 同口径：baths.ended_at 进 CSV，sleeps.transitions 照
 * pause_events 先例 CSV 跳过、JSON 全字段保留）。
 */
public final class ExportTableSpecs {

    public enum Scope { PER_CHILD, GLOBAL }

    public record TableSpec(String table, String csvName, Scope scope, List<String> columns,
                            Set<String> timeColumns, Set<String> boolColumns,
                            Map<String, Map<String, String>> enumColumns,
                            Set<String> csvSkipColumns, Map<String, String> headerNames) {
        public boolean hasColumn(String column) {
            return columns.contains(column);
        }
    }

    public static final String DERIVED_TABLE = "reminders";
    public static final int ANDROID_SCHEMA_VERSION = 11;
    private static final Set<String> SKIP_SYNC = Set.of("updated_at", "deleted", "device_id");
    private static final Map<String, String> COMMON_HEADERS = Map.of(
            "id", "ID", "child_id", "宝宝ID", "note", "备注", "created_by", "记录者ID", "created_at", "创建时间");

    // 枚举表必须先于 ALL 初始化，否则类加载时会把 null 装进规格。
    private static final Map<String, String> FEEDING_TYPE = map("BOTTLE", "奶瓶", "BREAST_LEFT", "母乳左", "BREAST_RIGHT", "母乳右", "SOLID", "辅食", "WATER", "喝水");
    private static final Map<String, String> AMOUNT_UNIT = map("ML", "ml", "GRAM", "g", "PORTION", "份");
    private static final Map<String, String> DIAPER_TYPE = map("WET", "尿", "SOILED", "便", "BOTH", "都有");
    private static final Map<String, String> DIAPER_CONSISTENCY = map("WATERY", "水样", "LOOSE", "稀", "PASTY", "糊", "FORMED", "成形", "MILK_CURD", "奶瓣");
    private static final Map<String, String> DIAPER_COLOR = map("YELLOW", "黄", "GREEN", "绿", "BLACK", "黑", "WHITE", "白", "RED", "红");
    private static final Map<String, String> SLEEP_PLACE = map("CRIB", "小床", "BIG_BED", "大床", "HELD", "抱睡", "STROLLER", "推车");
    private static final Map<String, String> MEASUREMENT_TYPE = map("WEIGHT", "体重", "HEIGHT", "身高", "HEAD", "头围", "TEMP", "体温", "JAUNDICE", "黄疸");
    private static final Map<String, String> MEASUREMENT_METHOD = map("AXILLARY", "腋温", "EAR", "耳温", "FOREHEAD", "额温", "RECTAL", "肛温", "FACE", "面部", "TRUNK", "胸腹部", "LIMBS", "四肢", "PALMS_SOLES", "手心脚心");
    private static final Map<String, String> MEDICATION_CATEGORY = map("SUPPLEMENT", "补剂", "MEDICINE", "药物");
    private static final Map<String, String> CORD_STATUS = map("INTACT", "完整", "CLEANED", "已消毒", "FALLEN_OFF", "已脱落");
    private static final Map<String, String> SPIT_AMOUNT = map("SMALL", "少量", "MEDIUM", "中等", "LARGE", "大量", "PROJECTILE", "喷射性");
    private static final Map<String, String> SPIT_TIMING = map("RIGHT_AFTER", "喂后即刻", "AFTER_BURP", "拍嗝后", "WITHIN_HOUR", "1 小时内", "NIGHT", "夜间");
    private static final Map<String, String> PUMP_SIDE = map("LEFT", "左侧", "RIGHT", "右侧", "BOTH", "双侧");
    private static final Map<String, String> MILK_STORAGE = map("ROOM", "室温", "FRIDGE", "冷藏", "FREEZER", "冷冻");
    private static final Map<String, String> VACCINE_STATUS = map("PLANNED", "待接种", "DONE", "已接种", "SKIPPED", "未接种");
    private static final Map<String, String> FOLLOW_UP_TYPE = map("COMMUNITY_7D", "社区随访", "R42", "42 天复查", "HEARING", "听力筛查", "HEEL_BLOOD", "足跟血", "CUSTOM", "自定义");
    private static final Map<String, String> PRODUCT_CATEGORY = map("FORMULA", "奶粉", "SUPPLEMENT", "补剂", "MEDICINE", "药品", "DIAPER", "尿布");
    private static final Map<String, String> MEMBER_ROLE = map("OWNER", "创建者", "CAREGIVER", "照护者", "VIEWER", "只读");
    private static final Map<String, String> MEMBER_STATUS = map("ACTIVE", "正常", "INVITED", "已邀请", "REMOVED", "已移除");
    private static final Map<String, String> RECORD_CATEGORY = map("FEEDING", "喂养", "CARE", "护理", "HEALTH", "健康", "MEDICAL", "医疗");
    private static final Map<String, String> ATTACHMENT_OWNER = map("VISIT", "就医", "VACCINATION", "疫苗", "MEASUREMENT", "健康指标");
    private static final Map<String, String> REMINDER_SOURCE = map("VACCINATION", "疫苗", "FOLLOW_UP", "随访/复诊", "VISIT", "就医", "MEDICATION", "补剂", "PUMP_STORAGE", "储奶", "CUSTOM", "自定义");
    private static final Map<String, String> REMINDER_REPEAT = map("NONE", "单次", "DAILY", "每天");
    private static final Map<String, String> RECORD_TYPE = map("BOTTLE", "奶瓶", "BREAST_LEFT", "母乳左", "BREAST_RIGHT", "母乳右", "SOLID", "辅食", "WATER", "喝水", "PUMP", "泵奶", "DIAPER", "尿布", "SLEEP", "睡眠", "BATH", "洗澡", "CORD", "脐带护理", "SPIT_UP", "吐奶", "TEMP", "体温", "WEIGHT", "体重", "HEIGHT", "身高", "HEAD", "头围", "JAUNDICE", "黄疸", "SUPPLEMENT", "补剂", "MEDICINE", "用药", "VACCINE", "疫苗", "FOLLOW_UP", "随访", "VISIT", "就医");

    public static final List<TableSpec> ALL = List.of(
            global("children", "宝宝列表", "id name gender birthday is_premature gestational_weeks birth_weight_g birth_height_cm avatar_path note archived created_at updated_at deleted version device_id",
                    set("birthday", "created_at"), set("is_premature", "archived"), enums("gender", map("MALE", "男", "FEMALE", "女", "UNKNOWN", "未填")),
                    set("updated_at", "deleted", "device_id", "avatar_path"),
                    headers("name", "昵称", "gender", "性别", "birthday", "生日", "is_premature", "早产", "gestational_weeks", "孕周", "birth_weight_g", "出生体重(g)", "birth_height_cm", "出生身长(cm)", "archived", "已归档")),
            child("record_type_settings", "记录开关", "id child_id record_type enabled start_age_days end_age_days created_at updated_at deleted version device_id",
                    set(), set("enabled"), enums("record_type", RECORD_TYPE), set(),
                    headers("record_type", "记录类型", "enabled", "已启用", "start_age_days", "起始日龄", "end_age_days", "截止日龄")),
            global("family_members", "家庭成员", "id family_key display_name role status joined_at created_at updated_at deleted version device_id",
                    set("joined_at", "created_at"), set(), enums("role", MEMBER_ROLE, "status", MEMBER_STATUS), SKIP_SYNC,
                    headers("family_key", "家庭标识", "display_name", "显示名", "role", "角色", "status", "状态", "joined_at", "加入时间")),
            child("member_permissions", "成员权限", "id member_id child_id category can_read can_write created_at updated_at deleted version device_id",
                    set(), set("can_read", "can_write"), enums("category", RECORD_CATEGORY), set(),
                    headers("member_id", "成员ID", "category", "分类", "can_read", "可读", "can_write", "可写")),
            global("products", "产品档案", "id category brand name spec default_dose last_used_at enabled is_builtin note icon_path created_at updated_at deleted version device_id",
                    set("last_used_at", "created_at"), set("enabled", "is_builtin"), enums("category", PRODUCT_CATEGORY),
                    set("updated_at", "deleted", "device_id", "icon_path"),
                    headers("category", "分类", "brand", "品牌", "name", "名称", "spec", "规格", "default_dose", "默认剂量", "last_used_at", "最近使用", "enabled", "已启用", "is_builtin", "内置")),
            child("feedings", "喂奶", "id child_id type amount_ml amount_unit duration_s product_id brand_snapshot spec_snapshot note started_at created_by in_progress ended_at total_seconds active_seconds pause_events created_at updated_at deleted version device_id",
                    set("started_at", "ended_at"), set("in_progress"), enums("type", FEEDING_TYPE, "amount_unit", AMOUNT_UNIT), set("pause_events"),
                    headers("type", "类型", "amount_ml", "数量", "amount_unit", "单位", "duration_s", "时长(秒)", "product_id", "产品ID", "brand_snapshot", "品牌", "spec_snapshot", "规格", "started_at", "记录时间", "in_progress", "进行中", "ended_at", "结束时间", "total_seconds", "总用时(秒)", "active_seconds", "纯喂养(秒)")),
            child("diapers", "尿布", "id child_id type consistency color started_at created_by created_at updated_at deleted version device_id",
                    set("started_at"), set(), enums("type", DIAPER_TYPE, "consistency", DIAPER_CONSISTENCY, "color", DIAPER_COLOR), set(),
                    headers("type", "类型", "consistency", "性状", "color", "颜色", "started_at", "记录时间")),
            child("sleeps", "睡眠", "id child_id started_at ended_at place transitions created_by created_at updated_at deleted version device_id",
                    set("started_at", "ended_at"), set(), enums("place", SLEEP_PLACE), set("transitions"),
                    headers("started_at", "入睡时间", "ended_at", "醒来时间", "place", "地点")),
            child("baths", "洗澡", "id child_id started_at ended_at water_temp_c hair_washed cord_status note created_by created_at updated_at deleted version device_id",
                    set("started_at", "ended_at"), set("hair_washed"), enums("cord_status", CORD_STATUS), set(),
                    headers("started_at", "时间", "ended_at", "结束时间", "water_temp_c", "水温(℃)", "hair_washed", "洗头", "cord_status", "脐带状态")),
            child("spits", "吐奶", "id child_id started_at amount timing note created_by created_at updated_at deleted version device_id",
                    set("started_at"), set(), enums("amount", SPIT_AMOUNT, "timing", SPIT_TIMING), set(),
                    headers("started_at", "时间", "amount", "量级", "timing", "时机")),
            child("pump_sessions", "泵奶", "id child_id started_at amount_ml side storage expires_at note created_by created_at updated_at deleted version device_id",
                    set("started_at", "expires_at"), set(), enums("side", PUMP_SIDE, "storage", MILK_STORAGE), set(),
                    headers("started_at", "时间", "amount_ml", "奶量(ml)", "side", "侧", "storage", "储存", "expires_at", "过期时间")),
            child("measurements", "健康指标", "id child_id type value method measured_at note created_by created_at updated_at deleted version device_id",
                    set("measured_at"), set(), enums("type", MEASUREMENT_TYPE, "method", MEASUREMENT_METHOD), set(),
                    headers("type", "指标", "value", "数值", "method", "部位/范围", "measured_at", "测量时间")),
            child("medications", "补剂用药", "id child_id category name product_id brand_snapshot spec_snapshot dose unit route started_at ended_at note created_by created_at updated_at deleted version device_id",
                    set("started_at", "ended_at"), set(), enums("category", MEDICATION_CATEGORY), set(),
                    headers("category", "类别", "name", "名称", "product_id", "产品ID", "brand_snapshot", "品牌", "spec_snapshot", "规格", "dose", "剂量", "unit", "单位", "route", "途径", "started_at", "开始时间", "ended_at", "结束时间")),
            child("vaccinations", "疫苗", "id child_id vaccine_name dose_no scheduled_date actual_date status institution batch_no reaction next_date note created_by created_at updated_at deleted version device_id",
                    set("scheduled_date", "actual_date", "next_date"), set(), enums("status", VACCINE_STATUS), set(),
                    headers("vaccine_name", "疫苗名称", "dose_no", "剂次", "scheduled_date", "计划日期", "actual_date", "接种日期", "status", "状态", "institution", "接种机构", "batch_no", "批号", "reaction", "接种反应", "next_date", "下次日期")),
            child("follow_ups", "随访", "id child_id type scheduled_date actual_date institution result next_date note created_by created_at updated_at deleted version device_id",
                    set("scheduled_date", "actual_date", "next_date"), set(), enums("type", FOLLOW_UP_TYPE), set(),
                    headers("type", "类型", "scheduled_date", "计划日期", "actual_date", "完成日期", "institution", "机构", "result", "结果", "next_date", "下次日期")),
            child("visits", "就医", "id child_id visited_at hospital department doctor chief_complaint diagnosis prescription cost note created_by created_at updated_at deleted version device_id",
                    set("visited_at"), set(), Map.of(), set(),
                    headers("visited_at", "就诊时间", "hospital", "医院", "department", "科室", "doctor", "医生", "chief_complaint", "主诉", "diagnosis", "诊断", "prescription", "处方", "cost", "费用")),
            global("attachments", "附件索引", "id owner_type owner_id local_path mime created_at deleted version device_id",
                    set("created_at"), set(), enums("owner_type", ATTACHMENT_OWNER), Set.of("deleted", "device_id"),
                    headers("owner_type", "归属类型", "owner_id", "归属记录ID", "local_path", "文件路径", "mime", "文件类型")),
            child("reminders", "提醒", "id child_id source_type source_id title body trigger_at repeat_rule enabled last_fired_at note created_at updated_at deleted version device_id",
                    set("trigger_at", "last_fired_at"), set("enabled"), enums("source_type", REMINDER_SOURCE, "repeat_rule", REMINDER_REPEAT), set(),
                    headers("source_type", "来源类型", "source_id", "来源ID", "title", "标题", "body", "内容", "trigger_at", "触发时间", "repeat_rule", "重复", "enabled", "已启用", "last_fired_at", "上次送达"))
    );

    private ExportTableSpecs() {
    }

    public static List<TableSpec> included(boolean includeReminders) {
        return ALL.stream().filter(s -> includeReminders || !DERIVED_TABLE.equals(s.table())).toList();
    }

    private static TableSpec child(String table, String csvName, String columns, Set<String> times,
                                   Set<String> bools, Map<String, Map<String, String>> enums,
                                   Set<String> extraSkips, Map<String, String> headers) {
        Set<String> allTimes = new LinkedHashSet<>(times);
        allTimes.add("created_at");
        Set<String> skips = new LinkedHashSet<>(SKIP_SYNC);
        skips.add("child_id");
        skips.addAll(extraSkips);
        return new TableSpec(table, csvName, Scope.PER_CHILD, columns(columns), Set.copyOf(allTimes), bools, enums,
                Set.copyOf(skips), withCommon(headers));
    }

    private static TableSpec global(String table, String csvName, String columns, Set<String> times,
                                    Set<String> bools, Map<String, Map<String, String>> enums,
                                    Set<String> skips, Map<String, String> headers) {
        return new TableSpec(table, csvName, Scope.GLOBAL, columns(columns), times, bools, enums, skips, withCommon(headers));
    }

    private static List<String> columns(String value) {
        return List.of(value.split(" "));
    }

    private static Map<String, String> withCommon(Map<String, String> extra) {
        Map<String, String> result = new LinkedHashMap<>(COMMON_HEADERS);
        result.putAll(extra);
        return Map.copyOf(result);
    }

    private static Set<String> set(String... values) {
        return Set.of(values);
    }

    private static Map<String, String> headers(String... values) {
        return map(values);
    }

    private static Map<String, String> map(String... values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put(values[i], values[i + 1]);
        return Map.copyOf(result);
    }

    private static Map<String, Map<String, String>> enums(Object... values) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            @SuppressWarnings("unchecked") Map<String, String> labels = (Map<String, String>) values[i + 1];
            result.put((String) values[i], labels);
        }
        return Map.copyOf(result);
    }

}
