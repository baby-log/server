package com.babylog.biz.media.vo;

/**
 * 下载端点出参（媒体对象字节 + 展示用元信息）。
 *
 * <p>同步返回 {@code ResponseEntity<byte[]>}：与 {@code BabyLogExportController} 同款，
 * 不用 StreamingResponseBody —— 异步二次 dispatch 会重走 app-user 注解鉴权，
 * 但 Sa-Token 请求上下文已清，导致完整文件后追加 R 错误体。
 *
 * <p>objectKey 只用于生成下载文件名，不暴露后端凭据 / 绝对路径以外的信息。
 */
public record MediaDownload(String objectKey, String mimeType, byte[] data) {
}
