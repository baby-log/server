package com.babylog.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.babylog.common.core.page.PageResult;
import com.babylog.system.convert.LoginLogConvert;
import com.babylog.system.domain.dto.LoginLogQuery;
import com.babylog.system.domain.entity.SysLoginLog;
import com.babylog.system.domain.vo.LoginLogVO;
import com.babylog.system.mapper.SysLoginLogMapper;
import com.babylog.system.service.LoginLogService;
import com.babylog.system.support.PageUtils;
import com.babylog.system.support.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** 登录日志实现 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogServiceImpl extends ServiceImpl<SysLoginLogMapper, SysLoginLog>
        implements LoginLogService {

    /** UA 截断上限（与列宽一致） */
    private static final int UA_MAX_LENGTH = 512;

    private final LoginLogConvert loginLogConvert;

    @Override
    public void record(String username, boolean success, String msg) {
        try {
            SysLoginLog entity = new SysLoginLog();
            entity.setUsername(username);
            HttpServletRequest request = ServletUtils.getRequest();
            entity.setIp(ServletUtils.getClientIp(request));
            entity.setUserAgent(ServletUtils.getUserAgent(request, UA_MAX_LENGTH));
            entity.setStatus(success ? 0 : 1);
            entity.setMsg(msg);
            entity.setLoginTime(LocalDateTime.now());
            save(entity);
        } catch (Exception ex) {
            log.warn("[login-log] 登录日志记录失败：{}", ex.getMessage());
        }
    }

    @Override
    public PageResult<LoginLogVO> pageLogs(LoginLogQuery query) {
        Page<SysLoginLog> page = page(PageUtils.toMpPage(query), Wrappers.<SysLoginLog>lambdaQuery()
                .like(StringUtils.hasText(query.getUsername()), SysLoginLog::getUsername, query.getUsername())
                .like(StringUtils.hasText(query.getIp()), SysLoginLog::getIp, query.getIp())
                .eq(query.getStatus() != null, SysLoginLog::getStatus, query.getStatus())
                .ge(query.getBeginTime() != null, SysLoginLog::getLoginTime, query.getBeginTime())
                .le(query.getEndTime() != null, SysLoginLog::getLoginTime, query.getEndTime())
                .orderByDesc(SysLoginLog::getLoginTime));
        return PageUtils.toPageResult(page, loginLogConvert.toVoList(page.getRecords()));
    }
}
