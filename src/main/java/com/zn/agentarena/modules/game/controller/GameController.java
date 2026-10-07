package com.zn.agentarena.modules.game.controller;

import com.zn.agentarena.common.Response;
import com.zn.agentarena.common.utils.FixedWindowRateLimiter;
import com.zn.agentarena.modules.game.dto.request.CreateWerewolfGameRequest;
import com.zn.agentarena.modules.game.dto.request.RematchGameRequest;
import com.zn.agentarena.modules.game.dto.response.GameResponse;
import com.zn.agentarena.modules.game.dto.response.GamePlayerResponse;
import com.zn.agentarena.modules.game.service.IGameService;
import com.zn.agentarena.modules.replay.dto.response.GameSummaryResponse;
import com.zn.agentarena.modules.replay.dto.response.ReplayEventResponse;
import com.zn.agentarena.modules.battle.dto.request.CreateBattleGameRequest;
import com.zn.agentarena.modules.turing.dto.request.CreateTuringHuntGameRequest;
import com.zn.agentarena.modules.turing.dto.request.TuringHuntHumanActionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 游戏控制器。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/games")
public class GameController {

    private final IGameService gameService;
    private final FixedWindowRateLimiter rateLimiter;
    @GetMapping
    /**
     * 查询当前用户拥有的游戏。
     *
     * @param userId 当前认证用户的标识
     * @return 游戏列表
     */
    public Response<List<GameResponse>> list(@AuthenticationPrincipal Long userId,
                                             @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Response.success(gameService.listGames(userId, page, size));
    }

    /** 创建一局标准狼人杀。 */
    @PostMapping("/werewolf")
    public Response<GameResponse> createWerewolf(@AuthenticationPrincipal Long userId,
                                                 @Valid @RequestBody CreateWerewolfGameRequest request) {
        rateLimiter.check(userId, "game-create", 10);
        return Response.success(gameService.createWerewolfGame(userId, request));
    }

    /** 创建一局简化大逃杀。 */
    @PostMapping("/battle-royale")
    public Response<GameResponse> createBattle(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody CreateBattleGameRequest request) {
        rateLimiter.check(userId, "game-create", 10);
        return Response.success(gameService.createBattleGame(userId, request));
    }

    /** 创建一场图灵潜伏局。 */
    @PostMapping("/turing-hunt")
    public Response<GameResponse> createTuringHunt(@AuthenticationPrincipal Long userId,
                                                    @Valid @RequestBody CreateTuringHuntGameRequest request) {
        rateLimiter.check(userId, "game-create", 10);
        return Response.success(gameService.createTuringHuntGame(userId, request));
    }

    /** 启动大逃杀。 */
    @PostMapping("/{gameId}/battle-start")
    public Response<GameResponse> startBattle(@AuthenticationPrincipal Long userId,
                                              @PathVariable @Positive Long gameId) {
        return Response.success(gameService.startBattleGame(userId, gameId));
    }

    /** 推进大逃杀游戏的一次 AI 回合。 */
    @PostMapping("/{gameId}/battle-next-turn")
    public Response<GameResponse> battleNextTurn(@AuthenticationPrincipal Long userId,
                                                 @PathVariable @Positive Long gameId) {
        return Response.success(gameService.runNextBattleTurn(userId, gameId));
    }

    /** 启动图灵潜伏局并发布第一轮公共问题。 */
    @PostMapping("/{gameId}/turing-start")
    public Response<GameResponse> startTuringHunt(@AuthenticationPrincipal Long userId,
                                                   @PathVariable @Positive Long gameId) {
        return Response.success(gameService.startTuringHuntGame(userId, gameId));
    }

    /** 推进一名 AI 或一次确定性阶段转换。 */
    @PostMapping("/{gameId}/turing-next-turn")
    public Response<GameResponse> turingNextTurn(@AuthenticationPrincipal Long userId,
                                                  @PathVariable @Positive Long gameId) {
        return Response.success(gameService.runNextTuringTurn(userId, gameId));
    }

    /** 提交图灵潜伏局当前要求的真人动作。 */
    @PostMapping("/{gameId}/turing-human-actions")
    public Response<GameResponse> turingHumanAction(@AuthenticationPrincipal Long userId,
                                                     @PathVariable @Positive Long gameId,
                                                     @Valid @RequestBody TuringHuntHumanActionRequest request) {
        return Response.success(gameService.submitTuringHumanAction(userId, gameId, request));
    }

    /** 使用相同模型、人格、规则和席位名称创建一场新比赛。 */
    @PostMapping("/{gameId}/rematch")
    public Response<GameResponse> rematch(@AuthenticationPrincipal Long userId,
                                          @PathVariable @Positive Long gameId,
                                          @Valid @RequestBody RematchGameRequest request) {
        rateLimiter.check(userId, "game-create", 10);
        return Response.success(gameService.rematch(userId, gameId, request));
    }

    /** 启动等待中的游戏。 */
    @PostMapping("/{gameId}/start")
    public Response<GameResponse> start(@AuthenticationPrincipal Long userId,
                                        @PathVariable @Positive Long gameId) {
        return Response.success(gameService.startGame(userId, gameId));
    }

    /** 推进一名AI玩家。 */
    @PostMapping("/{gameId}/next-turn")
    public Response<GameResponse> nextTurn(@AuthenticationPrincipal Long userId,
                                           @PathVariable @Positive Long gameId) {
        return Response.success(gameService.runNextAiTurn(userId, gameId));
    }

    /** 获取公开回放事件。 */
    @GetMapping("/{gameId}/replay")
    public Response<List<ReplayEventResponse>> replay(@AuthenticationPrincipal Long userId,
                                                      @PathVariable @Positive Long gameId,
                                                      @RequestParam(defaultValue = "0") @Min(0) long afterSequence,
                                                      @RequestParam(defaultValue = "500") @Min(1) @Max(500) int limit) {
        return Response.success(gameService.getReplay(userId, gameId, afterSequence, limit));
    }

    /** 获取对局总结。 */
    @GetMapping("/{gameId}/summary")
    public Response<GameSummaryResponse> summary(@AuthenticationPrincipal Long userId,
                                                 @PathVariable @Positive Long gameId) {
        return Response.success(gameService.getSummary(userId, gameId));
    }

    /** 获取游戏状态。 */
    @GetMapping("/{gameId}")
    public Response<GameResponse> get(@AuthenticationPrincipal Long userId,
                                      @PathVariable @Positive Long gameId) {
        return Response.success(gameService.getGame(userId, gameId));
    }

    /** 查询游戏中的玩家列表。 */
    @GetMapping("/{gameId}/players")
    public Response<List<GamePlayerResponse>> players(@AuthenticationPrincipal Long userId,
                                                      @PathVariable @Positive Long gameId) {
        return Response.success(gameService.getGamePlayers(userId, gameId));
    }
}
