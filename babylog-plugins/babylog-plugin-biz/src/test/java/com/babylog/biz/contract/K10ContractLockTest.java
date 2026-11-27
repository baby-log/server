package com.babylog.biz.contract;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.devicebind.controller.BabyLogDeviceBindController;
import com.babylog.biz.entity.BlFeeding;
import com.babylog.biz.sync.entity.BlFeedingSyncView;
import com.babylog.biz.sync.store.SyncViewRegistry;
import com.babylog.biz.sync.support.BaseDataTable;
import com.babylog.biz.sync.support.SyncTable;
import com.babylog.starter.auth.account.StpAppUtil;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * K10 契约锁 · 零业务改动：设备绑定 + 按钮进行中行闭环全部复用既有契约。
 *
 * <p>按 24 号契约 §八：TV 侧沿用 device-bind 四端点与 bl_device、8400~8403 错误码；
 * 按钮事件直接写现有 /sync/push 的 feeding 进行中行（START/PAUSE/RESUME/END 落 pauseEvents，
 * 结束同 ID 覆盖由 M53 支持），一组按钮按既有 recordType 区分。本节点零新增端点 / 表 /
 * 错误码 / 设备事件流通道，也不碰 19 张同步表语义——因此这里只做纯反射与查表断言，
 * 把这些「已存在且不得扩张」的既有契约逐条钉死，不启动 Spring 上下文、不连库。
 */
class K10ContractLockTest {

    @Test
    void deviceBindExposesExactlyFourEndpointsWithSplitLoginPolicy() {
        RequestMapping classMapping = BabyLogDeviceBindController.class.getAnnotation(RequestMapping.class);
        assertThat(classMapping).as("device-bind 类级路径").isNotNull();
        assertThat(classMapping.value()).containsExactly("/app/babylog/device-bind");

        Map<String, Method> byPath = new LinkedHashMap<>();
        for (Method method : BabyLogDeviceBindController.class.getDeclaredMethods()) {
            PostMapping post = method.getAnnotation(PostMapping.class);
            if (post != null) {
                assertThat(post.value()).as("%s 的 PostMapping 路径", method.getName()).hasSize(1);
                byPath.put(post.value()[0], method);
            }
        }
        assertThat(byPath).as("声明的 PostMapping 端点").hasSize(4);
        assertThat(byPath.keySet()).containsExactlyInAnyOrder("/apply", "/poll", "/confirm", "/deny");

        // 手机确认 / 拒绝必须 app-user 登录；电视申请 / 轮询保持匿名
        for (String path : List.of("/confirm", "/deny")) {
            SaCheckLogin login = byPath.get(path).getAnnotation(SaCheckLogin.class);
            assertThat(login).as("%s 需登录注解", path).isNotNull();
            assertThat(login.type()).as("%s 登录体系", path).isEqualTo(StpAppUtil.TYPE);
        }
        for (String path : List.of("/apply", "/poll")) {
            assertThat(byPath.get(path).getAnnotation(SaCheckLogin.class))
                    .as("%s 不得要求登录（电视匿名）", path).isNull();
        }
    }

    @Test
    void deviceBindErrorCodeBandHoldsExactlyFourConstants() {
        List<BabyLogErrorCode> inBand = Arrays.stream(BabyLogErrorCode.values())
                .filter(e -> e.getCode() >= 8400 && e.getCode() <= 8419)
                .toList();
        assertThat(inBand).as("8400~8419 号段常量数").hasSize(4);
        assertThat(inBand.stream().map(e -> e.name()).toList())
                .containsExactlyInAnyOrder("DEVICE_BIND_CODE_INVALID", "DEVICE_BIND_RATE_LIMITED",
                        "DEVICE_BIND_CONFIRM_LOCKED", "DEVICE_BIND_ALREADY_CLAIMED");

        // 逐字锁定名字与码的对应关系
        assertThat(BabyLogErrorCode.DEVICE_BIND_CODE_INVALID.getCode()).isEqualTo(8400);
        assertThat(BabyLogErrorCode.DEVICE_BIND_RATE_LIMITED.getCode()).isEqualTo(8401);
        assertThat(BabyLogErrorCode.DEVICE_BIND_CONFIRM_LOCKED.getCode()).isEqualTo(8402);
        assertThat(BabyLogErrorCode.DEVICE_BIND_ALREADY_CLAIMED.getCode()).isEqualTo(8403);

        // 不得出现 8404 及以上的 DEVICE_BIND 码（零新增错误码）
        assertThat(Arrays.stream(BabyLogErrorCode.values())
                .filter(e -> e.name().startsWith("DEVICE_BIND"))
                .filter(e -> e.getCode() >= 8404)
                .map(e -> e.name())
                .toList())
                .as("8404+ 的 DEVICE_BIND 码").isEmpty();
    }

    @Test
    void syncTablesStayAtNineteenWithNoDeviceEventOrButtonChannel() {
        assertThat(SyncTable.values()).as("同步表数量").hasSize(19);
        assertThat(Arrays.stream(SyncTable.values()).map(e -> e.name()).toList())
                .containsExactlyInAnyOrder("CHILDREN", "RECORD_TYPE_SETTINGS", "FAMILY_MEMBERS",
                        "MEMBER_PERMISSIONS", "PRODUCTS", "PRODUCT_PREFS", "REMINDERS", "FEEDINGS",
                        "DIAPERS", "SLEEPS", "BATHS", "SPITS", "PUMP_SESSIONS", "MEASUREMENTS",
                        "MEDICATIONS", "VACCINATIONS", "FOLLOW_UPS", "VISITS", "ATTACHMENTS");

        for (SyncTable table : SyncTable.values()) {
            assertThat(table.tableName()).as("%s 库表名", table.name()).startsWith("bl_");
            String lower = (table.name() + "|" + table.apiName() + "|" + table.tableName())
                    .toLowerCase(Locale.ROOT);
            assertThat(lower).as("%s 不得承载设备事件流", table.name())
                    .doesNotContain("device", "event", "button");
        }
        assertThat(Arrays.stream(SyncTable.values()).map(SyncTable::tableName).toList())
                .doesNotContain("bl_device");
    }

    @Test
    void viewRegistryDeclaresNineteenSyncSpecsAndFourBaseSpecs() {
        assertThat(SyncViewRegistry.syncSpecs()).as("同步规格数").hasSize(19);
        assertThat(SyncViewRegistry.baseSpecs()).as("base 规格数").hasSize(4);
        assertThat(BaseDataTable.values()).as("base 运营表数").hasSize(4);
    }

    @Test
    void feedingsSpecHasNoPullPredicateAndKeepsInProgressRejectNull() {
        SyncViewRegistry.SyncSpec feedings = SyncViewRegistry.syncSpec(SyncTable.FEEDINGS);
        assertThat(feedings.predicateProperty()).as("M53 移除 in_progress=0 拉取谓词").isNull();
        assertThat(feedings.predicateValue()).as("FEEDINGS 拉取谓词值").isNull();
        assertThat(feedings.rejectNullProperties()).containsExactly("inProgress");

        SyncViewRegistry.BaseSpec versions = SyncViewRegistry.baseSpec(BaseDataTable.APP_VERSIONS);
        assertThat(versions.predicateProperty()).as("版本表按 status 过滤").isEqualTo("status");
    }

    @Test
    void feedingTimerColumnsKeepDeclaredTypesOnEntityAndSyncView() {
        Map<String, Class<?>> expected = Map.<String, Class<?>>of(
                "inProgress", Integer.class,
                "pauseEvents", String.class,
                "endedAt", Long.class,
                "totalSeconds", Integer.class,
                "activeSeconds", Integer.class);
        for (Class<?> type : List.of(BlFeeding.class, BlFeedingSyncView.class)) {
            expected.forEach((name, fieldType) -> {
                Field field = findField(type, name);
                assertThat(field).as("%s.%s 声明（含继承）", type.getSimpleName(), name).isNotNull();
                assertThat(field.getType()).as("%s.%s 类型", type.getSimpleName(), name)
                        .isEqualTo(fieldType);
            });
        }
    }

    /** 沿继承链反射查找声明字段（计时五列声明在业务实体，同步视图实体继承）。 */
    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续向父类查找
            }
        }
        return null;
    }
}
