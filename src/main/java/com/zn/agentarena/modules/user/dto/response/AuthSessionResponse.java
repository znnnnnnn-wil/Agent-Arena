package com.zn.agentarena.modules.user.dto.response;

import lombok.Builder;
import lombok.Data;

/** Browser session returned after visitor creation, registration, or login. */
@Data
@Builder
public class AuthSessionResponse {
    private Long userId;
    /** 用户昵称 */
    private String nickname;
    /** 账号用户名 */
    private String username;
    /** visitorToken 字段 */
    private String visitorToken;
    /** 是否已注册账号 */
    private Boolean registered;
}
