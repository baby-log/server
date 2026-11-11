package com.babylog.migration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.migration.domain.entity.MigrationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 迁移执行日志 Mapper。
 */
@Mapper
public interface MigrationLogMapper extends BaseMapper<MigrationLog> {
}
