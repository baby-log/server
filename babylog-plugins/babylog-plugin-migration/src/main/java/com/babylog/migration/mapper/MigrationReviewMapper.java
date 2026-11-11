package com.babylog.migration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.migration.domain.entity.MigrationReview;
import org.apache.ibatis.annotations.Mapper;

/**
 * 迁移人工评审记录 Mapper。
 */
@Mapper
public interface MigrationReviewMapper extends BaseMapper<MigrationReview> {
}
