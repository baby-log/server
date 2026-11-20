package com.babylog.biz.sync.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.sync.dto.SyncPushCmd;
import com.babylog.biz.sync.service.SyncService;
import com.babylog.biz.sync.support.SyncAccessScopeResolver;
import com.babylog.biz.sync.support.SyncChangeNotifier;
import com.babylog.biz.sync.vo.SyncChangesVO;
import com.babylog.biz.sync.vo.SyncPushResultVO;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpAppUtil;
import com.babylog.starter.core.context.LoginContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * App 端增量同步端点。
 *
 * <p>鉴权在<b>类上</b>声明 {@code @SaCheckLogin(app-user)}：这两个端点没有一条是匿名可用的，
 * 逐个方法加注解只会漏（A11 规则的存在理由）。token 走 {@code Authorization: <裸值>}，
 * 与 M5 认证端点同口径。
 *
 * <p>两个端点都是「账号私有」语义：拉取只返回 {@code user_id = 自己} 的行，
 * 推送只写自己名下（user_id 由 token 盖章，请求体里传什么都不算数）。
 */
@Tag(name = "App 同步（BabyLog）", description = "增量拉取 changes / 批量推送 push")
@RestController
@RequestMapping("/app/babylog/sync")
@SaCheckLogin(type = StpAppUtil.TYPE)
public class BabyLogSyncController {

    private final SyncService syncService;
    private final SyncChangeNotifier changeNotifier;
    private final SyncAccessScopeResolver accessScopeResolver;

    /** 长轮询挂起上限：与四端 {@code wait=25000} 对齐，留 2 秒给容器超时兜底 */
    private static final long MAX_WAIT_MS = 30_000L;

    public BabyLogSyncController(SyncService syncService,
                                 SyncChangeNotifier changeNotifier,
                                 SyncAccessScopeResolver accessScopeResolver) {
        this.syncService = syncService;
        this.changeNotifier = changeNotifier;
        this.accessScopeResolver = accessScopeResolver;
    }

    @Operation(summary = "增量拉取（按服务端游标 since + sinceId）")
    @GetMapping("/changes")
    public R<SyncChangesVO> changes(@RequestParam(required = false) Long since,
                                    @RequestParam(required = false) Long sinceId,
                                    @RequestParam(required = false) String categories,
                                    @RequestParam(required = false) Integer limit) {
        return R.ok(syncService.pull(currentUserId(), since, sinceId, categories, limit));
    }

    @Operation(summary = "批量推送（单次上限 200 条，逐条 version 乐观锁 upsert）")
    @PostMapping("/push")
    public R<SyncPushResultVO> push(@Validated @RequestBody SyncPushCmd cmd) {
        long userId = currentUserId();
        SyncPushResultVO result = syncService.push(userId, cmd);
        // M53：只要有行落库就抬高变更戳，让其它设备的 ping 立刻返回 changed=true。
        // M56：读侧可见范围是家庭全部 ACTIVE 成员，只 bump 推送者自己会让跨账号共享的
        // 成员永远感知不到；改为一次抬高整个可见范围。放在 Controller 层确保事务已提交。
        if (result.getUpserted() > 0) {
            changeNotifier.markChanged(accessScopeResolver.resolveAccess(userId).visibleUserIds());
        }
        return R.ok(result);
    }

    @Operation(summary = "M53/M56：轻量变更探测；带 wait 时挂起为长轮询，有写入即返回")
    @GetMapping("/ping")
    public R<Map<String, Object>> ping(@RequestParam(required = false) Long since,
                                       @RequestParam(required = false) Long wait) {
        long userId = currentUserId();
        long client = since == null ? 0L : since;
        long waitMs = normalizeWait(wait);
        long server = changeNotifier.lastChangeAt(userId);

        // 旧包不带 wait，或已有变更：立即返回，响应体与 M53 完全一致（兼容未重打的客户端）。
        if (waitMs == 0L || server > client) {
            return R.ok(pingData(server, client));
        }

        // M56：同步阻塞式长轮询，**不用 DeferredResult**。
        // 原因：Spring MVC 的 DeferredResult 在 async dispatch 时会重新执行
        // SaInterceptor.preHandle，此时 Sa-Token 的 ThreadLocal 上下文尚未重建，
        // 直接抛 SaTokenContextException → 1500（dev 服实测）。阻塞式只占用一个
        // Tomcat 工作线程，家庭用户量级（四端各一条挂起连接）完全可接受，
        // 且响应体、鉴权链路与旧版逐字一致。
        CountDownLatch latch = new CountDownLatch(1);
        String token = changeNotifier.subscribe(userId, stamp -> latch.countDown());
        try {
            // 二次判定：覆盖「读到旧值 → 登记等待者」之间恰好发生写入的竞态。
            // 有写入时 notifyWaiters 会 countDown，这里立即醒来；否则等到超时。
            long after = changeNotifier.lastChangeAt(userId);
            if (after <= client) {
                latch.await(waitMs, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            changeNotifier.unsubscribe(userId, token);
        }
        // 返回当时的真实戳：超时也回 200（不是 503），客户端据此立即发起下一次挂起；
        // 若期间恰好有写入则 changed=true，不会丢通知。
        return R.ok(pingData(changeNotifier.lastChangeAt(userId), client));
    }

    private static long normalizeWait(Long wait) {
        if (wait == null || wait <= 0L) {
            return 0L;
        }
        return Math.min(wait, MAX_WAIT_MS);
    }

    private static Map<String, Object> pingData(long server, long client) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("changed", server > client);
        data.put("lastChangeAt", server);
        data.put("serverTime", System.currentTimeMillis());
        return data;
    }

    /**
     * 取当前 app-user 账号。
     *
     * <p>正常路径上 {@code @SaCheckLogin} 会先抛出未登录异常，这里为 null 只可能是
     * 会话数据被清（Redis 掉了但 token 还没过期）——按 1002 回，让客户端走 refresh。
     */
    private static long currentUserId() {
        Long userId = LoginContext.getUserId();
        if (userId == null) {
            throw new ServiceException(GlobalErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
