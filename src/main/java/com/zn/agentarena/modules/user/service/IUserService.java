package com.zn.agentarena.modules.user.service;

import com.zn.agentarena.modules.user.dto.request.CreateVisitorUserRequest;
import com.zn.agentarena.modules.user.dto.request.LoginRequest;
import com.zn.agentarena.modules.user.dto.request.RegisterAccountRequest;
import com.zn.agentarena.modules.user.dto.response.AuthSessionResponse;
import com.zn.agentarena.modules.user.dto.response.CurrentUserResponse;

/**
 * 用户服务接口。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public interface IUserService {

    /** 创建游客用户并生成访问令牌。 */
    AuthSessionResponse createVisitorUser(CreateVisitorUserRequest request);

    /** Upgrade a visitor into a local account, preserving owned games and model configurations. */
    AuthSessionResponse registerAccount(Long currentUserId, RegisterAccountRequest request);

    /** Verify local credentials and rotate the browser access token. */
    AuthSessionResponse login(LoginRequest request);

    /** Revoke the current browser access token. */
    void logout(Long userId);

    /** Load the safe current-user profile. */
    CurrentUserResponse getCurrentUser(Long userId);

    /** 根据游客令牌解析用户标识。 */
    Long getUserIdByVisitorToken(String visitorToken);
}
