package com.babylog.system.service;

import com.babylog.common.core.page.PageResult;
import com.babylog.system.domain.dto.OperLogQuery;
import com.babylog.system.domain.entity.SysOperLog;
import com.babylog.system.domain.vo.OperLogVO;

/** 操作日志服务 */
public interface OperLogService {

    /** 保存操作日志（OperLogAspect 切面调用） */
    void saveLog(SysOperLog entity);

    /** 分页查询 */
    PageResult<OperLogVO> pageLogs(OperLogQuery query);
}
