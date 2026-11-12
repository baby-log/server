package com.babylog.biz.feedback.service;

import com.babylog.biz.feedback.dto.FeedbackPageQuery;
import com.babylog.biz.feedback.dto.FeedbackReplyCmd;
import com.babylog.biz.feedback.dto.FeedbackSubmitCmd;
import com.babylog.biz.feedback.vo.FeedbackVO;
import com.babylog.common.core.page.PageResult;

/**
 * 意见反馈（M10）：App 端匿名提交 + 管理端查看/回复。
 *
 * <p>userId 由 controller 从 LoginContext 取（未登录为 null），service 不碰登录上下文——
 * 匿名/实名是提交时的快照，落库后不再变。
 */
public interface FeedbackService {

    /**
     * 提交反馈（落 PENDING）。
     *
     * @param userId 提交用户 id，匿名提交传 null
     * @return 新反馈 id
     */
    Long submit(FeedbackSubmitCmd cmd, Long userId);

    /** 管理端分页（状态过滤，提交时间倒序） */
    PageResult<FeedbackVO> page(FeedbackPageQuery query);

    /**
     * 管理端回复（PENDING → REPLIED，盖回复时间）。
     *
     * @throws com.babylog.common.core.exception.ServiceException 8380 反馈不存在
     */
    void reply(long id, FeedbackReplyCmd cmd);
}
