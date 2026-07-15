package com.zn.agentarena.modules.user.dto.response;

import lombok.Builder;
import lombok.Data;

/** Safe account profile; never exposes the password hash or bearer token. */
@Data
@Builder
public class CurrentUserResponse {
    private Long userId;
    private String nickname;
    private String username;
    private Boolean registered;
}
