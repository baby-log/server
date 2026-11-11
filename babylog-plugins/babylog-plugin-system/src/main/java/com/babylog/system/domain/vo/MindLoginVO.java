package com.babylog.system.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** BabyLog·智域登录响应 */
@Data
@AllArgsConstructor
public class MindLoginVO {

    /** 访问令牌 */
    private String token;

    /** 是否新用户 */
    private Boolean fresh;
}
