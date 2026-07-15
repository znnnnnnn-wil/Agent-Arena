package com.zn.agentarena.modules.user.dto.response;

import lombok.Builder;
import lombok.Data;

/** Browser session returned after visitor creation, registration, or login. */
@Data
@Builder
public class AuthSessionResponse {
    private Long userId;
    private String nickname;
    private String username;
    private String visitorToken;
    private Boolean registered;
}
