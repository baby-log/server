package com.babylog.biz.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.babylog.biz.appversion.controller.AdminAppVersionController;
import com.babylog.biz.device.controller.AdminDeviceController;
import com.babylog.biz.feedback.controller.AdminFeedbackController;
import com.babylog.biz.product.controller.AdminProductController;
import com.babylog.biz.recordtype.controller.AdminRecordTypeRuleController;
import com.babylog.starter.auth.account.StpSysUtil;
import com.babylog.starter.web.annotation.RepeatSubmit;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理端 controller 权限字符契约测试（M10）。
 *
 * <p>锁三件事：
 * ① /babylog/** 的每个 handler 方法都有 @SaCheckPermission 且 type = sys 体系；
 * ② 权限字符与菜单 SQL（V2.2.9 的 5010/5020/5030/5040 树）逐字一致——
 *    菜单改字或 controller 改字，这里立刻红，不会出现「按钮显隐与接口鉴权两张皮」；
 * ③ App 反馈端点必须保持匿名（无 @SaCheck*）且带 @RepeatSubmit 防刷。
 */
class AdminControllerPermissionContractTest {

    private static final List<Class<?>> ADMIN_CONTROLLERS = List.of(
            AdminProductController.class,
            AdminDeviceController.class,
            AdminAppVersionController.class,
            AdminFeedbackController.class,
            AdminRecordTypeRuleController.class);

    /** controller#method → 期望权限字符（与 V2.2.9 菜单 SQL 对齐） */
    private static final Map<String, String> EXPECTED_PERMS = buildExpectedPerms();

    private static Map<String, String> buildExpectedPerms() {
        Map<String, String> map = new HashMap<>();
        // 5010 内置产品库
        map.put("AdminProductController#page", "babylog:product:list");
        map.put("AdminProductController#get", "babylog:product:query");
        map.put("AdminProductController#edit", "babylog:product:edit");
        map.put("AdminProductController#remove", "babylog:product:remove");
        // 5020 设备查看
        map.put("AdminDeviceController#overview", "babylog:device:list");
        map.put("AdminDeviceController#kick", "babylog:device:kick");
        // 5030 版本公告
        map.put("AdminAppVersionController#page", "babylog:version:list");
        map.put("AdminAppVersionController#get", "babylog:version:query");
        map.put("AdminAppVersionController#create", "babylog:version:add");
        map.put("AdminAppVersionController#update", "babylog:version:edit");
        map.put("AdminAppVersionController#publish", "babylog:version:publish");
        map.put("AdminAppVersionController#remove", "babylog:version:remove");
        // 5040 意见反馈
        map.put("AdminFeedbackController#page", "babylog:feedback:list");
        map.put("AdminFeedbackController#reply", "babylog:feedback:reply");
        // 5050 记录类型规则（M41；无新增/删除端点——边界：不能云端新增类型）
        map.put("AdminRecordTypeRuleController#page", "babylog:recordTypeRule:list");
        map.put("AdminRecordTypeRuleController#get", "babylog:recordTypeRule:query");
        map.put("AdminRecordTypeRuleController#edit", "babylog:recordTypeRule:edit");
        return map;
    }

    @Test
    void everyAdminHandlerHasSysPermissionMatchingMenuSql() {
        List<String> violations = new java.util.ArrayList<>();
        int handlerCount = 0;
        for (Class<?> controller : ADMIN_CONTROLLERS) {
            for (Method method : controller.getDeclaredMethods()) {
                if (Arrays.stream(method.getAnnotations()).noneMatch(a -> a.annotationType().getSimpleName()
                        .endsWith("Mapping"))) {
                    continue;
                }
                handlerCount++;
                SaCheckPermission perm = method.getAnnotation(SaCheckPermission.class);
                if (perm == null || !StpSysUtil.TYPE.equals(perm.type())) {
                    violations.add(controller.getSimpleName() + "#" + method.getName() + " 缺 sys 体系权限注解");
                    continue;
                }
                String expected = EXPECTED_PERMS.get(controller.getSimpleName() + "#" + method.getName());
                // value 是 String[]（Sa-Token 支持 or 语义多值），本批约定每端点恰好一个权限字符
                if (expected == null || perm.value().length != 1 || !expected.equals(perm.value()[0])) {
                    violations.add(controller.getSimpleName() + "#" + method.getName()
                            + " 权限字符 " + java.util.Arrays.toString(perm.value())
                            + " 与菜单 SQL 期望不符（期望 " + expected + "）");
                }
            }
        }
        // 防规则空转：本批 5 个 controller 共 17 个 handler
        assertThat(handlerCount).isGreaterThanOrEqualTo(17);
        assertThat(violations).as("管理端权限字符契约违规：%s", violations).isEmpty();
    }

    @Test
    void appFeedbackEndpointStaysAnonymousWithRepeatSubmit() throws Exception {
        Method submit = com.babylog.biz.feedback.controller.AppFeedbackController.class
                .getDeclaredMethod("submit",
                        com.babylog.biz.feedback.dto.FeedbackSubmitCmd.class);
        assertThat(Arrays.stream(submit.getAnnotations())
                .noneMatch(a -> a.annotationType().getName().startsWith("cn.dev33.satoken.annotation.SaCheck")))
                .as("App 反馈端点设计上匿名（未登录可提），加 @SaCheck* 会把未开云同步的用户挡在外面")
                .isTrue();
        assertThat(submit.getAnnotation(RepeatSubmit.class))
                .as("匿名端点必须有 @RepeatSubmit 防刷（指纹未登录走 IP）")
                .isNotNull();
    }
}
