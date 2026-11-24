package com.babylog.biz.notice.mapper;

import com.babylog.biz.notice.entity.BlNotice;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内通知 Mapper（服务端专用表，不进同步通道）。
 * SQL 全走类型化 Wrapper（本接口不承载任何 SQL 片段，见 P0 ArchUnit A12）。
 */
@Mapper
public interface BlNoticeMapper extends BaseMapper<BlNotice> {
}
