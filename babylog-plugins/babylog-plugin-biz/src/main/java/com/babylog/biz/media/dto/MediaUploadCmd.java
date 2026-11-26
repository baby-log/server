package com.babylog.biz.media.dto;

import lombok.Data;

/**
 * 媒体上传命令（App 端 multipart 表单字段）。
 *
 * <p>入站 Long 一律按全局契约由客户端发字符串（雪花 id 19 位，JS 会丢精度），
 * fastjson2 / Spring 宽松转换为 Long；userId 不由客户端传，服务端按 token 盖章。
 *
 * <p>K8 解锁后（24 ①-4 / ①-5）：本端点既承载<b>附件语义</b>（E6 就医附件 / V3.1 拍照导入），
 * 也承载已开云同步用户主动选择的<b>静态画面帧</b>（截图 / 照片，{@code image/*}）。
 * 是否允许上传画面由四端云同步总开关把关，服务端只认有效 app-user token，不接收客户端
 * 开关状态（本 Cmd 亦无 userId 字段，user_id 一律由 token 盖章）。原始帧流归 K9，
 * 不接摄像头帧流；{@code video/*} 由 service 侧拒绝（8423，M69 §一保持）。
 */
@Data
public class MediaUploadCmd {

    /** 所属宝宝 id（必填） */
    private Long childId;

    /** MIME 覆盖；为空时取 MultipartFile 的 contentType */
    private String mimeType;

    /** 关联记录类型（E6 附件天然带，如 VISIT / VACCINATION） */
    private String linkedRecordType;

    /** 关联记录 id（雪花；与 linkedRecordType 同时非空 ⇒ 已关联正式记录，不参与淘汰） */
    private Long linkedRecordId;

    /** 采集 / 识别时刻（epoch millis，可空） */
    private Long detectedAt;

    /** 是否定稿帧：0/1（确认瞬间转存记录附件时置 1） */
    private Integer isFinalFrame;

    /** 存储后端覆盖（dev 实测用；为空走默认 writeBackend）。生产不应由客户端指定 */
    private String backend;
}
