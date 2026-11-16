package com.babylog.biz.export.service.impl;

import com.babylog.biz.sync.store.SyncRecordStore;
import com.babylog.biz.sync.support.SyncAccessScope;
import com.babylog.biz.sync.support.SyncAccessScopeResolver;
import com.babylog.biz.sync.support.SyncCategory;
import com.babylog.biz.sync.support.SyncTable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExportServiceImplTest {

    private static final long USER_ID = 1001L;
    private static final long FAMILY_USER_ID = 2002L;
    private static final List<Long> VISIBLE = List.of(USER_ID, FAMILY_USER_ID);

    @Mock private SyncRecordStore store;
    @Mock private SyncAccessScopeResolver scopeResolver;
    private ExportServiceImpl service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ExportServiceImpl(store, scopeResolver);
        when(scopeResolver.resolveAccess(USER_ID)).thenReturn(new SyncAccessScope(301L, "OWNER", VISIBLE,
                EnumSet.allOf(SyncCategory.class), EnumSet.allOf(SyncCategory.class)));
    }

    @Test
    void jsonContainsTombstonesUsesAndroidColumnsAndMergesBuiltinProducts() throws Exception {
        stub(SyncTable.VACCINATIONS,
                row(SyncTable.VACCINATIONS, 11L, map("id", 11L, "childId", 101L, "vaccineName", "乙肝疫苗",
                        "doseNo", 1, "scheduledDate", 1763203200000L, "status", "DONE", "deleted", 0)),
                row(SyncTable.VACCINATIONS, 12L, map("id", 12L, "childId", 101L, "vaccineName", "自定义疫苗",
                        "scheduledDate", 1763289600000L, "status", "SKIPPED", "deleted", 1)));
        stub(SyncTable.FAMILY_MEMBERS, row(SyncTable.FAMILY_MEMBERS, 301L,
                map("id", 301L, "familyId", 88L, "userType", "WX_MINI", "displayName", "爸爸", "role", "OWNER",
                        "status", "ACTIVE", "joinedAt", 1763203200000L, "createdAt", 1763203200000L,
                        "updatedAt", 1763203200000L, "deleted", 0, "version", 1L, "deviceId", "server")));
        stub(SyncTable.PRODUCTS, row(SyncTable.PRODUCTS, 9001L,
                map("id", 9001L, "category", "FORMULA", "name", "自定义奶粉", "isBuiltin", 0, "deleted", 0)));
        when(store.selectBuiltinChanges(eq(SyncTable.PRODUCTS), any(), anyLong(), anyInt())).thenReturn(List.of(
                row(SyncTable.PRODUCTS, 1L, map("id", 1L, "category", "FORMULA", "name", "内置奶粉", "isBuiltin", 1, "deleted", 0))));

        JsonNode root = mapper.readTree(exportJson(false));

        assertThat(root.at("/meta/schema_version").asInt()).isEqualTo(10);
        assertThat(root.at("/meta/total_rows").asInt()).isEqualTo(root.at("/meta/table_counts").properties().stream()
                .mapToInt(e -> e.getValue().asInt()).sum());
        assertThat(root.at("/tables/vaccinations")).hasSize(2);
        assertThat(root.at("/tables/vaccinations/1/deleted").asInt()).isEqualTo(1);
        assertThat(root.at("/tables/vaccinations/0/scheduled_date").isNumber()).isTrue();
        assertThat(root.at("/tables/vaccinations/0/scheduled_date").asLong()).isEqualTo(1763203200000L);
        assertThat(root.at("/tables/vaccinations/0").has("scheduledDate")).isFalse();
        assertThat(root.at("/tables/family_members/0/family_key").asText()).isEqualTo("88");
        assertThat(root.at("/tables/family_members/0").has("family_id")).isFalse();
        assertThat(root.at("/tables/family_members/0").has("user_type")).isFalse();
        assertThat(root.at("/tables/products")).hasSize(2);
        assertThat(root.at("/tables").has("reminders")).isFalse();
        verify(store).selectChanges(eq(SyncTable.VACCINATIONS), eq(VISIBLE), any(), eq(0L), eq(500));
    }

    @Test
    void jsonPaginatesUntilStoreExhaustedInsteadOfTruncatingAtFiveHundred() throws Exception {
        List<SyncRecordStore.Row> first = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            first.add(row(SyncTable.VISITS, i, map("id", (long) i, "childId", 101L,
                    "visitedAt", 1763203200000L + i, "deleted", 0)));
        }
        when(store.selectChanges(eq(SyncTable.VISITS), any(), any(), anyLong(), eq(500)))
                .thenReturn(first, List.of(row(SyncTable.VISITS, 501L,
                        map("id", 501L, "childId", 101L, "visitedAt", 1763203200501L, "deleted", 0))));

        JsonNode root = mapper.readTree(exportJson(false));

        assertThat(root.at("/tables/visits")).hasSize(501);
        assertThat(root.at("/meta/table_counts/visits").asInt()).isEqualTo(501);
        verify(store, org.mockito.Mockito.times(2))
                .selectChanges(eq(SyncTable.VISITS), any(), any(), anyLong(), eq(500));
    }

    @Test
    void readableCategoryNarrowingAlsoNarrowsExport() throws Exception {
        when(scopeResolver.resolveAccess(USER_ID)).thenReturn(new SyncAccessScope(301L, "VIEWER", VISIBLE,
                EnumSet.of(SyncCategory.FEEDING), EnumSet.noneOf(SyncCategory.class)));
        stub(SyncTable.FEEDINGS, row(SyncTable.FEEDINGS, 21L,
                map("id", 21L, "childId", 101L, "type", "BOTTLE", "startedAt", 1763203200000L, "deleted", 0)));

        JsonNode root = mapper.readTree(exportJson(false));

        assertThat(root.at("/tables").has("feedings")).isTrue();
        assertThat(root.at("/tables").has("vaccinations")).isFalse();
        assertThat(root.at("/tables").has("follow_ups")).isFalse();
        assertThat(root.at("/tables").has("visits")).isFalse();
        assertThat(root.at("/tables").has("measurements")).isFalse();
        assertThat(root.at("/tables").has("children")).isTrue();
    }

    @Test
    void includeRemindersOptInMatchesAndroidDerivedTableRule() throws Exception {
        stub(SyncTable.REMINDERS, row(SyncTable.REMINDERS, 31L,
                map("id", 31L, "childId", 101L, "sourceType", "CUSTOM", "title", "复查", "enabled", 1, "deleted", 0)));
        JsonNode root = mapper.readTree(exportJson(true));
        assertThat(root.at("/meta/include_reminders").asBoolean()).isTrue();
        assertThat(root.at("/tables/reminders")).hasSize(1);
    }

    @Test
    void csvZipHasLiveRowsChineseHeadersTimePairsBomCrlfAndChildFiles() throws Exception {
        stub(SyncTable.CHILDREN,
                row(SyncTable.CHILDREN, 101L, map("id", 101L, "name", "筱/筱", "gender", "FEMALE",
                        "birthday", 1763203200000L, "archived", 0, "createdAt", 100L, "deleted", 0)),
                row(SyncTable.CHILDREN, 102L, map("id", 102L, "name", "二宝", "gender", "MALE",
                        "birthday", 1763203200000L, "archived", 1, "createdAt", 200L, "deleted", 0)));
        stub(SyncTable.VACCINATIONS,
                row(SyncTable.VACCINATIONS, 11L, map("id", 11L, "childId", 101L, "vaccineName", "乙肝,疫苗\"一号\"",
                        "doseNo", 1, "scheduledDate", 1763203200000L, "actualDate", 1763289600000L,
                        "status", "DONE", "note", "第一行\n第二行", "createdAt", 300L, "deleted", 0)),
                row(SyncTable.VACCINATIONS, 12L, map("id", 12L, "childId", 101L, "vaccineName", "不应出现",
                        "scheduledDate", 1763203200000L, "status", "SKIPPED", "deleted", 1)));

        Map<String, byte[]> files = unzip(exportCsv(false));

        assertThat(files).containsKeys("筱_筱-疫苗.csv", "二宝（已归档）-疫苗.csv", "全局-宝宝列表.csv");
        String csv = new String(files.get("筱_筱-疫苗.csv"), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("计划日期,scheduled_date_epoch,接种日期,actual_date_epoch");
        assertThat(csv).contains("已接种");
        assertThat(csv).contains("\"乙肝,疫苗\"\"一号\"\"\"");
        assertThat(csv).contains("\"第一行\n第二行\"");
        assertThat(csv).doesNotContain("不应出现");
        // 行结束符严格 CRLF；字段内容里的 LF 按 Android/RFC4180 原样保留在引号内
        assertThat(csv.split("\r\n", -1)).hasSize(3);
        String children = new String(files.get("全局-宝宝列表.csv"), StandardCharsets.UTF_8);
        assertThat(children).contains("女").contains("否").contains("是");
    }

    private byte[] exportJson(boolean includeReminders) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeJson(USER_ID, includeReminders, out);
        return out.toByteArray();
    }

    private byte[] exportCsv(boolean includeReminders) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeCsvZip(USER_ID, includeReminders, out);
        return out.toByteArray();
    }

    private void stub(SyncTable table, SyncRecordStore.Row... rows) {
        when(store.selectChanges(eq(table), any(), any(), anyLong(), anyInt())).thenReturn(List.of(rows));
    }

    private static SyncRecordStore.Row row(SyncTable table, long id, Map<String, Object> data) {
        return new SyncRecordStore.Row(table, id, 1000L + id, data);
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) result.put(entry.getName(), zip.readAllBytes());
        }
        return result;
    }
}
