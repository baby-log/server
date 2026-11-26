package com.babylog.biz.kb.service;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.entity.BlKbFaq;
import com.babylog.biz.kb.dto.KbFaqSaveCmd;
import com.babylog.biz.kb.vo.KbFaqVO;
import com.babylog.biz.mapper.BlKbFaqMapper;
import com.babylog.biz.mapper.BlKbFavoriteMapper;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 管理端 FAQ 维护单测（K6-S / M76）：来源必填、禁整篇搬运、上架默认值、删除清收藏。 */
@ExtendWith(MockitoExtension.class)
class AdminKbFaqServiceTest {

    @Mock
    private BlKbFaqMapper faqMapper;
    @Mock
    private BlKbFavoriteMapper favoriteMapper;

    private AdminKbFaqService service;

    @BeforeEach
    void setUp() {
        service = new AdminKbFaqService(faqMapper, favoriteMapper);
    }

    @Test
    void sourceIsMandatory() {
        KbFaqSaveCmd cmd = cmd();
        cmd.setSource("  ");

        assertThatThrownBy(() -> service.save(cmd))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(BabyLogErrorCode.KB_FAQ_SOURCE_REQUIRED.getCode()));
        verifyNoInteractions(faqMapper);
    }

    @Test
    void contentOver2000CharsIsRejected() {
        KbFaqSaveCmd cmd = cmd();
        cmd.setContent("x".repeat(2001));

        assertThatThrownBy(() -> service.save(cmd))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(GlobalErrorCode.PARAM_INVALID.getCode()));
        verifyNoInteractions(faqMapper);
    }

    @Test
    void savePersistsSourceAndDefaults() {
        when(faqMapper.insert(any(BlKbFaq.class))).thenReturn(1);

        KbFaqVO vo = service.save(cmd());

        ArgumentCaptor<BlKbFaq> captor = ArgumentCaptor.forClass(BlKbFaq.class);
        verify(faqMapper).insert(captor.capture());
        BlKbFaq row = captor.getValue();
        assertThat(row.getSource()).isEqualTo("国家卫健委《婴幼儿喂养指南》");
        assertThat(row.getCategory()).isEqualTo("FEEDING");
        assertThat(row.getEnabled()).isEqualTo(1);
        assertThat(row.getSortOrder()).isZero();
        assertThat(vo.getFavorited()).isZero();
    }

    private static KbFaqSaveCmd cmd() {
        KbFaqSaveCmd c = new KbFaqSaveCmd();
        c.setCategory("FEEDING");
        c.setTitle("拍嗝要点");
        c.setContent("喂奶后竖抱，轻拍背部。");
        c.setSource("国家卫健委《婴幼儿喂养指南》");
        return c;
    }
}
