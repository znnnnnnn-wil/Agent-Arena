package com.zn.agentarena.modules.agent.controller;

import com.zn.agentarena.common.Response;
import com.zn.agentarena.common.utils.FixedWindowRateLimiter;
import com.zn.agentarena.modules.agent.dto.request.UpsertAgentProfileRequest;
import com.zn.agentarena.modules.agent.dto.response.AgentProfileResponse;
import com.zn.agentarena.modules.agent.service.AgentProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/agent-profiles")
/**
 * 提供当前用户 Agent 人格档案的查询与维护接口。
 */
public class AgentProfileController {
    private final AgentProfileService profileService;
    private final FixedWindowRateLimiter rateLimiter;

    @GetMapping
    /**
     * 查询当前用户可用的人格档案。
     *
     * @param userId 当前认证用户的标识
     * @return 人格档案列表
     */
    public Response<List<AgentProfileResponse>> list(@AuthenticationPrincipal Long userId) {
        return Response.success(profileService.list(userId));
    }

    @PostMapping
    /**
     * 创建一个自定义 Agent 人格档案。
     *
     * @param userId 当前认证用户的标识
     * @param request 人格档案内容
     * @return 创建后的人格档案
     */
    public Response<AgentProfileResponse> create(@AuthenticationPrincipal Long userId,
                                                 @Valid @RequestBody UpsertAgentProfileRequest request) {
        rateLimiter.check(userId, "agent-profile-create", 20);
        return Response.success(profileService.create(userId, request));
    }

    @PutMapping("/{profileId}")
    /**
     * 更新当前用户的人格档案。
     *
     * @param userId 当前认证用户的标识
     * @param profileId 待更新的人格档案标识
     * @param request 新的人格档案内容
     * @return 更新后的人格档案
     */
    public Response<AgentProfileResponse> update(@AuthenticationPrincipal Long userId,
                                                 @PathVariable @Positive Long profileId,
                                                 @Valid @RequestBody UpsertAgentProfileRequest request) {
        return Response.success(profileService.update(userId, profileId, request));
    }

    @DeleteMapping("/{profileId}")
    /**
     * 删除当前用户的人格档案。
     *
     * @param userId 当前认证用户的标识
     * @param profileId 待删除的人格档案标识
     * @return 空的成功响应
     */
    public Response<Void> delete(@AuthenticationPrincipal Long userId,
                                 @PathVariable @Positive Long profileId) {
        profileService.delete(userId, profileId);
        return Response.success();
    }
}
