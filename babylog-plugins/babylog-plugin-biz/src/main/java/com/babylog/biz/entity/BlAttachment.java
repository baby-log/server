package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 附件（同步表，App 端 Room attachments 对齐）。照片走 plugin-file 存储，
 * remote_key 为远端文件 key（App 端 V2 上传后回填，对应其规划的 remote_key 列）。
 * App 端无 updated_at / created_by，服务端业务列对齐不补。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_attachments")
public class BlAttachment extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 宿主类型：VISIT/VACCINATION/MEASUREMENT */
    private String ownerType;

    /** 宿主记录行 id */
    private Long ownerId;

    /** App 端本地路径（V1 不上传时保留） */
    private String localPath;

    /** 远端文件 key（plugin-file 上传后回填） */
    private String remoteKey;

    /** MIME 类型 */
    private String mime;

    /** App 端创建时刻（epoch millis） */
    private Long createdAt;

    /** 来源设备标识 */
    private String deviceId;
}
