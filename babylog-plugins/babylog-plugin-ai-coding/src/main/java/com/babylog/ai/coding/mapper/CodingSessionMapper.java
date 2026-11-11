package com.babylog.ai.coding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.ai.coding.domain.entity.CodingSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI Coding session mapper.
 *
 * @author BabyLog
 * @since 2.2.0
 */
@Mapper
public interface CodingSessionMapper extends BaseMapper<CodingSession> {
}
