package com.babylog.biz.export.service.impl;

import com.babylog.biz.export.service.ExportService;
import com.babylog.biz.export.support.ExportTableSpecs;
import com.babylog.biz.export.support.ExportTableSpecs.TableSpec;
import com.babylog.biz.sync.store.SyncRecordStore;
import com.babylog.biz.sync.support.SyncAccessScope;
import com.babylog.biz.sync.support.SyncAccessScopeResolver;
import com.babylog.biz.sync.support.SyncCursor;
import com.babylog.biz.sync.support.SyncTable;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 服务端导出实现。可见账号与分类减窄只信 SyncAccessScopeResolver；
 * 行查询复用同步存储的双段游标与归属谓词，内置产品另行合并（user_id=0 不在家庭账号集合内）。
 */
@Service
public class ExportServiceImpl implements ExportService {

    private static final int PAGE_SIZE = 500;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final LocalDateTime EPOCH = SyncCursor.toLocalDateTime(0L);

    private final SyncRecordStore store;
    private final SyncAccessScopeResolver accessScopeResolver;
    /** 独立文件 Mapper：不能使用 Web 全局 Long→String，恢复文件必须保留 JSON number。 */
    private final ObjectMapper fileMapper = new ObjectMapper();

    public ExportServiceImpl(SyncRecordStore store, SyncAccessScopeResolver accessScopeResolver) {
        this.store = store;
        this.accessScopeResolver = accessScopeResolver;
    }

    @Override
    public void writeJson(long userId, boolean includeReminders, OutputStream out) throws IOException {
        ExportData data = load(userId, includeReminders);
        long exportedAt = System.currentTimeMillis();
        try (JsonGenerator json = fileMapper.getFactory().createGenerator(out)) {
            json.writeStartObject();
            json.writeObjectFieldStart("meta");
            json.writeNumberField("schema_version", ExportTableSpecs.ANDROID_SCHEMA_VERSION);
            json.writeStringField("app_version", "server-3.2.0");
            json.writeNumberField("exported_at", exportedAt);
            json.writeStringField("exported_at_text", formatEpoch(exportedAt));
            json.writeBooleanField("include_reminders", includeReminders);
            json.writeNumberField("total_rows", data.totalRows());
            json.writeObjectField("table_counts", data.counts());
            json.writeEndObject();
            json.writeObjectFieldStart("tables");
            for (TableSpec spec : data.specs()) {
                json.writeFieldName(spec.table());
                json.writeStartArray();
                for (Map<String, Object> row : data.rows().get(spec.table())) json.writeObject(row);
                json.writeEndArray();
            }
            json.writeEndObject();
            json.writeEndObject();
        }
    }

    @Override
    public void writeCsvZip(long userId, boolean includeReminders, OutputStream out) throws IOException {
        ExportData data = load(userId, includeReminders);
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(out))) {
            for (TableSpec spec : data.specs()) {
                if (spec.scope() != ExportTableSpecs.Scope.GLOBAL) continue;
                writeEntry(zip, spec, "全局-" + spec.csvName() + ".csv", live(data.rows().get(spec.table())));
            }
            for (ChildRef child : data.children()) {
                String label = sanitize(child.archived() ? child.name() + "（已归档）" : child.name());
                for (TableSpec spec : data.specs()) {
                    if (spec.scope() != ExportTableSpecs.Scope.PER_CHILD) continue;
                    List<Map<String, Object>> rows = live(data.rows().get(spec.table())).stream()
                            .filter(row -> child.id() == longValue(row.get("child_id"))).toList();
                    writeEntry(zip, spec, label + "-" + spec.csvName() + ".csv", rows);
                }
            }
        }
    }

    private ExportData load(long userId, boolean includeReminders) {
        SyncAccessScope scope = accessScopeResolver.resolveAccess(userId);
        Set<SyncTable> readable = SyncTable.forCategories(scope.readableCategories());
        List<TableSpec> specs = ExportTableSpecs.included(includeReminders).stream()
                .filter(spec -> SyncTable.byApiName(spec.table()).map(readable::contains).orElse(false)).toList();
        Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;
        for (TableSpec spec : specs) {
            SyncTable table = SyncTable.byApiName(spec.table()).orElseThrow();
            List<Map<String, Object>> tableRows = new ArrayList<>(readAll(table, scope.visibleUserIds()));
            if (table == SyncTable.PRODUCTS) tableRows.addAll(readBuiltinProducts());
            List<Map<String, Object>> androidRows = tableRows.stream().map(row -> toAndroidRow(spec, row)).toList();
            rows.put(spec.table(), androidRows);
            counts.put(spec.table(), androidRows.size());
            total += androidRows.size();
        }
        return new ExportData(specs, rows, counts, total);
    }

    private List<Map<String, Object>> readAll(SyncTable table, List<Long> visibleUserIds) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDateTime since = EPOCH;
        long sinceId = 0L;
        while (true) {
            List<SyncRecordStore.Row> page = store.selectChanges(table, visibleUserIds, since, sinceId, PAGE_SIZE);
            if (page.isEmpty()) return result;
            page.forEach(row -> result.add(row.data()));
            SyncRecordStore.Row last = page.get(page.size() - 1);
            since = SyncCursor.toLocalDateTime(last.cursorMillis());
            sinceId = last.id();
            if (page.size() < PAGE_SIZE) return result;
        }
    }

    private List<Map<String, Object>> readBuiltinProducts() {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDateTime since = EPOCH;
        long sinceId = 0L;
        while (true) {
            List<SyncRecordStore.Row> page = store.selectBuiltinChanges(SyncTable.PRODUCTS, since, sinceId, PAGE_SIZE);
            if (page.isEmpty()) return result;
            page.forEach(row -> result.add(row.data()));
            SyncRecordStore.Row last = page.get(page.size() - 1);
            since = SyncCursor.toLocalDateTime(last.cursorMillis());
            sinceId = last.id();
            if (page.size() < PAGE_SIZE) return result;
        }
    }

    /** camelCase 同步行 → Android/Room snake_case；familyKey 的结构差异在导出边界显式转换。 */
    private static Map<String, Object> toAndroidRow(TableSpec spec, Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String column : spec.columns()) {
            Object value;
            if ("family_key".equals(column)) {
                value = input.get("familyKey");
                if (value == null) value = input.get("familyId");
                if (value != null) value = String.valueOf(value);
            } else {
                value = input.get(snakeToCamel(column));
            }
            result.put(column, value);
        }
        return result;
    }

    private static String snakeToCamel(String column) {
        StringBuilder result = new StringBuilder(column.length());
        boolean upper = false;
        for (char c : column.toCharArray()) {
            if (c == '_') upper = true;
            else if (upper) { result.append(Character.toUpperCase(c)); upper = false; }
            else result.append(c);
        }
        return result.toString();
    }

    private static List<Map<String, Object>> live(List<Map<String, Object>> rows) {
        return rows.stream().filter(row -> !boolValue(row.get("deleted"))).toList();
    }

    private static void writeEntry(ZipOutputStream zip, TableSpec spec, String filename,
                                   List<Map<String, Object>> rows) throws IOException {
        zip.putNextEntry(new ZipEntry(filename));
        OutputStreamWriter writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
        writer.write('\uFEFF');
        List<String> active = spec.columns().stream().filter(c -> !spec.csvSkipColumns().contains(c)).toList();
        List<String> header = new ArrayList<>();
        for (String column : active) {
            header.add(spec.headerNames().getOrDefault(column, column));
            if (spec.timeColumns().contains(column)) header.add(column + "_epoch");
        }
        writer.write(csvLine(header));
        for (Map<String, Object> row : rows) {
            List<String> cells = new ArrayList<>();
            for (String column : active) {
                Object value = row.get(column);
                if (spec.timeColumns().contains(column)) {
                    cells.add(formatTimeCell(value));
                    cells.add(value == null ? "" : String.valueOf(value));
                } else cells.add(formatCell(spec, column, value));
            }
            writer.write(csvLine(cells));
        }
        writer.flush();
        zip.closeEntry();
    }

    private static String csvLine(List<String> cells) {
        return cells.stream().map(ExportServiceImpl::csvEscape)
                .collect(java.util.stream.Collectors.joining(",")) + "\r\n";
    }

    private static String formatCell(TableSpec spec, String column, Object value) {
        if (value == null) return "";
        if (spec.boolColumns().contains(column)) return boolValue(value) ? "是" : "否";
        Map<String, String> labels = spec.enumColumns().get(column);
        if (labels != null) return labels.getOrDefault(String.valueOf(value), String.valueOf(value));
        return String.valueOf(value);
    }

    private static String formatTimeCell(Object value) {
        if (!(value instanceof Number number)) return "";
        return formatEpoch(number.longValue());
    }

    private static String formatEpoch(long epoch) {
        return Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(TIME_FORMAT);
    }

    private static String csvEscape(String value) {
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static String sanitize(String name) {
        String result = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return result.isEmpty() ? "宝宝" : result;
    }

    private static boolean boolValue(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.longValue() != 0L;
        return "1".equals(String.valueOf(value)) || "true".equals(String.valueOf(value));
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0L; }
    }

    private record ExportData(List<TableSpec> specs, Map<String, List<Map<String, Object>>> rows,
                              Map<String, Integer> counts, int totalRows) {
        List<ChildRef> children() {
            return live(rows.getOrDefault("children", List.of())).stream()
                    .map(row -> new ChildRef(longValue(row.get("id")),
                            row.get("name") == null ? "宝宝" : String.valueOf(row.get("name")),
                            boolValue(row.get("archived")), longValue(row.get("created_at"))))
                    .sorted(Comparator.comparingLong(ChildRef::createdAt).thenComparingLong(ChildRef::id))
                    .toList();
        }
    }

    private record ChildRef(long id, String name, boolean archived, long createdAt) {
    }
}
