package com.babylog.biz.kb.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 育儿知识库 RAG 问答入参（K6-S / M76）。
 *
 * <p><b>全局契约</b>：JSON 里所有 Long 都是字符串（fastjson2 {@code WriteLongAsString}），
 * 客户端传 {@code kbId} 用字符串；null 键缺省、四端容忍缺键。
 * 客户端绝不传 userId——服务端从 app-user 裸 token 取。
 */
@Data
public class KbAskCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 家长的问题（必填，1~200 字） */
    private String question;

    /** 指定知识库；缺省 = 上游默认知识库（IKnowledgeBaseFacade 内部解析） */
    private Long kbId;

    /** 检索条数（1~10，缺省 5）；仅影响引用条目数量，不影响额度 */
    private Integer topK;
}
