package com.babylog.migration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.migration.domain.entity.MigrationStep;
import org.apache.ibatis.annotations.Mapper;

/**
 * 迁移步骤 Mapper。
 */
@Mapper
public interface MigrationStepMapper extends BaseMapper<MigrationStep> {
}
