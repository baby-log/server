package com.babylog.biz.sync.support;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 写权限钩子单测（M16：拦截点亮）。
 *
 * <p>锁四件事：
 * <ol>
 *   <li><b>文档锁</b>：{@code WRITE_ENFORCEMENT_ENABLED == true}——M16（2026-11-13）
 *       龙哥拍板「越权写整条拒绝 8343」；改回 false 必须是有意为之；</li>
 *   <li><b>行为锁</b>：非 OWNER 家庭成员推 writableCategories 之外的分类 → 8343；</li>
 *   <li><b>兜底锁</b>：OWNER 全过、无家庭用户全过（V2.0 单用户模型行为零变化的回归锁）；</li>
 *   <li><b>边界锁</b>：基础设施表（category null）不按分类拦截（它们的边界是 8344/8350）。</li>
 * </ol>
 */
class MemberPermissionGuardTest {

    private static final long RECORD_ID = 88L;

    private final MemberPermissionGuard guard = new MemberPermissionGuard();

    private static SyncAccessScope familyScope(String role, Set<SyncCategory> writable) {
        return new SyncAccessScope(9L, role, List.of(1001L, 2002L),
                EnumSet.allOf(SyncCategory.class), writable);
    }

    @Test
    void writeEnforcementIsOnSinceM16() {
        // 文档锁：M15 落地时为 false，M16 翻 true 常开（越权写 8343 单条拒绝）
        assertThat(MemberPermissionGuard.WRITE_ENFORCEMENT_ENABLED)
                .as("M16 拍板：push 写拦截点亮（PROGRESS.md 会话 M16）")
                .isTrue();
    }

    @Test
    void viewerPushingCareRecordIsRejectedWith8343() {
        SyncAccessScope viewer = familyScope("VIEWER", EnumSet.noneOf(SyncCategory.class));

        assertThatThrownBy(() -> guard.checkWrite(SyncTable.DIAPERS, viewer, RECORD_ID, Map.of()))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(BabyLogErrorCode.SYNC_CATEGORY_DENIED.getCode()));
    }

    @Test
    void caregiverPushingMedicalRecordIsRejectedWith8343() {
        // 01B 第六节：CAREGIVER 健康/医疗只读
        SyncAccessScope caregiver = familyScope("CAREGIVER",
                EnumSet.of(SyncCategory.FEEDING, SyncCategory.CARE));

        assertThatThrownBy(() -> guard.checkWrite(SyncTable.VACCINATIONS, caregiver, RECORD_ID, Map.of()))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(BabyLogErrorCode.SYNC_CATEGORY_DENIED.getCode()));
    }

    @Test
    void caregiverPushingCareRecordPasses() {
        SyncAccessScope caregiver = familyScope("CAREGIVER",
                EnumSet.of(SyncCategory.FEEDING, SyncCategory.CARE));

        assertThatCode(() -> guard.checkWrite(SyncTable.DIAPERS, caregiver, RECORD_ID, Map.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void ownerPassesAllCategories() {
        // OWNER 是不可转让的兜底角色（01B），全分类放行
        SyncAccessScope owner = familyScope("OWNER", EnumSet.allOf(SyncCategory.class));

        for (SyncTable table : SyncTable.values()) {
            assertThatCode(() -> guard.checkWrite(table, owner, RECORD_ID, Map.of()))
                    .as("OWNER 推 %s 不应被拦", table.apiName())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void singleUserWithoutFamilyPassesEverything() {
        // 回归锁：无家庭用户读写全开，行为与 V2.0 零变化
        SyncAccessScope single = SyncAccessScope.singleUser(1001L);

        for (SyncTable table : SyncTable.values()) {
            assertThatCode(() -> guard.checkWrite(table, single, RECORD_ID, Map.of()))
                    .as("无家庭用户推 %s 不应被拦", table.apiName())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void infrastructureTablesAreNotIntercepted() {
        // 基础设施表（category null）不按分类拦截：family_members/member_permissions 的边界是
        // 整表拒推 8350（SyncServiceImpl 预检段），其余四张的边界是归属校验 8344
        SyncAccessScope viewer = familyScope("VIEWER", EnumSet.noneOf(SyncCategory.class));

        assertThatCode(() -> guard.checkWrite(SyncTable.CHILDREN, viewer, RECORD_ID, Map.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.checkWrite(SyncTable.MEMBER_PERMISSIONS, viewer, RECORD_ID, Map.of()))
                .doesNotThrowAnyException();
    }
}
