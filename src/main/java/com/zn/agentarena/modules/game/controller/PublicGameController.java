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
public class PublicGameController {

    private final IGameService gameService;

    @GetMapping
    public Response<List<GameResponse>> list(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return Response.success(gameService.listPublicGames(limit));
    }

    @GetMapping("/{gameId}")
    public Response<GameResponse> get(@PathVariable @Positive Long gameId) {
        return Response.success(gameService.getPublicGame(gameId));
    }

    @GetMapping("/{gameId}/players")
    public Response<List<GamePlayerResponse>> players(@PathVariable @Positive Long gameId) {
        return Response.success(gameService.getPublicGamePlayers(gameId));
    }

    @GetMapping("/{gameId}/replay")
    public Response<List<ReplayEventResponse>> replay(@PathVariable @Positive Long gameId,
                                                      @RequestParam(defaultValue = "0") @Min(0) long afterSequence,
                                                      @RequestParam(defaultValue = "500") @Min(1) @Max(500) int limit) {
        return Response.success(gameService.getPublicReplay(gameId, afterSequence, limit));
    }

    @GetMapping("/{gameId}/summary")
    public Response<GameSummaryResponse> summary(@PathVariable @Positive Long gameId) {
        return Response.success(gameService.getPublicSummary(gameId));
    }
}
