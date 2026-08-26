package com.zn.agentarena.modules.lineup.controller;

import com.zn.agentarena.common.Response;
import com.zn.agentarena.common.utils.FixedWindowRateLimiter;
import com.zn.agentarena.modules.game.dto.response.GameResponse;
import com.zn.agentarena.modules.lineup.dto.request.CreateGameFromTemplateRequest;
import com.zn.agentarena.modules.lineup.dto.request.CreateLineupTemplateRequest;
import com.zn.agentarena.modules.lineup.dto.response.LineupTemplateResponse;
import com.zn.agentarena.modules.lineup.service.LineupTemplateService;
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
@RequestMapping("/api/lineup-templates")
/**
 * 提供当前用户阵容模板的维护及由模板创建游戏的接口。
 */
public class LineupTemplateController {
    private final LineupTemplateService templateService;
    private final FixedWindowRateLimiter rateLimiter;

    @GetMapping
    /**
     * 查询当前用户的阵容模板。
     *
     * @param userId 当前认证用户的标识
     * @return 阵容模板列表
     */
    public Response<List<LineupTemplateResponse>> list(@AuthenticationPrincipal Long userId) {
        return Response.success(templateService.list(userId));
    }

    @PostMapping("/from-game/{gameId}")
    /**
     * 从一场已有游戏保存阵容模板。
     *
     * @param userId 当前认证用户的标识
     * @param gameId 源游戏标识
     * @param request 模板创建参数
     * @return 已保存的阵容模板
     */
    public Response<LineupTemplateResponse> create(@AuthenticationPrincipal Long userId,
                                                   @PathVariable @Positive Long gameId,
                                                   @Valid @RequestBody CreateLineupTemplateRequest request) {
        rateLimiter.check(userId, "lineup-template-create", 20);
        return Response.success(templateService.createFromGame(userId, gameId, request));
    }

    @PostMapping("/{templateId}/games")
    /**
     * 使用阵容模板创建新的游戏。
     *
     * @param userId 当前认证用户的标识
     * @param templateId 阵容模板标识
     * @param request 游戏创建参数
     * @return 已创建的游戏
     */
    public Response<GameResponse> createGame(@AuthenticationPrincipal Long userId,
                                             @PathVariable @Positive Long templateId,
                                             @Valid @RequestBody CreateGameFromTemplateRequest request) {
        rateLimiter.check(userId, "game-create", 10);
        return Response.success(templateService.createGame(userId, templateId, request));
    }

    @DeleteMapping("/{templateId}")
    /**
     * 删除当前用户的阵容模板。
     *
     * @param userId 当前认证用户的标识
     * @param templateId 阵容模板标识
     * @return 空的成功响应
     */
    public Response<Void> delete(@AuthenticationPrincipal Long userId,
                                 @PathVariable @Positive Long templateId) {
        templateService.delete(userId, templateId);
        return Response.success();
    }
}
