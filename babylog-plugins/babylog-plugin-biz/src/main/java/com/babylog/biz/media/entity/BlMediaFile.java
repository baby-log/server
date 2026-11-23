package com.babylog.biz.media.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.starter.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 媒体对象元数据（K2 / M69，服务端专用表）。
 *
 * <p><b>不进 {@code SyncTable} 枚举、不参与 {@code /sync/changes}</b>：与
 * {@code bl_family} / {@code bl_device} / {@code bl_vaccine_schedule} / {@code bl_ai_quota} 同口径，
 * 走独立业务端点（上传 / 下载 / 删除 / 配额调整）。契约见 {@code 独立产品规划/24} ②。
 *
 * <p>{@code user_id} 是直写 {@code bl_*} 表的必填语义列（正服迁移漏写落 DEFAULT 0 拉不到的坑），
 * 由服务端按 token 写入，禁客户端传值；读侧按 {@code visibleUserIds} 家庭口径过滤。
 *
 * <p>{@code deleted} 继承 {@link BaseDO} 的 {@code @TableLogic}：
 * {@code deleteById} 即 DB 逻辑删（UPDATE deleted=1），配合 {@code server_updated_at ON UPDATE}
 * 自动刷新；<b>物理删由存储适配器单独执行</b>，两步缺一不可（24 ④-3）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_media_file")
public class BlMediaFile extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 所属宝宝 id */
    private Long childId;

    /** 归属用户 id（必填语义列，服务端按 token 写入） */
    private Long userId;

    /** 存储后端标识：local / cos / oss。按行路由，配置变更只影响新写入 */
    private String storageBackend;

    /** 对象键（含路径）；行内固化，存量不随配置变更重定位 */
    private String objectKey;

    /** 文件字节数 */
    private Long sizeBytes;

    /** MIME 类型（可空） */
    private String mimeType;

    /** 关联记录类型；与 linkedRecordId 同时非空 ⇒ 已关联正式记录，不参与任何淘汰 */
    private String linkedRecordType;

    /** 关联记录 id（雪花，可空） */
    private Long linkedRecordId;

    /** 采集 / 识别时刻（epoch millis，可空） */
    private Long detectedAt;

    /** 确认时刻（epoch millis）；NULL = 未确认 */
    private Long confirmedAt;

    /** 定稿帧标记：0/1（TINYINT，JSON 暴露为整数） */
    @TableField("is_final_frame")
    private Integer isFinalFrame;

    /** 创建时刻（epoch millis，容量满「删最早」排序键） */
    private Long createdAt;
}
