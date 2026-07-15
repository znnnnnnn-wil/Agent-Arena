package com.zn.agentarena.modules.game.service;

import com.zn.agentarena.modules.game.dto.request.CreateWerewolfGameRequest;
import com.zn.agentarena.modules.game.dto.response.GameResponse;
import com.zn.agentarena.modules.game.dto.response.GamePlayerResponse;
import com.zn.agentarena.modules.replay.dto.response.GameSummaryResponse;
import com.zn.agentarena.modules.replay.dto.response.ReplayEventResponse;
import com.zn.agentarena.modules.battle.dto.request.CreateBattleGameRequest;

import java.util.List;

/**
 * 游戏服务接口。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public interface IGameService {

    List<GameResponse> listGames(Long userId);

    /** 创建一局狼人杀游戏。 */
    GameResponse createWerewolfGame(Long userId, CreateWerewolfGameRequest request);

    /** 创建一局大逃杀游戏。 */
    GameResponse createBattleGame(Long userId, CreateBattleGameRequest request);

    /** 启动已创建的狼人杀游戏。 */
    GameResponse startGame(Long userId, Long gameId);

    /** 启动已创建的大逃杀游戏。 */
    GameResponse startBattleGame(Long userId, Long gameId);

    /** 推进狼人杀游戏的一次 AI 行动。 */
    GameResponse runNextAiTurn(Long userId, Long gameId);

    /** 推进大逃杀游戏的一次 AI 回合。 */
    GameResponse runNextBattleTurn(Long userId, Long gameId);

    /** 查询用户拥有的游戏详情。 */
    GameResponse getGame(Long userId, Long gameId);

    List<GamePlayerResponse> getGamePlayers(Long userId, Long gameId);

    /** 查询游戏回放事件。 */
    List<ReplayEventResponse> getReplay(Long userId, Long gameId, long afterSequence, int limit);

    /** 查询游戏结算摘要。 */
    GameSummaryResponse getSummary(Long userId, Long gameId);

    List<GameResponse> listPublicGames(int limit);

    GameResponse getPublicGame(Long gameId);

    List<GamePlayerResponse> getPublicGamePlayers(Long gameId);

    List<ReplayEventResponse> getPublicReplay(Long gameId, long afterSequence, int limit);

    GameSummaryResponse getPublicSummary(Long gameId);

    void assertPublicGame(Long gameId);
}
