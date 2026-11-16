package com.babylog.biz.export.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.export.service.ExportService;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.starter.auth.account.StpAppUtil;
import com.babylog.starter.core.context.LoginContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 登录态内直接下载导出文件。没有下载 token、永久链接或公开文件存储；
 * 每次请求都经 app-user 鉴权，导出权限随家庭可见范围即时生效。
 *
 * 同步返回二进制而非 StreamingResponseBody：异步完成后的二次 dispatch 会重走
 * app-user 注解鉴权，但 Sa-Token 请求上下文已清，导致完整文件后追加 R 错误体。
 */
@Tag(name = "App 数据导出（BabyLog）", description = "服务端生成 Android 同规格 JSON / CSV ZIP")
@RestController
@RequestMapping("/app/babylog/export")
@SaCheckLogin(type = StpAppUtil.TYPE)
public class BabyLogExportController {

    private final ExportService exportService;

    public BabyLogExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @Operation(summary = "下载全量导出文件（format=json|csv）")
    @GetMapping
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(defaultValue = "false") boolean includeReminders) throws IOException {
        long userId = currentUserId();
        boolean json = switch (format == null ? "" : format.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "json" -> true;
            case "csv" -> false;
            default -> throw new ServiceException(GlobalErrorCode.PARAM_INVALID, "format 只支持 json 或 csv");
        };
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        if (json) exportService.writeJson(userId, includeReminders, buffer);
        else exportService.writeCsvZip(userId, includeReminders, buffer);
        byte[] body = buffer.toByteArray();
        String extension = json ? "json" : "zip";
        String filename = "筱筱记-数据导出-" + java.time.LocalDate.now() + "." + extension;
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(json ? MediaType.APPLICATION_JSON : MediaType.parseMediaType("application/zip"))
                .contentLength(body.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"babylog-export." + extension + "\"; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    private static long currentUserId() {
        Long userId = LoginContext.getUserId();
        if (userId == null) throw new ServiceException(GlobalErrorCode.UNAUTHORIZED);
        return userId;
    }
}
