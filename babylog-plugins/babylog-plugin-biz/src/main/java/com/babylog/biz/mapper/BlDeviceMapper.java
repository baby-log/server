package com.babylog.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.biz.entity.BlDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BlDeviceMapper extends BaseMapper<BlDevice> {

    /**
     * 该账号已用的最大 workerId（无设备时返回 -1，方便上游直接 +1）。
     *
     * <p>刻意<b>不加 {@code deleted = 0}</code>：软删除的设备仍占着 workerId，
     * 复用它的号会撞 {@code uk(user_id, worker_id)}，等于把一次「重新登录」变成 500。
     */
    @Select("SELECT COALESCE(MAX(worker_id), -1) FROM bl_device WHERE user_id = #{userId}")
    Integer selectMaxWorkerId(@Param("userId") Long userId);
}
