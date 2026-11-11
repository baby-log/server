package com.babylog.migration.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.babylog.migration.domain.entity.MigrationFile;
import com.babylog.migration.mapper.MigrationFileMapper;
import com.babylog.migration.service.MigrationFileService;
import org.springframework.stereotype.Service;

/**
 * 迁移源文件索引 Service 实现。
 */
@Service
public class MigrationFileServiceImpl extends ServiceImpl<MigrationFileMapper, MigrationFile>
        implements MigrationFileService {
}
