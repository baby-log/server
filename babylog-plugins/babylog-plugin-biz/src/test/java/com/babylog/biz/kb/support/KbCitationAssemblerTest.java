package com.babylog.biz.kb.support;

import com.babylog.ai.kb.api.dto.KbSearchResultDTO;
import com.babylog.biz.kb.vo.KbCitationVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 引用装配单测（K6-S / M76）：溯源三件套、去重、空片段过滤、编号连续。 */
class KbCitationAssemblerTest {

    @Test
    void emptyHitsReturnEmptyList() {
        assertThat(KbCitationAssembler.assemble(null)).isEmpty();
        assertThat(KbCitationAssembler.assemble(List.of())).isEmpty();
    }

    @Test
    void keepsTraceFieldsAndNumbersContinuously() {
        KbSearchResultDTO first = hit("片段一", 7L, 11L);
        KbSearchResultDTO second = hit("片段二", 8L, 12L);
        List<KbCitationVO> out = KbCitationAssembler.assemble(List.of(first, second));

        assertThat(out).hasSize(2);
        assertThat(out.get(0).getIndex()).isEqualTo(1);
        assertThat(out.get(1).getIndex()).isEqualTo(2);
        assertThat(out.get(0).getFileName()).isEqualTo("权威育儿资料.pdf");
        assertThat(out.get(0).getDocId()).isEqualTo(7L);
        assertThat(out.get(0).getChunkId()).isEqualTo(11L);
        assertThat(out.get(0).getKbId()).isEqualTo(1L);
        assertThat(out.get(0).getExcerpt()).isEqualTo("片段一");
    }

    @Test
    void deduplicatesByDocAndChunkAndDropsBlankContent() {
        KbSearchResultDTO dup1 = hit("同一片段", 7L, 11L);
        KbSearchResultDTO dup2 = hit("同一片段", 7L, 11L);
        KbSearchResultDTO blank = hit("   ", 8L, 12L);
        KbSearchResultDTO nullContent = new KbSearchResultDTO();

        List<KbCitationVO> out = KbCitationAssembler.assemble(List.of(dup1, blank, dup2, nullContent));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getIndex()).isEqualTo(1);
    }

    private static KbSearchResultDTO hit(String content, Long docId, Long chunkId) {
        KbSearchResultDTO d = new KbSearchResultDTO();
        d.setContent(content);
        d.setFileName("权威育儿资料.pdf");
        d.setDocId(docId);
        d.setChunkId(chunkId);
        d.setKbId(1L);
        return d;
    }
}
