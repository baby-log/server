package com.babylog.biz.feedback.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.entity.BlFeedback;
import com.babylog.biz.feedback.dto.FeedbackPageQuery;
import com.babylog.biz.feedback.dto.FeedbackReplyCmd;
import com.babylog.biz.feedback.dto.FeedbackSubmitCmd;
import com.babylog.biz.feedback.service.FeedbackService;
import com.babylog.biz.feedback.vo.FeedbackVO;
import com.babylog.biz.mapper.BlFeedbackMapper;
import com.babylog.biz.support.PageUtils;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.page.PageResult;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** 意见反馈实现 */
@Service
public class FeedbackServiceImpl implements FeedbackService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_REPLIED = "REPLIED";

    private final BlFeedbackMapper feedbackMapper;

    public FeedbackServiceImpl(BlFeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    @Override
    public Long submit(FeedbackSubmitCmd cmd, Long userId) {
        BlFeedback row = new BlFeedback();
        row.setUserId(userId);
        row.setContact(cmd.getContact());
        row.setContent(cmd.getContent());
        row.setStatus(STATUS_PENDING);
        feedbackMapper.insert(row);
        return row.getId();
    }

    @Override
    public PageResult<FeedbackVO> page(FeedbackPageQuery query) {
        Page<BlFeedback> page = feedbackMapper.selectPage(PageUtils.toMpPage(query),
                Wrappers.<BlFeedback>lambdaQuery()
                        .eq(StringUtils.hasText(query.getStatus()), BlFeedback::getStatus, query.getStatus())
                        .orderByDesc(BlFeedback::getId));
        return PageUtils.toPageResult(page, page.getRecords().stream().map(FeedbackServiceImpl::toVo).toList());
    }

    @Override
    public void reply(long id, FeedbackReplyCmd cmd) {
        BlFeedback row = feedbackMapper.selectById(id);
        if (row == null) {
            throw new ServiceException(BabyLogErrorCode.FEEDBACK_NOT_FOUND);
        }
        row.setReply(cmd.getReply());
        row.setReplyTime(LocalDateTime.now());
        row.setStatus(STATUS_REPLIED);
        feedbackMapper.updateById(row);
    }

    private static FeedbackVO toVo(BlFeedback row) {
        FeedbackVO vo = new FeedbackVO();
        vo.setId(row.getId());
        vo.setUserId(row.getUserId());
        vo.setContact(row.getContact());
        vo.setContent(row.getContent());
        vo.setStatus(row.getStatus());
        vo.setReply(row.getReply());
        vo.setReplyTime(row.getReplyTime());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }
}
