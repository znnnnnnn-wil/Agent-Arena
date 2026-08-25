package com.zn.agentarena.modules.game.controller;

import com.zn.agentarena.common.Response;
import com.zn.agentarena.modules.game.dto.response.GamePlayerResponse;
import com.zn.agentarena.modules.game.dto.response.GameResponse;
import com.zn.agentarena.modules.game.service.IGameService;
import com.zn.agentarena.modules.replay.dto.response.GameSummaryResponse;
import com.zn.agentarena.modules.replay.dto.response.ReplayEventResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Anonymous, deliberately redacted views for games marked public. */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/public/games")
/**
 * 提供无需认证的公开游戏、回放与摘要查询接口。
 */
public class PublicGameController {

    private final IGameService gameService;

    @GetMapping
    /**
     * 分页查询公开游戏。
     *
     * @param limit 最大返回数量
     * @return 公开游戏列表
     */
    public Response<List<GameResponse>> list(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return Response.success(gameService.listPublicGames(limit));
    }

    @GetMapping("/{gameId}")
    /**
     * 查询单个公开游戏的状态。
     *
     * @param gameId 游戏标识
     * @return 游戏详情
     */
    public Response<GameResponse> get(@PathVariable @Positive Long gameId) {
        return Response.success(gameService.getPublicGame(gameId));
    }

    @GetMapping("/{gameId}/players")
    /**
     * 查询公开游戏中的玩家。
     *
     * @param gameId 游戏标识
     * @return 玩家列表
     */
    public Response<List<GamePlayerResponse>> players(@PathVariable @Positive Long gameId) {
        return Response.success(gameService.getPublicGamePlayers(gameId));
    }

    @GetMapping("/{gameId}/replay")
    /**
     * 查询公开游戏的回放事件。
     *
     * @param gameId 游戏标识
     * @param afterSequence 排除该序号及之前的事件
     * @param limit 最大返回数量
     * @return 回放事件列表
     */
    public Response<List<ReplayEventResponse>> replay(@PathVariable @Positive Long gameId,
                                                      @RequestParam(defaultValue = "0") @Min(0) long afterSequence,
                                                      @RequestParam(defaultValue = "500") @Min(1) @Max(500) int limit) {
        return Response.success(gameService.getPublicReplay(gameId, afterSequence, limit));
    }

    @GetMapping("/{gameId}/summary")
    /**
     * 查询公开游戏的结算摘要。
     *
     * @param gameId 游戏标识
     * @return 游戏摘要
     */
    public Response<GameSummaryResponse> summary(@PathVariable @Positive Long gameId) {
        return Response.success(gameService.getPublicSummary(gameId));
    }
}
