package com.zn.agentarena.modules.replay.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.dao.entity.Game;
import com.zn.agentarena.dao.entity.GamePlayer;
import com.zn.agentarena.dao.mapper.GameMapper;
import com.zn.agentarena.dao.mapper.GamePlayerMapper;
import com.zn.agentarena.modules.replay.dto.response.HighlightClipResponse;
import com.zn.agentarena.modules.replay.dto.response.MatchProjectionResponse;
import com.zn.agentarena.modules.spectator.service.GameEventRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 赛事解说与精彩片段的读取边界。 */
@Service
@RequiredArgsConstructor
public class MatchProjectionService {
    private final GameMapper gameMapper;
    private final GamePlayerMapper gamePlayerMapper;
    private final GameEventRecorder eventRecorder;
    private final MatchNarrativeProjector projector;

    /**
     * 构建用户拥有的游戏的回放投影。
     *
     * @param userId 用户标识
     * @param gameId 游戏标识
     * @return 比赛投影信息
     */
    public MatchProjectionResponse getOwned(Long userId, Long gameId) {
        Game game = requireGame(gameId);
        if (userId == null || !userId.equals(game.getOwnerUserId())) {
            throw CustomException.create(ResultCodeEnum.GAME_NOT_FOUND);
        }
        return project(game);
    }

    /**
     * 构建公开游戏的回放投影。
     *
     * @param gameId 游戏标识
     * @return 比赛投影信息
     */
    public MatchProjectionResponse getPublic(Long gameId) {
        Game game = requireGame(gameId);
        if (!Boolean.TRUE.equals(game.getPublicGame())) {
            throw CustomException.create(ResultCodeEnum.GAME_NOT_FOUND);
        }
        return project(game);
    }

    /**
     * 获取用户拥有的游戏中的指定高光片段。
     *
     * @param userId 用户标识
     * @param gameId 游戏标识
     * @param clipId 高光片段标识
     * @return 高光片段
     */
    public HighlightClipResponse getOwnedClip(Long userId, Long gameId, String clipId) {
        return findClip(getOwned(userId, gameId), clipId);
    }

    /**
     * 获取公开游戏中的指定高光片段。
     *
     * @param gameId 游戏标识
     * @param clipId 高光片段标识
     * @return 高光片段
     */
    public HighlightClipResponse getPublicClip(Long gameId, String clipId) {
        return findClip(getPublic(gameId), clipId);
    }

    private MatchProjectionResponse project(Game game) {
        List<GamePlayer> players = gamePlayerMapper.selectListByQuery(QueryWrapper.create()
                .eq(GamePlayer::getGameId, game.getId()).orderBy(GamePlayer::getSeatNo, true));
        return projector.project(game.getId(), game.getMode(), eventRecorder.listAllEvents(game.getId()), players);
    }

    private HighlightClipResponse findClip(MatchProjectionResponse projection, String clipId) {
        return projection.getHighlights().stream().filter(clip -> clip.getClipId().equals(clipId)).findFirst()
                .orElseThrow(() -> CustomException.create(ResultCodeEnum.RESOURCE_NOT_FOUND, "精彩片段不存在"));
    }

    private Game requireGame(Long gameId) {
        Game game = gameId == null ? null : gameMapper.selectOneById(gameId);
        if (game == null) throw CustomException.create(ResultCodeEnum.GAME_NOT_FOUND);
        return game;
    }
}
