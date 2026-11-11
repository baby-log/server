package com.babylog.ai.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.ai.kb.domain.entity.KbEvalRecordItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库检索评测跑分逐题明细 Mapper（S67）。
 */
@Mapper
public interface KbEvalRecordItemMapper extends BaseMapper<KbEvalRecordItem> {
}
