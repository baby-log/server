package com.babylog.biz.media.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.media.dto.MediaConfirmCmd;
import com.babylog.biz.media.dto.MediaUploadCmd;
import com.babylog.biz.media.service.MediaFileService;
import com.babylog.biz.media.service.MediaQuotaService;
import com.babylog.biz.media.vo.MediaDownload;
import com.babylog.biz.media.vo.MediaFileVO;
import com.babylog.biz.media.vo.MediaQuotaVO;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpAppUtil;
import com.babylog.starter.core.context.LoginContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * App 端媒体附件端点（K2 / M69）。
 *
 * <p>与既有 App 端点同口径：类级 {@code @SaCheckLogin(app-user)}、
 * token 走 {@code Authorization: <裸值>}、userId 一律从 token 取（请求传什么都不算数）。
 * 端点路径不占用 {@code /sync/*} 命名空间。
 *
 * <p>下载沿用 {@code BabyLogExportController} 的<b>同步</b> {@code ResponseEntity<byte[]>}：
 * 不用 StreamingResponseBody —— 异步二次 dispatch 会重走 app-user 注解鉴权，
 * 但 Sa-Token 请求上下文已清，导致完整文件后追加 R 错误体。
 */
@Tag(name = "App 媒体附件（BabyLog）", description = "上传 / 查询 / 下载 / 删除 / 确认 / 配额读")
@RestController
@RequestMapping("/app/babylog/media")
@SaCheckLogin(type = StpAppUtil.TYPE)
public class BabyLogMediaController {

    private final MediaFileService mediaFileService;
    private final MediaQuotaService quotaService;

    public BabyLogMediaController(MediaFileService mediaFileService, MediaQuotaService quotaService) {
        this.mediaFileService = mediaFileService;
        this.quotaService = quotaService;
    }

    @Operation(summary = "上传媒体附件（multipart；V3 前仅附件语义，拒 video/*）")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<MediaFileVO> upload(@RequestParam("file") MultipartFile file,
                                 @ModelAttribute MediaUploadCmd cmd) throws IOException {
        return R.ok(mediaFileService.upload(currentUserId(), cmd, file.getBytes(), file.getContentType()));
    }

    @Operation(summary = "媒体元数据详情（不在家庭可见范围内返回 8420）")
    @GetMapping("/file/{id}")
    public R<MediaFileVO> get(@PathVariable("id") Long id) {
        return R.ok(mediaFileService.get(currentUserId(), id));
    }

    @Operation(summary = "按宝宝列媒体文件（家庭可见范围内，createdAt 降序）")
    @GetMapping("/files")
    public R<List<MediaFileVO>> list(@RequestParam("childId") Long childId) {
        return R.ok(mediaFileService.list(currentUserId(), childId));
    }

    @Operation(summary = "下载媒体字节（按行内 storage_backend 路由）")
    @GetMapping("/file/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable("id") Long id) {
        MediaDownload download = mediaFileService.download(currentUserId(), id);
        MediaType contentType = safeMediaType(download.mimeType());
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(download.data().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileNameOf(download.objectKey()) + "\"")
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.data());
    }

    @Operation(summary = "删除媒体（先物理删、再 DB 逻辑删，两步）")
    @DeleteMapping("/file/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        mediaFileService.delete(currentUserId(), id);
        return R.ok();
    }

    @Operation(summary = "确认 / 定稿帧转存（关联正式记录后长期保留，不随淘汰删除）")
    @PostMapping("/file/{id}/confirm")
    public R<MediaFileVO> confirm(@PathVariable("id") Long id, @RequestBody MediaConfirmCmd cmd) {
        return R.ok(mediaFileService.confirm(currentUserId(), id, cmd));
    }

    @Operation(summary = "读宝宝媒体容量配额（初始容量 / 调整后 / 已用 / 剩余）")
    @GetMapping("/quota")
    public R<MediaQuotaVO> quota(@RequestParam("childId") Long childId) {
        return R.ok(quotaService.quota(childId, currentUserId()));
    }

    private static MediaType safeMediaType(String mime) {
        if (mime == null || mime.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mime);
        } catch (RuntimeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String fileNameOf(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return "media.bin";
        }
        int slash = objectKey.lastIndexOf('/');
        String name = slash < 0 ? objectKey : objectKey.substring(slash + 1);
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static long currentUserId() {
        Long userId = LoginContext.getUserId();
        if (userId == null) {
            throw new ServiceException(GlobalErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
