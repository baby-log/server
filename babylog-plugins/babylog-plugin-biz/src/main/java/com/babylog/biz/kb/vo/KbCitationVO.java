package com.babylog.biz.kb.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * RAG 问答的引用条目（K6-S / M76，红线三）。
 *
 * <p>由上游 {@code KbSearchResultDTO} 装配：{@code fileName} / {@code docId} / {@code chunkId}
 * 三件套保证<b>来源可追溯</b>，{@code excerpt} 是送进模型的原文片段。
 * 所有 Long（docId/chunkId/kbId）由 fastjson2 序列化成字符串。
 */
@Data
public class KbCitationVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 引用编号（从 1 起），模型答案里用 [1] [2] 标注 */
    private Integer index;

    /** 来源文件名 */
    private String fileName;

    /** 来源文档 ID（ai_kb_document.id） */
    private Long docId;

    /** 命中分块 ID（ai_kb_chunk.id） */
    private Long chunkId;

    /** 来源知识库 ID */
    private Long kbId;

    /** 相似度分数（上游可能不返回，null 键缺省） */
    private Double score;

    /** 送进模型的原文片段（截取，防超长 prompt） */
    private String excerpt;
}
