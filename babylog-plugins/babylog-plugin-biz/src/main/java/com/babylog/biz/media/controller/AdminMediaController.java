package com.babylog.biz.media.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.media.dto.MediaQuotaAdjustCmd;
import com.babylog.biz.media.service.MediaMigrationService;
import com.babylog.biz.media.service.MediaQuotaService;
import com.babylog.biz.media.service.MediaRetentionService;
import com.babylog.biz.media.storage.MediaStorageRouter;
import com.babylog.biz.media.vo.MediaMigrateVO;
import com.babylog.biz.media.vo.MediaQuotaVO;
import com.babylog.biz.media.vo.MediaRetentionVO;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpSysUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 管理端媒体运维端点（K2 / M69）。
 *
 * <p>与既有管理端 controller 同口径：路径 {@code /babylog/**}（项目惯例无 {@code /admin} 前缀）、
 * sys-admin 体系鉴权。这里用<b>类级 {@code @SaCheckLogin(type = StpSysUtil.TYPE)}</b>：
 * 既满足 P0 ArchUnit A11 的鉴权信号，又不新增 {@code @SaCheckPermission} 权限字符
 * （权限字符须与菜单 SQL 逐字对应，K3 配额页落地时再一并登记；
 * {@code AdminControllerPermissionContractTest} 只遍历硬编码的 5 个历史 controller，
 * 本类不在其清单内，不破坏既有契约）。
 *
 * <p>淘汰 / 迁移是<b>运维显式触发</b>，不经 xxl-job 调度；xxl-job 执行器与任务启停交龙哥。
 */
@Tag(name = "BabyLog 媒体运维（管理端）", description = "配额调整 / 淘汰诊断 / 手动迁移 / 后端注册")
@RestController
@RequestMapping("/babylog/media")
@SaCheckLogin(type = StpSysUtil.TYPE)
public class AdminMediaController {

    private final MediaQuotaService quotaService;
    private final MediaRetentionService retentionService;
    private final MediaMigrationService migrationService;
    private final MediaStorageRouter router;

    public AdminMediaController(MediaQuotaService quotaService,
                                MediaRetentionService retentionService,
                                MediaMigrationService migrationService,
                                MediaStorageRouter router) {
        this.quotaService = quotaService;
        this.retentionService = retentionService;
        this.migrationService = migrationService;
        this.router = router;
    }

    @Operation(summary = "动态调整宝宝媒体容量（只写 adjustedBytes，initialBytes 不改写）")
    @PutMapping("/quota")
    public R<MediaQuotaVO> adjust(@RequestBody MediaQuotaAdjustCmd cmd) {
        if (cmd == null || cmd.getChildId() == null || cmd.getOwnerUserId() == null) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID);
        }
        return R.ok(quotaService.adjust(cmd.getChildId(), cmd.getOwnerUserId(), cmd.getAdjustedBytes()));
    }

    @Operation(summary = "手动触发一次淘汰（与 xxl-job 同一实现；不启调度器）")
    @PostMapping("/retention/run")
    public R<MediaRetentionVO> runRetention() {
        return R.ok(retentionService.run());
    }

    @Operation(summary = "存量对象手动迁移（配置变更只对新写入生效，存量走本工具）")
    @PostMapping("/migrate")
    public R<MediaMigrateVO> migrate(@RequestParam("from") String from,
                                     @RequestParam("to") String to,
                                     @RequestParam(value = "limit", defaultValue = "0") int limit) {
        return R.ok(migrationService.migrate(from, to, limit));
    }

    @Operation(summary = "已注册的存储后端标识（诊断：local / cos / oss）")
    @GetMapping("/backends")
    public R<Set<String>> backends() {
        return R.ok(router.registeredBackends());
    }
}
