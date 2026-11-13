package com.babylog.biz.sync.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 写权限钩子单测（M15）。
 *
 * <p>锁两件事：
 * <ol>
 *   <li><b>文档锁</b>：{@code WRITE_ENFORCEMENT_ENABLED == false}——push 越权拒绝按
 *       M15 任务书拍板留给 M16；M16 点亮时这条会红，提醒同步打开拦截段并补
 *       8343 的行为用例；</li>
 *   <li><b>行为锁</b>：开关关闭期间，任何角色/分类的组合都不抛异常（单用户无家庭
 *       行为零变化的回归锁）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberPermissionGuardTest {

    @Mock
    private SyncAccessScopeResolver accessResolver;

    @Test
    void writeEnforcementStaysOffUntilM16() {
        // 文档锁：翻成 true 的那天必须是有意为之（M16 任务），不是顺手改的
        assertThat(MemberPermissionGuard.WRITE_ENFORCEMENT_ENABLED)
                .as("M15 拍板：push 按权限拒绝越权写入留 M16（PROGRESS.md 会话 M15）")
                .isFalse();
    }

    @Test
    void checkWriteNeverThrowsWhileEnforcementIsOff() {
        MemberPermissionGuard guard = new MemberPermissionGuard(accessResolver);
        org.mockito.Mockito.when(accessResolver.resolveAccess(1001L)).thenReturn(new SyncAccessScope(
                9L, "VIEWER", List.of(1001L),
                EnumSet.allOf(SyncCategory.class), EnumSet.noneOf(SyncCategory.class)));

        // VIEWER 推 MEDICAL：矩阵上无写权限，但 M15 不拦截（读已点亮、写拦截留 M16）
        assertThatCode(() -> guard.checkWrite(SyncTable.MEDICATIONS, 1001L, 88L, Map.of()))
                .doesNotThrowAnyException();
        // 基础设施表同样不拦
        assertThatCode(() -> guard.checkWrite(SyncTable.CHILDREN, 1001L, 88L, Map.of()))
                .doesNotThrowAnyException();
    }
}
