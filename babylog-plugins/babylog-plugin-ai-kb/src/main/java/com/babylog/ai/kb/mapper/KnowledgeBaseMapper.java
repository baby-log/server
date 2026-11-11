package com.babylog.ai.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.ai.kb.domain.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库 Mapper。
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {
}
