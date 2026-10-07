package com.zn.agentarena.modules.game.service;

import com.zn.agentarena.modules.game.dto.request.CreateWerewolfGameRequest;
import com.zn.agentarena.modules.game.dto.request.RematchGameRequest;
import com.zn.agentarena.modules.game.dto.response.GameResponse;
import com.zn.agentarena.modules.game.dto.response.GamePlayerResponse;
import com.zn.agentarena.modules.replay.dto.response.GameSummaryResponse;
import com.zn.agentarena.modules.replay.dto.response.ReplayEventResponse;
import com.zn.agentarena.modules.battle.dto.request.CreateBattleGameRequest;
import com.zn.agentarena.modules.turing.dto.request.CreateTuringHuntGameRequest;
import com.zn.agentarena.modules.turing.dto.request.TuringHuntHumanActionRequest;

import java.util.List;

/**
 * 游戏服务接口。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public interface IGameService {

    List<GameResponse> listGames(Long userId);

    List<GameResponse> listGames(Long userId, int page, int size);

    /** 创建一局狼人杀游戏。 */
    GameResponse createWerewolfGame(Long userId, CreateWerewolfGameRequest request);

    /** 创建一局大逃杀游戏。 */
    GameResponse createBattleGame(Long userId, CreateBattleGameRequest request);

    /** 创建固定一名真人与五名 AI 的图灵潜伏局。 */
    GameResponse createTuringHuntGame(Long userId, CreateTuringHuntGameRequest request);

    /** 启动已创建的狼人杀游戏。 */
    GameResponse startGame(Long userId, Long gameId);

    /** 启动已创建的大逃杀游戏。 */
    GameResponse startBattleGame(Long userId, Long gameId);

    GameResponse startTuringHuntGame(Long userId, Long gameId);

    /** 推进狼人杀游戏的一次 AI 行动。 */
    GameResponse runNextAiTurn(Long userId, Long gameId);

    /** 推进大逃杀游戏的一次 AI 回合。 */
    GameResponse runNextBattleTurn(Long userId, Long gameId);

    /** 推进一个 AI 行动或一个确定性阶段转换；需要真人时原地暂停。 */
    GameResponse runNextTuringTurn(Long userId, Long gameId);

    /** 提交当前创建者所控制真人席位的动作。 */
    GameResponse submitTuringHumanAction(Long userId, Long gameId, TuringHuntHumanActionRequest request);

    /** 复制规则、模型、人格和席位名称，创建一场使用新随机种子的重赛。 */
    GameResponse rematch(Long userId, Long gameId, RematchGameRequest request);

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
