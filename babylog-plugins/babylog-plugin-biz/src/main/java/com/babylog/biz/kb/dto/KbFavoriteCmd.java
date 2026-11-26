package com.babylog.biz.kb.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 收藏 / 取消收藏入参（K6-S / M76）。faqId 为字符串形式的 Long（全局 Long 字符串化契约）。 */
@Data
public class KbFavoriteCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** bl_kb_faq.id */
    private Long faqId;
}
