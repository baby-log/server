package com.babylog.biz.kb.support;

import com.babylog.ai.kb.api.dto.KbSearchResultDTO;
import com.babylog.biz.kb.vo.KbCitationVO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 把上游检索结果装配成「引用条目」（K6-S / M76，{@code 10} 号 §4.1 红线一 + 红线三）。
 *
 * <p><b>不重造检索</b>：入参直接是 {@code IKnowledgeBaseFacade.search()} 的返回，
 * 本类只做「过滤空片段 → 按 (docId, chunkId) 去重 → 编号 → 截断摘录」，
 * 不碰 BM25 / 向量 / 重排，那套全在上游 ai-kb 插件。
 *
 * <p><b>来源可追溯</b>：{@code fileName} + {@code docId} + {@code chunkId} 三件套原样透传，
 * 客户端可据此下钻到上游文档；任何一条引用都不允许只有模型生成文本而没有溯源字段。
 */
public final class KbCitationAssembler {

    /** 单条摘录上限，防把整篇文档塞进 prompt 烧额度 */
    private static final int EXCERPT_LIMIT = 300;

    private KbCitationAssembler() {
    }

    public static List<KbCitationVO> assemble(List<KbSearchResultDTO> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<KbCitationVO> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int index = 1;
        for (KbSearchResultDTO hit : hits) {
            if (hit == null || hit.getContent() == null || hit.getContent().isBlank()) {
                continue;
            }
            String key = hit.getDocId() + ":" + hit.getChunkId();
            if (!seen.add(key)) {
                continue;
            }
            KbCitationVO vo = new KbCitationVO();
            vo.setIndex(index++);
            vo.setFileName(hit.getFileName());
            vo.setDocId(hit.getDocId());
            vo.setChunkId(hit.getChunkId());
            vo.setKbId(hit.getKbId());
            vo.setScore(hit.getScore());
            vo.setExcerpt(clip(hit.getContent().trim()));
            out.add(vo);
        }
        return out;
    }

    private static String clip(String text) {
        return text.length() <= EXCERPT_LIMIT ? text : text.substring(0, EXCERPT_LIMIT) + "…";
    }
}
