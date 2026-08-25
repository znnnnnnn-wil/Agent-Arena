package com.zn.agentarena.modules.replay.controller;

import com.zn.agentarena.common.Response;
import com.zn.agentarena.modules.replay.dto.response.HighlightClipResponse;
import com.zn.agentarena.modules.replay.dto.response.MatchProjectionResponse;
import com.zn.agentarena.modules.replay.service.MatchProjectionService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 结构化赛事解说与精彩片段接口。 */
@RestController
@Validated
@RequiredArgsConstructor
public class MatchProjectionController {
    private final MatchProjectionService projectionService;

    @GetMapping("/api/games/{gameId}/projection")
    public Response<MatchProjectionResponse> owned(@AuthenticationPrincipal Long userId,
                                                   @PathVariable @Positive Long gameId) {
        return Response.success(projectionService.getOwned(userId, gameId));
    }

    @GetMapping("/api/public/games/{gameId}/projection")
    /**
     * 查询公开游戏的投影回放数据。
     *
     * @param gameId 游戏标识
     * @return 比赛投影信息
     */
    public Response<MatchProjectionResponse> publicProjection(@PathVariable @Positive Long gameId) {
        return Response.success(projectionService.getPublic(gameId));
    }

    @GetMapping("/api/games/{gameId}/highlights/{clipId}")
    /**
     * 查询当前用户拥有的游戏中的指定高光片段。
     *
     * @param userId 当前认证用户的标识
     * @param gameId 游戏标识
     * @param clipId 高光片段标识
     * @return 高光片段
     */
    public Response<HighlightClipResponse> ownedClip(@AuthenticationPrincipal Long userId,
                                                     @PathVariable @Positive Long gameId,
                                                     @PathVariable @Pattern(regexp = "[a-z0-9-]{8,96}") String clipId) {
        return Response.success(projectionService.getOwnedClip(userId, gameId, clipId));
    }

    @GetMapping("/api/public/games/{gameId}/highlights/{clipId}")
    /**
     * 查询公开游戏中的指定高光片段。
     *
     * @param gameId 游戏标识
     * @param clipId 高光片段标识
     * @return 高光片段
     */
    public Response<HighlightClipResponse> publicClip(@PathVariable @Positive Long gameId,
                                                      @PathVariable @Pattern(regexp = "[a-z0-9-]{8,96}") String clipId) {
        return Response.success(projectionService.getPublicClip(gameId, clipId));
    }
}
