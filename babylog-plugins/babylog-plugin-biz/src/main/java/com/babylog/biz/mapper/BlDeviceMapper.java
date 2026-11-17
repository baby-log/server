package com.babylog.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.biz.entity.BlDevice;
import org.apache.ibatis.annotations.Mapper;

/**
 * bl_device 业务 Mapper：业务写路径（登录 touchDevice 等）专用。
 *
 * <p>带 @TableLogic 的业务语义（默认只看有效数据）；需要全量可见的查询
 * （workerId 号段聚合——软删设备仍占号）走 {@code com.babylog.biz.sync.mapper.BlDeviceViewMapper}。
 */
@Mapper
public interface BlDeviceMapper extends BaseMapper<BlDevice> {
}
