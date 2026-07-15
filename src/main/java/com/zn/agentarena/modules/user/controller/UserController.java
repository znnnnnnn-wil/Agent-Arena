package com.zn.agentarena.modules.user.controller;

import com.zn.agentarena.common.Response;
import com.zn.agentarena.modules.user.dto.request.CreateVisitorUserRequest;
import com.zn.agentarena.modules.user.dto.request.LoginRequest;
import com.zn.agentarena.modules.user.dto.request.RegisterAccountRequest;
import com.zn.agentarena.modules.user.dto.response.AuthSessionResponse;
import com.zn.agentarena.modules.user.dto.response.CurrentUserResponse;
import com.zn.agentarena.modules.user.service.IUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.zn.agentarena.common.utils.FixedWindowRateLimiter;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户控制器。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final IUserService userService;
    private final FixedWindowRateLimiter rateLimiter;

    /** 创建游客用户。 */
    @PostMapping("/visitor")
    public Response<AuthSessionResponse> createVisitor(@Valid @RequestBody(required = false) CreateVisitorUserRequest request,
                                                       HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest.getRemoteAddr(), "visitor-create", 10);
        return Response.success(userService.createVisitorUser(request));
    }

    /** Upgrade the current visitor identity into a password-protected local account. */
    @PostMapping("/register")
    public Response<AuthSessionResponse> register(@AuthenticationPrincipal Long userId,
                                                  @Valid @RequestBody RegisterAccountRequest request,
                                                  HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest.getRemoteAddr(), "account-register", 5);
        return Response.success(userService.registerAccount(userId, request));
    }

    /** Sign in and issue a new opaque browser token. */
    @PostMapping("/login")
    public Response<AuthSessionResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest.getRemoteAddr(), "account-login", 12);
        return Response.success(userService.login(request));
    }

    /** Revoke the current token. */
    @PostMapping("/logout")
    public Response<Void> logout(@AuthenticationPrincipal Long userId) {
        userService.logout(userId);
        return Response.success();
    }

    /** Return the safe profile for session restoration. */
    @GetMapping("/me")
    public Response<CurrentUserResponse> currentUser(@AuthenticationPrincipal Long userId) {
        return Response.success(userService.getCurrentUser(userId));
    }

    /** 校验游客令牌并返回当前用户ID。 */
    @GetMapping("/visitor/verify")
    public Response<CurrentUserResponse> verifyVisitor(@AuthenticationPrincipal Long userId) {
        return Response.success(userService.getCurrentUser(userId));
    }
}
