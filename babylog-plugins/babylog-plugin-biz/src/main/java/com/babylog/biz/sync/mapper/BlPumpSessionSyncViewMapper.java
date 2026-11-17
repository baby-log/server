package com.babylog.biz.sync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.biz.sync.entity.BlPumpSessionSyncView;
import org.apache.ibatis.annotations.Mapper;

/**
 * bl_pump_sessions 同步通道视图实体的 Mapper（PivotOS 严格整改 S3）。
 *
 * <p>与业务 Mapper 并列、互不替代：业务写路径（家庭服务/喂养计时等）继续用业务实体
 * （带 @TableLogic，默认只看有效数据）；同步/base 通道一律走本视图 Mapper
 * （无逻辑删除，deleted 全量可见）。SQL 全部由 MyBatis-Plus 从类型化条件生成，
 * 本接口零手写 SQL。
 */
@Mapper
public interface BlPumpSessionSyncViewMapper extends BaseMapper<BlPumpSessionSyncView> {
}
