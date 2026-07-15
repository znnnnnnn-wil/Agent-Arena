package com.zn.agentarena.modules.game.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.zn.agentarena.common.enums.GameMode;
import com.zn.agentarena.common.enums.GameStatus;
import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.dao.entity.Game;
import com.zn.agentarena.dao.entity.GameEvent;
import com.zn.agentarena.dao.entity.GamePlayer;
import com.zn.agentarena.dao.mapper.GameMapper;
import com.zn.agentarena.dao.mapper.GamePlayerMapper;
import com.zn.agentarena.dao.mapper.UserMapper;
import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.game.dto.response.GamePlayerResponse;
import com.zn.agentarena.modules.game.dto.response.GameResponse;
import com.zn.agentarena.modules.replay.dto.response.GameSummaryResponse;
import com.zn.agentarena.modules.replay.dto.response.ReplayEventResponse;
import com.zn.agentarena.modules.spectator.service.GameEventRecorder;
import com.zn.agentarena.modules.spectator.service.PublicEventPolicy;
import com.zn.agentarena.modules.werewolf.domain.WerewolfGameState;
import com.zn.agentarena.modules.werewolf.domain.WerewolfPlayerState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-only game projections, including the privacy boundary for public spectators. */
@Service
@RequiredArgsConstructor
public class GameReadService {

    private static final Set<String> KEY_EVENTS = Set.of(
            "PLAYER_ELIMINATED", "VOTE_RESULT", "VOTE_TIED", "DAWN_RESULT", "GAME_FINISHED",
            "BATTLE_PLAYER_ELIMINATED", "BATTLE_ZONE_UPDATED", "BATTLE_SUPPLY_DROP", "BATTLE_FINISHED");

    private final GameMapper gameMapper;
    private final GamePlayerMapper gamePlayerMapper;
    private final UserMapper userMapper;
    private final GameSnapshotService snapshotService;
    private final GameEventRecorder eventRecorder;

    public List<GameResponse> listOwned(Long userId) {
        ensureUserExists(userId);
        return gameMapper.selectListByQuery(QueryWrapper.create()
                        .eq(Game::getOwnerUserId, userId)
                        .orderBy(Game::getUpdateTime, false)
                        .limit(20))
                .stream().map(this::toResponse).toList();
    }

    public GameResponse getOwned(Long userId, Long gameId) {
        return toResponse(getOwnedEntity(userId, gameId));
    }

    public List<GamePlayerResponse> getOwnedPlayers(Long userId, Long gameId) {
        return buildPlayerResponses(getOwnedEntity(userId, gameId), true);
    }

    public List<ReplayEventResponse> getOwnedReplay(Long userId, Long gameId, long afterSequence, int limit) {
        getOwnedEntity(userId, gameId);
        return mapReplay(eventRecorder.listEvents(gameId, afterSequence, limit), false);
    }

    public GameSummaryResponse getOwnedSummary(Long userId, Long gameId) {
        return buildSummary(getOwnedEntity(userId, gameId));
    }

    public List<GameResponse> listPublic(int requestedLimit) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        return gameMapper.selectListByQuery(QueryWrapper.create()
                        .eq(Game::getPublicGame, true)
                        .orderBy(Game::getUpdateTime, false).limit(limit))
                .stream().map(this::toResponse).toList();
    }

    public GameResponse getPublic(Long gameId) {
        return toResponse(getPublicEntity(gameId));
    }

    public List<GamePlayerResponse> getPublicPlayers(Long gameId) {
        return buildPlayerResponses(getPublicEntity(gameId), false);
    }

    public List<ReplayEventResponse> getPublicReplay(Long gameId, long afterSequence, int limit) {
        getPublicEntity(gameId);
        return mapReplay(eventRecorder.listEvents(gameId, afterSequence, limit), true);
    }

    public GameSummaryResponse getPublicSummary(Long gameId) {
        return buildSummary(getPublicEntity(gameId));
    }

    public void assertPublic(Long gameId) {
        getPublicEntity(gameId);
    }

    private List<GamePlayerResponse> buildPlayerResponses(Game game, boolean ownerView) {
        Map<Long, BattlePlayerState> battleStates = Map.of();
        Map<Long, WerewolfPlayerState> werewolfStates = Map.of();
        if (game.getMode() == GameMode.BATTLE_ROYALE) {
            BattleRoyaleGameState snapshot = snapshotService.loadLatestBattleSnapshot(game.getId());
            if (snapshot != null) {
                battleStates = snapshot.getPlayers().stream()
                        .collect(Collectors.toMap(BattlePlayerState::getPlayerId, state -> state));
            }
        } else if (game.getMode() == GameMode.WEREWOLF) {
            WerewolfGameState snapshot = snapshotService.loadLatestWerewolfSnapshot(game.getId());
            if (snapshot != null) {
                werewolfStates = snapshot.getPlayers().stream()
                        .collect(Collectors.toMap(WerewolfPlayerState::getPlayerId, state -> state));
            }
        }
        Map<Long, BattlePlayerState> finalBattleStates = battleStates;
        Map<Long, WerewolfPlayerState> finalWerewolfStates = werewolfStates;
        return getPlayers(game.getId()).stream().map(player -> {
            BattlePlayerState state = finalBattleStates.get(player.getId());
            WerewolfPlayerState wolfState = finalWerewolfStates.get(player.getId());
            boolean revealWerewolfRole = ownerView || game.getStatus() == GameStatus.FINISHED;
            boolean revealBattleLocation = ownerView || state == null || !state.isHidden();
            return GamePlayerResponse.builder()
                    .id(player.getId()).seatNo(player.getSeatNo()).playerType(player.getPlayerType())
                    .modelConfigId(ownerView ? player.getModelConfigId() : null).displayName(player.getDisplayName())
                    .status(player.getStatus()).roleCode(revealWerewolfRole ? player.getRoleCode() : null)
                    .antidoteAvailable(ownerView && wolfState != null ? wolfState.isAntidoteAvailable() : null)
                    .poisonAvailable(ownerView && wolfState != null ? wolfState.isPoisonAvailable() : null)
                    .seerChecks(ownerView && wolfState != null ? wolfState.getSeerChecks() : null)
                    .region(state == null || !revealBattleLocation ? null : state.getRegion())
                    .health(state == null ? null : state.getHealth())
                    .food(state == null ? null : state.getFood())
                    .water(state == null ? null : state.getWater())
                    .armed(state == null ? null : state.isArmed())
                    .weaponName(state == null ? null : state.getWeaponName())
                    .weaponLevel(state == null ? null : state.getWeaponLevel())
                    .ammo(state == null ? null : state.getAmmo())
                    .armor(state == null ? null : state.getArmor())
                    .medkits(state == null ? null : state.getMedkits())
                    .kills(state == null ? null : state.getKills())
                    .totalDamage(state == null ? null : state.getTotalDamage())
                    .hidden(state == null ? null : state.isHidden())
                    .build();
        }).toList();
    }

    private List<ReplayEventResponse> mapReplay(List<GameEvent> events, boolean publicView) {
        return events.stream().map(event -> ReplayEventResponse.builder()
                .sequenceNo(event.getSequenceNo()).eventType(event.getEventType())
                .actorPlayerId(publicView
                        ? PublicEventPolicy.publicActorPlayerId(event.getEventType(), event.getActorPlayerId())
                        : event.getActorPlayerId())
                .roundNo(event.getRoundNo()).phase(event.getPhase())
                .payload(publicView ? event.getPublicPayload() : event.getPayload())
                .createTime(event.getCreateTime()).build()).toList();
    }

    private GameSummaryResponse buildSummary(Game game) {
        List<GameEvent> events = eventRecorder.listRecentEvents(game.getId(), 5_000);
        List<String> keyEvents = events.stream().filter(event -> KEY_EVENTS.contains(event.getEventType()))
                .map(this::summarizeKeyEvent).toList();
        String winner = events.stream()
                .filter(event -> Set.of("GAME_FINISHED", "BATTLE_FINISHED").contains(event.getEventType()))
                .reduce((first, second) -> second)
                .map(event -> String.valueOf(event.getPublicPayload().getOrDefault("winner",
                        event.getPublicPayload().getOrDefault("winnerName",
                                event.getPublicPayload().get("winnerPlayerId")))))
                .orElse(null);
        int eliminations = (int) events.stream()
                .filter(event -> Set.of("PLAYER_ELIMINATED", "BATTLE_PLAYER_ELIMINATED")
                        .contains(event.getEventType())).count();
        return GameSummaryResponse.builder().gameId(game.getId()).status(game.getStatus().name()).winner(winner)
                .eventCount(events.size()).eliminationCount(eliminations).keyEvents(keyEvents).build();
    }

    private String summarizeKeyEvent(GameEvent event) {
        Map<String, Object> payload = event.getPublicPayload();
        return switch (event.getEventType()) {
            case "PLAYER_ELIMINATED", "BATTLE_PLAYER_ELIMINATED" -> "第" + event.getRoundNo() + "轮：玩家"
                    + payload.get("playerId") + "出局（" + payload.get("reason") + "）";
            case "VOTE_RESULT" -> "第" + event.getRoundNo() + "轮：投票结算 " + payload.get("counts");
            case "VOTE_TIED" -> "第" + event.getRoundNo() + "轮：投票平局";
            case "DAWN_RESULT" -> "第" + event.getRoundNo() + "轮："
                    + (Boolean.TRUE.equals(payload.get("peaceful")) ? "平安夜" : "天亮有人出局");
            case "BATTLE_ZONE_UPDATED" -> "第" + event.getRoundNo() + "轮：毒圈阶段" + payload.get("ringStage");
            case "BATTLE_SUPPLY_DROP" -> "第" + event.getRoundNo() + "轮：空投抵达" + payload.get("region") + "区";
            case "GAME_FINISHED", "BATTLE_FINISHED" -> "对局结束：" + payload;
            default -> event.getEventType() + "#" + event.getSequenceNo();
        };
    }

    private Game getOwnedEntity(Long userId, Long gameId) {
        if (userId == null || gameId == null) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "用户ID和游戏ID不能为空");
        }
        Game game = gameMapper.selectOneById(gameId);
        if (game == null || !userId.equals(game.getOwnerUserId())) {
            throw CustomException.create(ResultCodeEnum.GAME_NOT_FOUND);
        }
        return game;
    }

    private Game getPublicEntity(Long gameId) {
        if (gameId == null) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "游戏ID不能为空");
        }
        Game game = gameMapper.selectOneById(gameId);
        if (game == null || !Boolean.TRUE.equals(game.getPublicGame())) {
            throw CustomException.create(ResultCodeEnum.GAME_NOT_FOUND);
        }
        return game;
    }

    private List<GamePlayer> getPlayers(Long gameId) {
        return gamePlayerMapper.selectListByQuery(QueryWrapper.create()
                .eq(GamePlayer::getGameId, gameId).orderBy(GamePlayer::getSeatNo, true));
    }

    private void ensureUserExists(Long userId) {
        if (userId == null || userMapper.selectOneById(userId) == null) {
            throw CustomException.create(ResultCodeEnum.USER_NOT_FOUND);
        }
    }

    private GameResponse toResponse(Game game) {
        return GameResponse.builder().id(game.getId()).name(game.getName()).mode(game.getMode())
                .status(game.getStatus()).currentPhase(game.getCurrentPhase()).currentRound(game.getCurrentRound())
                .maxPlayers(game.getMaxPlayers()).publicGame(game.getPublicGame())
                .startTime(game.getStartTime()).finishTime(game.getFinishTime())
                .createTime(game.getCreateTime()).updateTime(game.getUpdateTime()).build();
    }
}
