package com.babylog.biz.devicebind.service;

import com.babylog.biz.devicebind.dto.DeviceBindApplyCmd;
import com.babylog.biz.devicebind.dto.DeviceBindPollCmd;
import com.babylog.biz.devicebind.dto.DeviceBindUserCodeCmd;
import com.babylog.biz.devicebind.vo.DeviceBindApplyVO;
import com.babylog.biz.devicebind.vo.DeviceBindPollVO;
import com.babylog.biz.devicebind.vo.DeviceBindStatusVO;

public interface DeviceBindService {

    DeviceBindApplyVO apply(DeviceBindApplyCmd cmd, String clientIp);

    DeviceBindPollVO poll(DeviceBindPollCmd cmd);

    DeviceBindStatusVO confirm(long userId, DeviceBindUserCodeCmd cmd, String clientIp);

    DeviceBindStatusVO deny(long userId, DeviceBindUserCodeCmd cmd, String clientIp);
}
