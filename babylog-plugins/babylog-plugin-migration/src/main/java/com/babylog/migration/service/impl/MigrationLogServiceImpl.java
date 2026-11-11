package com.babylog.migration.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.babylog.migration.domain.entity.MigrationLog;
import com.babylog.migration.mapper.MigrationLogMapper;
import com.babylog.migration.service.MigrationLogService;
import org.springframework.stereotype.Service;

/**
 * 迁移执行日志 Service 实现。
 */
@Service
public class MigrationLogServiceImpl extends ServiceImpl<MigrationLogMapper, MigrationLog>
        implements MigrationLogService {
}
