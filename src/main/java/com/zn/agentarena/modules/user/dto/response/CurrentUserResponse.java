package com.zn.agentarena.modules.user.dto.response;

import lombok.Builder;
import lombok.Data;

/** Safe account profile; never exposes the password hash or bearer token. */
@Data
@Builder
public class CurrentUserResponse {
    private Long userId;
    /** 用户昵称 */
    private String nickname;
    /** 账号用户名 */
    private String username;
    /** 是否已注册账号 */
    private Boolean registered;
}
