package com.babylog.biz.feedback.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.entity.BlFeedback;
import com.babylog.biz.feedback.dto.FeedbackPageQuery;
import com.babylog.biz.feedback.dto.FeedbackReplyCmd;
import com.babylog.biz.feedback.dto.FeedbackSubmitCmd;
import com.babylog.biz.feedback.vo.FeedbackVO;
import com.babylog.biz.mapper.BlFeedbackMapper;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.page.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 反馈通道单测：匿名/实名提交、管理端分页与回复状态机 */
@ExtendWith(MockitoExtension.class)
class FeedbackServiceImplTest {

    @Mock
    private BlFeedbackMapper feedbackMapper;

    @InjectMocks
    private FeedbackServiceImpl service;

    private static FeedbackSubmitCmd submitCmd() {
        FeedbackSubmitCmd cmd = new FeedbackSubmitCmd();
        cmd.setContent("希望增加夜奶统计的周报功能");
        cmd.setContact("a@163.com");
        return cmd;
    }

    @Test
    void submit_anonymousKeepsUserIdNull() {
        service.submit(submitCmd(), null);

        ArgumentCaptor<BlFeedback> captor = ArgumentCaptor.forClass(BlFeedback.class);
        verify(feedbackMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getContent()).isEqualTo("希望增加夜奶统计的周报功能");
    }

    @Test
    void submit_loggedInRecordsUserId() {
        service.submit(submitCmd(), 42L);

        ArgumentCaptor<BlFeedback> captor = ArgumentCaptor.forClass(BlFeedback.class);
        verify(feedbackMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(42L);
    }

    @Test
    void page_mapsRowsToVo() {
        BlFeedback row = new BlFeedback();
        row.setId(1L);
        row.setUserId(42L);
        row.setContent("内容");
        row.setStatus("PENDING");
        Page<BlFeedback> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(feedbackMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<FeedbackVO> result = service.page(new FeedbackPageQuery());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList().get(0).getUserId()).isEqualTo(42L);
        assertThat(result.getList().get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void reply_movesPendingToRepliedWithTimestamp() {
        BlFeedback row = new BlFeedback();
        row.setId(1L);
        row.setStatus("PENDING");
        when(feedbackMapper.selectById(1L)).thenReturn(row);

        FeedbackReplyCmd cmd = new FeedbackReplyCmd();
        cmd.setReply("收到，排进下个版本");
        service.reply(1L, cmd);

        ArgumentCaptor<BlFeedback> captor = ArgumentCaptor.forClass(BlFeedback.class);
        verify(feedbackMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("REPLIED");
        assertThat(captor.getValue().getReply()).isEqualTo("收到，排进下个版本");
        assertThat(captor.getValue().getReplyTime()).isNotNull();
    }

    @Test
    void reply_rejectsMissingFeedback() {
        when(feedbackMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.reply(404L, new FeedbackReplyCmd()))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(BabyLogErrorCode.FEEDBACK_NOT_FOUND.getCode());
    }
}
