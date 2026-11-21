package com.babylog.biz.adminconsole.service;

import com.babylog.biz.adminconsole.vo.AdminPageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * M53：管理端「所有人 / 家庭 / 宝宝 / 全部记录」只读查询。
 *
 * <p>设计边界（与隐私口径一致）：
 * <ul>
 *   <li><b>只读</b>：全部走 {@code SELECT}，不提供任何写入、删除、导出端点；</li>
 *   <li><b>跨用户</b>：这是与 App 端 {@code /app/babylog/sync/**} 的本质区别——
 *       同步通道按 {@code user_id = 自己} 隔离，管理端在 sys-admin 体系下按运营需要查看全量；</li>
 *   <li><b>参数化</b>：所有用户输入走 {@code ?} 占位；唯一动态拼接的是表名，
 *       由 {@link #RECORD_TABLES} 白名单逐字校验，不接受任意标识符；</li>
 *   <li><b>列名归一</b>：数据库 snake_case 统一转成 camelCase 再出参，前端不需要知道物理列名。</li>
 * </ul>
 */
@Service
public class AdminConsoleService {

    /** 记录明细允许查询的物理表名（与 SyncTable.apiName 对齐，不接受拼接任意表名） */
    private static final Set<String> RECORD_TABLES = Set.of(
            "bl_feedings", "bl_diapers", "bl_sleeps", "bl_baths", "bl_spits",
            "bl_pump_sessions", "bl_measurements", "bl_medications",
            "bl_vaccinations", "bl_follow_ups", "bl_visits");

    /**
     * 记录明细各表的「业务发生时刻」列（BIGINT epoch millis）。前端「起始日期 / 截止日期」
     * 按业务日期筛选才符合直觉，而不是按 {@code server_updated_at}（最后编辑时刻）。
     * 列名只从这份常量映射取，且 {@code physical} 已过 {@link #RECORD_TABLES} 白名单，
     * 绝不拼接用户输入。
     */
    private static final Map<String, String> BUSINESS_TIME_COLUMNS = Map.ofEntries(
            Map.entry("bl_feedings", "started_at"),
            Map.entry("bl_diapers", "started_at"),
            Map.entry("bl_sleeps", "started_at"),
            Map.entry("bl_baths", "started_at"),
            Map.entry("bl_spits", "started_at"),
            Map.entry("bl_pump_sessions", "started_at"),
            Map.entry("bl_medications", "started_at"),
            Map.entry("bl_measurements", "measured_at"),
            Map.entry("bl_vaccinations", "scheduled_date"),
            Map.entry("bl_follow_ups", "scheduled_date"),
            Map.entry("bl_visits", "visited_at"));

    private final JdbcTemplate jdbc;

    public AdminConsoleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 概览：七个数量指标 */
    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userCount", count("SELECT COUNT(*) FROM sys_user WHERE deleted = 0"));
        result.put("familyCount", count("SELECT COUNT(*) FROM bl_family WHERE deleted = 0"));
        result.put("childCount", count("SELECT COUNT(*) FROM bl_children WHERE deleted = 0"));
        result.put("feedingCount", count("SELECT COUNT(*) FROM bl_feedings WHERE deleted = 0"));
        result.put("vaccinationCount", count("SELECT COUNT(*) FROM bl_vaccinations WHERE deleted = 0"));
        result.put("reminderCount", count("SELECT COUNT(*) FROM bl_reminders WHERE deleted = 0"));
        result.put("todayRecordCount", count(
                "SELECT COUNT(*) FROM bl_feedings WHERE deleted = 0 AND server_updated_at >= CURDATE()"));
        return result;
    }

    public AdminPageResult<Map<String, Object>> userPage(int pageNum, int pageSize, String keyword) {
        StringBuilder where = new StringBuilder(" WHERE u.deleted = 0");
        List<Object> args = new ArrayList<>();
        if (hasText(keyword)) {
            where.append(" AND (u.username LIKE ? OR u.nickname LIKE ? OR u.email LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        String from = " FROM sys_user u"
                + " LEFT JOIN (SELECT owner_user_id, COUNT(*) AS cnt FROM bl_children"
                + "            WHERE deleted = 0 GROUP BY owner_user_id) c ON c.owner_user_id = u.id"
                + " LEFT JOIN (SELECT user_id, COUNT(*) AS cnt FROM bl_device"
                + "            WHERE deleted = 0 GROUP BY user_id) d ON d.user_id = u.id"
                + where;
        String select = "SELECT u.id AS user_id, u.username, u.nickname, u.email, u.status,"
                + " u.create_time, COALESCE(c.cnt, 0) AS child_count, COALESCE(d.cnt, 0) AS device_count";
        return page(select, from, "u.create_time DESC, u.id DESC", pageNum, pageSize, args);
    }

    public AdminPageResult<Map<String, Object>> familyPage(int pageNum, int pageSize, String keyword) {
        StringBuilder where = new StringBuilder(" WHERE f.deleted = 0");
        List<Object> args = new ArrayList<>();
        if (hasText(keyword)) {
            where.append(" AND (f.name LIKE ? OR u.username LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        String from = " FROM bl_family f"
                + " LEFT JOIN sys_user u ON u.id = f.owner_user_id"
                + " LEFT JOIN (SELECT family_id, COUNT(*) AS cnt FROM bl_family_members"
                + "            WHERE deleted = 0 AND status = 'ACTIVE' GROUP BY family_id) m ON m.family_id = f.id"
                + " LEFT JOIN (SELECT family_id, COUNT(*) AS cnt FROM bl_children"
                + "            WHERE deleted = 0 GROUP BY family_id) c ON c.family_id = f.id"
                + where;
        String select = "SELECT f.id AS family_id, f.name AS family_name, f.owner_user_id,"
                + " u.username AS owner_name, COALESCE(m.cnt, 0) AS member_count,"
                + " COALESCE(c.cnt, 0) AS child_count, f.create_time";
        return page(select, from, "f.id DESC", pageNum, pageSize, args);
    }

    public AdminPageResult<Map<String, Object>> childPage(int pageNum, int pageSize,
                                                          String userId, String familyId, String keyword) {
        StringBuilder where = new StringBuilder(" WHERE c.deleted = 0");
        List<Object> args = new ArrayList<>();
        appendEq(where, args, "c.owner_user_id", userId);
        appendEq(where, args, "c.family_id", familyId);
        if (hasText(keyword)) {
            where.append(" AND c.name LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        String from = " FROM bl_children c" + where;
        // M57：feeding_method 随宝宝档案下发，后台只读列表一并展示（列白名单显式枚举）
        String select = "SELECT c.id, c.name, c.gender, c.birthday, c.is_premature,"
                + " c.feeding_method, c.archived, c.owner_user_id, c.family_id, c.created_at, c.deleted";
        return page(select, from, "c.created_at DESC, c.id DESC", pageNum, pageSize, args);
    }

    /**
     * 记录明细（通用表）。{@code table} 参数先映射到白名单物理表名，绝不拼进 SQL。
     * 日期过滤按各表的业务发生时刻（{@link #BUSINESS_TIME_COLUMNS}），
     * 非法格式才回落到 {@code server_updated_at} 字符串比较。
     */
    public AdminPageResult<Map<String, Object>> recordPage(int pageNum, int pageSize,
                                                           String table, String userId,
                                                           String familyId, String childId,
                                                           String from, String to) {
        String physical = resolveTable(table);
        StringBuilder where = new StringBuilder(" WHERE t.deleted = 0");
        List<Object> args = new ArrayList<>();
        appendEq(where, args, "t.user_id", userId);
        appendEq(where, args, "t.child_id", childId);
        if (hasText(familyId)) {
            where.append(" AND t.child_id IN (SELECT id FROM bl_children WHERE family_id = ? AND deleted = 0)");
            args.add(Long.parseLong(familyId.trim()));
        }
        // M53：日期筛选按「业务发生时刻」而非最后编辑时刻。异构表列名不同，走白名单常量映射；
        // 非 YYYY-MM-DD 的入参（或解析失败）回落到 server_updated_at 字符串比较，保持旧行为不炸。
        String timeColumn = BUSINESS_TIME_COLUMNS.get(physical);
        if (hasText(from)) {
            String value = from.trim();
            Long millis = timeColumn == null ? null : dayMillis(value, false);
            if (millis != null) {
                where.append(" AND t.").append(timeColumn).append(" >= ?");
                args.add(millis);
            } else {
                where.append(" AND t.server_updated_at >= ?");
                args.add(value);
            }
        }
        if (hasText(to)) {
            String value = to.trim();
            Long millis = timeColumn == null ? null : dayMillis(value, true);
            if (millis != null) {
                where.append(" AND t.").append(timeColumn).append(" <= ?");
                args.add(millis);
            } else {
                where.append(" AND t.server_updated_at <= ?");
                // 前端只下发 YYYY-MM-DD 时补到当天最后一刻，否则「截止今天」会漏掉当天全部记录
                args.add(value.length() == 10 ? value + " 23:59:59.999" : value);
            }
        }
        String fromClause = " FROM " + physical + " t" + where;
        String select = "SELECT t.*";
        AdminPageResult<Map<String, Object>> result =
                page(select, fromClause, "t.server_updated_at DESC, t.id DESC", pageNum, pageSize, args);
        // 前端表格统一展示「表标识 + 中文摘要」，由服务端按行拼好，避免前端遍历异构列
        List<Map<String, Object>> enriched = new ArrayList<>(result.getList().size());
        for (Map<String, Object> row : result.getList()) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            copy.put("table", physical.substring(3));
            copy.put("summary", summarize(row));
            enriched.add(copy);
        }
        return new AdminPageResult<>(enriched, result.getTotal(), result.getPageNum(), result.getPageSize());
    }

    /** 按行内可用列拼一段中文摘要（异构表统一展示用，缺列一律跳过不报错） */
    private static String summarize(Map<String, Object> row) {
        StringBuilder text = new StringBuilder();
        Object type = row.get("type");
        if (type != null) {
            text.append(type);
        }
        Object name = row.get("vaccineName");
        if (name != null) {
            if (!text.isEmpty()) {
                text.append(" · ");
            }
            text.append(name);
        }
        Object amount = row.get("amountMl");
        if (amount != null) {
            if (!text.isEmpty()) {
                text.append(" · ");
            }
            text.append(amount).append(" ml");
        }
        Object duration = row.get("durationS");
        if (duration != null) {
            if (!text.isEmpty()) {
                text.append(" · ");
            }
            text.append(duration).append(" 秒");
        }
        Object note = row.get("note");
        if (note != null && !String.valueOf(note).isBlank()) {
            if (!text.isEmpty()) {
                text.append(" · ");
            }
            text.append(note);
        }
        return text.isEmpty() ? "—" : text.toString();
    }

    public AdminPageResult<Map<String, Object>> vaccinationPage(int pageNum, int pageSize,
                                                                String userId, String childId,
                                                                String status) {
        StringBuilder where = new StringBuilder(" WHERE v.deleted = 0");
        List<Object> args = new ArrayList<>();
        appendEq(where, args, "v.user_id", userId);
        appendEq(where, args, "v.child_id", childId);
        if (hasText(status)) {
            where.append(" AND v.status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        String from = " FROM bl_vaccinations v"
                + " LEFT JOIN bl_children c ON c.id = v.child_id" + where;
        String select = "SELECT v.id, v.child_id, c.name AS child_name, v.vaccine_name, v.dose_no,"
                + " v.scheduled_date AS scheduled_at, v.actual_date AS done_at, v.status, v.deleted";
        return page(select, from, "v.scheduled_date DESC, v.id DESC", pageNum, pageSize, args);
    }

    public AdminPageResult<Map<String, Object>> reminderPage(int pageNum, int pageSize,
                                                             String userId, String childId) {
        StringBuilder where = new StringBuilder(" WHERE r.deleted = 0");
        List<Object> args = new ArrayList<>();
        appendEq(where, args, "r.user_id", userId);
        appendEq(where, args, "r.child_id", childId);
        String from = " FROM bl_reminders r"
                + " LEFT JOIN bl_children c ON c.id = r.child_id" + where;
        String select = "SELECT r.id, r.child_id, c.name AS child_name, r.source_type AS kind,"
                + " r.title, r.trigger_at AS due_at, r.enabled, r.deleted";
        return page(select, from, "r.trigger_at DESC, r.id DESC", pageNum, pageSize, args);
    }

    // ------------------------------------------------------------------
    // 通用分页与工具
    // ------------------------------------------------------------------

    private AdminPageResult<Map<String, Object>> page(String select, String from, String orderBy,
                                                      int pageNum, int pageSize, List<Object> args) {
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.min(Math.max(1, pageSize), 200);
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safePageSize);
        pageArgs.add((safePageNum - 1) * safePageSize);
        List<Map<String, Object>> rows = jdbc.queryForList(
                select + from + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?", pageArgs.toArray());
        List<Map<String, Object>> normalized = rows.stream().map(AdminConsoleService::camelKeys).toList();
        return new AdminPageResult<>(normalized, total == null ? 0L : total, safePageNum, safePageSize);
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    /**
     * {@code YYYY-MM-DD} → 当天起止的 epoch millis（系统时区，与 App 端落库口径一致）。
     * 非该格式或解析失败返回 {@code null}，调用方回落到 {@code server_updated_at} 字符串比较。
     */
    private static Long dayMillis(String value, boolean endOfDay) {
        if (value.length() != 10) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value);
            return endOfDay
                    ? date.atTime(23, 59, 59, 999_000_000)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    : date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String resolveTable(String apiName) {
        String candidate = hasText(apiName) ? apiName.trim().toLowerCase(Locale.ROOT) : "bl_feedings";
        String physical = candidate.startsWith("bl_") ? candidate : "bl_" + candidate;
        if (!RECORD_TABLES.contains(physical)) {
            throw new IllegalArgumentException("不支持查询的记录表：" + apiName);
        }
        return physical;
    }

    private static void appendEq(StringBuilder where, List<Object> args, String column, String value) {
        if (!hasText(value)) {
            return;
        }
        where.append(" AND ").append(column).append(" = ?");
        args.add(Long.parseLong(value.trim()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 物理列名 → camelCase（前端统一按驼峰取，Long 保持字符串化由 JSON 层处理） */
    private static Map<String, Object> camelKeys(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((key, value) -> {
            StringBuilder camel = new StringBuilder();
            boolean upperNext = false;
            for (char ch : key.toCharArray()) {
                if (ch == '_') {
                    upperNext = true;
                } else if (upperNext) {
                    camel.append(Character.toUpperCase(ch));
                    upperNext = false;
                } else {
                    camel.append(Character.toLowerCase(ch));
                }
            }
            result.put(camel.toString(), value);
        });
        return result;
    }
}
