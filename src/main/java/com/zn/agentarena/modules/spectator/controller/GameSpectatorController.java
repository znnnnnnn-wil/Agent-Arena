package com.zn.agentarena.modules.spectator.controller;

import com.zn.agentarena.modules.spectator.service.GameEventStreamService;
import com.zn.agentarena.modules.game.service.IGameService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 游戏观战控制器。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/games")
public class GameSpectatorController {

    private final GameEventStreamService eventStreamService;
    private final IGameService gameService;

    /**
     * 订阅游戏实时事件。
     *
     * @param gameId 游戏ID
     * @return SSE事件流
     */
    @GetMapping(value = "/{gameId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable @Positive(message = "游戏ID必须为正数") Long gameId) {
        gameService.assertPublicGame(gameId);
        return eventStreamService.subscribe(gameId);
    }
}
