package com.babylog.system.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.babylog.system.domain.dto.PostQuery;
import com.babylog.system.domain.dto.PostSaveRequest;
import com.babylog.system.domain.entity.SysPost;
import com.babylog.system.domain.vo.PostVO;

import java.util.List;

/** 岗位服务 */
public interface PostService extends IService<SysPost> {

    /** 岗位列表查询 */
    List<PostVO> listPosts(PostQuery query);

    /** 查询岗位详情 */
    PostVO getPost(Long postId);

    /** 新增岗位 */
    Long createPost(PostSaveRequest request);

    /** 修改岗位 */
    void updatePost(PostSaveRequest request);

    /** 删除岗位 */
    void deletePost(Long postId);
}
