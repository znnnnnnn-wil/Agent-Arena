package com.zn.agentarena.modules.game.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.zn.agentarena.common.enums.GameMode;
import com.zn.agentarena.common.enums.GameStatus;
import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.dao.entity.Game;
import com.zn.agentarena.dao.entity.GameEvent;
import com.zn.agentarena.dao.entity.GamePlayer;
import com.zn.agentarena.dao.entity.ModelConfig;
import com.zn.agentarena.dao.mapper.GameMapper;
import com.zn.agentarena.dao.mapper.GamePlayerMapper;
import com.zn.agentarena.dao.mapper.ModelConfigMapper;
import com.zn.agentarena.dao.mapper.UserMapper;
import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.agent.domain.AgentPersonaSnapshot;
import com.zn.agentarena.modules.agent.domain.AgentPersonaPresets;
import com.zn.agentarena.modules.game.dto.response.GamePlayerResponse;
import com.zn.agentarena.modules.game.dto.response.GameResponse;
import com.zn.agentarena.modules.replay.dto.response.GameSummaryResponse;
import com.zn.agentarena.modules.replay.dto.response.ReplayEventResponse;
import com.zn.agentarena.modules.spectator.service.GameEventRecorder;
import com.zn.agentarena.modules.spectator.service.PublicEventPolicy;
import com.zn.agentarena.modules.werewolf.domain.WerewolfGameState;
import com.zn.agentarena.modules.werewolf.domain.WerewolfPhase;
import com.zn.agentarena.modules.werewolf.domain.WerewolfPlayerState;
import com.zn.agentarena.modules.werewolf.domain.WerewolfRole;
import com.zn.agentarena.modules.turing.domain.TuringHuntActionType;
import com.zn.agentarena.modules.turing.domain.TuringHuntGameState;
import com.zn.agentarena.modules.turing.domain.TuringHuntPhase;
import com.zn.agentarena.modules.turing.domain.TuringHuntPlayerState;
import com.zn.agentarena.modules.turing.engine.TuringHuntGameEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-only game projections, including the privacy boundary for public spectators. */
@Service
@RequiredArgsConstructor
public class GameReadService {

    private static final Set<String> KEY_EVENTS = Set.of(
            "PLAYER_ELIMINATED", "VOTE_RESULT", "VOTE_TIED", "DAWN_RESULT", "GAME_FINISHED",
            "BATTLE_PLAYER_ELIMINATED", "BATTLE_ZONE_UPDATED", "BATTLE_SUPPLY_DROP", "BATTLE_FINISHED",
            "TURING_PLAYER_ELIMINATED", "TURING_VOTE_TIED", "TURING_ROUND_FINISHED",
            "TURING_GAME_FINISHED");

    private final TuringHuntGameEngine turingGameEngine = new TuringHuntGameEngine();

    private final GameMapper gameMapper;
    private final GamePlayerMapper gamePlayerMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final UserMapper userMapper;
    private final GameSnapshotService snapshotService;
    private final GameEventRecorder eventRecorder;

    /**
     * 查询指定用户拥有的游戏。
     *
     * @param userId 用户标识
     * @return 游戏列表
     */
    public List<GameResponse> listOwned(Long userId) {
        ensureUserExists(userId);
        return gameMapper.selectListByQuery(QueryWrapper.create()
                        .eq(Game::getOwnerUserId, userId)
                        .orderBy(Game::getUpdateTime, false)
                        .limit(20))
                .stream().map(game -> toResponse(game, true, false)).toList();
    }

    /**
     * 查询用户拥有的游戏详情。
     *
     * @param userId 用户标识
     * @param gameId 游戏标识
     * @return 游戏详情
     */
    public GameResponse getOwned(Long userId, Long gameId) {
        return toResponse(getOwnedEntity(userId, gameId), true, true);
    }

    /**
     * 查询用户拥有的游戏中的玩家。
     *
     * @param userId 用户标识
     * @param gameId 游戏标识
     * @return 玩家列表
     */
    public List<GamePlayerResponse> getOwnedPlayers(Long userId, Long gameId) {
        return buildPlayerResponses(getOwnedEntity(userId, gameId), true);
    }

    /**
     * 查询用户拥有的游戏的回放事件。
     *
     * @param userId 用户标识
     * @param gameId 游戏标识
     * @param afterSequence 排除该序号及之前的事件
     * @param limit 最大返回数量
     * @return 回放事件列表
     */
    public List<ReplayEventResponse> getOwnedReplay(Long userId, Long gameId, long afterSequence, int limit) {
        Game game = getOwnedEntity(userId, gameId);
        boolean turingView = game.getMode() == GameMode.TURING_HUNT;
        return mapReplay(eventRecorder.listEvents(gameId, afterSequence, limit), turingView, turingView);
    }

    /**
     * 查询用户拥有的游戏的结算摘要。
     *
     * @param userId 用户标识
     * @param gameId 游戏标识
     * @return 游戏摘要
     */
    public GameSummaryResponse getOwnedSummary(Long userId, Long gameId) {
        return buildSummary(getOwnedEntity(userId, gameId));
    }

    /**
     * 查询公开游戏，并将返回数量限制在系统允许的范围内。
     *
     * @param requestedLimit 请求的最大数量
     * @return 公开游戏列表
     */
    public List<GameResponse> listPublic(int requestedLimit) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        return gameMapper.selectListByQuery(QueryWrapper.create()
                        .eq(Game::getPublicGame, true)
                        .orderBy(Game::getUpdateTime, false).limit(limit))
                .stream().map(game -> toResponse(game, false, false)).toList();
    }

    /**
     * 查询公开游戏详情。
     *
     * @param gameId 游戏标识
     * @return 游戏详情
     */
    public GameResponse getPublic(Long gameId) {
        return toResponse(getPublicEntity(gameId), false, true);
    }

    /**
     * 查询公开游戏的玩家。
     *
     * @param gameId 游戏标识
     * @return 玩家列表
     */
    public List<GamePlayerResponse> getPublicPlayers(Long gameId) {
        return buildPlayerResponses(getPublicEntity(gameId), false);
    }

    /**
     * 查询公开游戏的回放事件。
     *
     * @param gameId 游戏标识
     * @param afterSequence 排除该序号及之前的事件
     * @param limit 最大返回数量
     * @return 回放事件列表
     */
    public List<ReplayEventResponse> getPublicReplay(Long gameId, long afterSequence, int limit) {
        Game game = getPublicEntity(gameId);
        return mapReplay(eventRecorder.listEvents(gameId, afterSequence, limit), true,
                game.getMode() == GameMode.TURING_HUNT);
    }

    /**
     * 查询公开游戏的结算摘要。
     *
     * @param gameId 游戏标识
     * @return 游戏摘要
     */
    public GameSummaryResponse getPublicSummary(Long gameId) {
        return buildSummary(getPublicEntity(gameId));
    }

    /**
     * 校验游戏可供未认证访问。
     *
     * @param gameId 游戏标识
     */
    public void assertPublic(Long gameId) {
        getPublicEntity(gameId);
    }

    private List<GamePlayerResponse> buildPlayerResponses(Game game, boolean ownerView) {
        Map<Long, BattlePlayerState> battleStates = Map.of();
        Map<Long, WerewolfPlayerState> werewolfStates = Map.of();
        Map<Long, TuringHuntPlayerState> turingStates = Map.of();
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
        } else if (game.getMode() == GameMode.TURING_HUNT) {
            TuringHuntGameState snapshot = snapshotService.loadLatestTuringSnapshot(game.getId());
            if (snapshot != null && snapshot.getPlayers() != null) {
                turingStates = snapshot.getPlayers().stream()
                        .collect(Collectors.toMap(TuringHuntPlayerState::getPlayerId, state -> state));
            }
        }
        List<GamePlayer> players = getPlayers(game.getId());
        Map<Long, ModelConfig> modelConfigs = loadModelConfigs(players);
        Map<Long, BattlePlayerState> finalBattleStates = battleStates;
        Map<Long, WerewolfPlayerState> finalWerewolfStates = werewolfStates;
        Map<Long, TuringHuntPlayerState> finalTuringStates = turingStates;
        return players.stream().map(player -> {
            BattlePlayerState state = finalBattleStates.get(player.getId());
            WerewolfPlayerState wolfState = finalWerewolfStates.get(player.getId());
            TuringHuntPlayerState turingState = finalTuringStates.get(player.getId());
            ModelConfig modelConfig = player.getModelConfigId() == null
                    ? null : modelConfigs.get(player.getModelConfigId());
            Map<String, Object> publicState = player.getPublicState() == null ? Map.of() : player.getPublicState();
            AgentPersonaSnapshot fallbackPersona = (game.getMode() == GameMode.WEREWOLF
                    || game.getMode() == GameMode.TURING_HUNT)
                    && publicState.get("agentProfileName") == null
                    ? AgentPersonaPresets.defaultSnapshot(player.getSeatNo()) : null;
            boolean revealWerewolfRole = ownerView || game.getStatus() == GameStatus.FINISHED;
            boolean revealBattleLocation = ownerView || state == null || !state.isHidden() || state.isExposed();
            boolean turingMode = game.getMode() == GameMode.TURING_HUNT;
            boolean revealTuringIdentity = turingMode && game.getStatus() == GameStatus.FINISHED;
            boolean revealModel = !turingMode || revealTuringIdentity;
            boolean aiSeat = player.getPlayerType() == com.zn.agentarena.common.enums.PlayerType.AI;
            return GamePlayerResponse.builder()
                    .id(turingMode ? null : player.getId()).seatNo(player.getSeatNo())
                    .playerType(turingMode && !revealTuringIdentity ? null : player.getPlayerType())
                    .modelProvider(!revealModel || !aiSeat || modelConfig == null ? null : modelConfig.getProvider())
                    .modelName(!revealModel || !aiSeat || modelConfig == null ? null : modelConfig.getModelName())
                    .modelDisplayName(!revealModel || !aiSeat || modelConfig == null ? null : modelConfig.getName())
                    .localStrategy(!revealModel || !aiSeat ? null : player.getModelConfigId() == null)
                    .agentProfileName(fallbackPersona == null ? stringValue(publicState.get("agentProfileName")) : fallbackPersona.getName())
                    .agentProfileCode(fallbackPersona == null ? stringValue(publicState.get("agentProfileCode")) : fallbackPersona.getCode())
                    .agentDescription(fallbackPersona == null ? stringValue(publicState.get("agentDescription")) : fallbackPersona.getDescription())
                    .agentTraits(fallbackPersona == null ? stringList(publicState.get("agentTraits")) : fallbackPersona.getTraits())
                    .agentSpeakingStyle(fallbackPersona == null ? stringValue(publicState.get("agentSpeakingStyle")) : fallbackPersona.getSpeakingStyle())
                    .agentRiskPreference(fallbackPersona == null ? stringValue(publicState.get("agentRiskPreference")) : fallbackPersona.getRiskPreference())
                    .agentReasoningPreference(fallbackPersona == null ? stringValue(publicState.get("agentReasoningPreference")) : fallbackPersona.getReasoningPreference())
                    .agentBuiltIn(fallbackPersona == null ? Boolean.TRUE.equals(publicState.get("agentBuiltIn")) : true)
                    .agentAvatarEmoji(fallbackPersona == null ? stringValue(publicState.get("agentAvatarEmoji")) : fallbackPersona.getAvatarEmoji())
                    .agentAccentColor(fallbackPersona == null ? stringValue(publicState.get("agentAccentColor")) : fallbackPersona.getAccentColor())
                    .displayName(player.getDisplayName())
                    .status(turingState == null ? player.getStatus()
                            : turingState.isAlive() ? com.zn.agentarena.modules.game.domain.PlayerStatus.ALIVE
                            : com.zn.agentarena.modules.game.domain.PlayerStatus.ELIMINATED)
                    .roleCode(turingMode ? revealTuringIdentity ? player.getRoleCode() : null
                            : revealWerewolfRole ? player.getRoleCode() : null)
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
                    .exposed(state == null ? null : state.isExposed())
                    .build();
        }).toList();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
    }

    private List<ReplayEventResponse> mapReplay(List<GameEvent> events, boolean publicView,
                                                boolean turingView) {
        return events.stream().map(event -> ReplayEventResponse.builder()
                .sequenceNo(event.getSequenceNo()).eventType(event.getEventType())
                .actorPlayerId(turingView ? null : publicView
                        ? PublicEventPolicy.publicActorPlayerId(event.getEventType(), event.getActorPlayerId())
                        : event.getActorPlayerId())
                .roundNo(event.getRoundNo()).phase(event.getPhase())
                .payload(publicView || turingView ? event.getPublicPayload() : event.getPayload())
                .createTime(event.getCreateTime()).build()).toList();
    }

    private GameSummaryResponse buildSummary(Game game) {
        List<GameEvent> events = eventRecorder.listRecentEvents(game.getId(), 5_000);
        List<String> keyEvents = events.stream().filter(event -> KEY_EVENTS.contains(event.getEventType()))
                .map(this::summarizeKeyEvent).toList();
        String winner = events.stream()
                .filter(event -> Set.of("GAME_FINISHED", "BATTLE_FINISHED", "TURING_GAME_FINISHED")
                        .contains(event.getEventType()))
                .reduce((first, second) -> second)
                .map(event -> String.valueOf(event.getPublicPayload().getOrDefault("winner",
                        event.getPublicPayload().getOrDefault("winnerName",
                                event.getPublicPayload().get("winnerPlayerId")))))
                .orElse(null);
        int eliminations = (int) events.stream()
                .filter(event -> Set.of("PLAYER_ELIMINATED", "BATTLE_PLAYER_ELIMINATED",
                                "TURING_PLAYER_ELIMINATED")
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
            case "TURING_PLAYER_ELIMINATED" -> "第" + event.getRoundNo() + "轮："
                    + payload.get("seatNo") + "号席位出局并揭晓为" + payload.get("identity");
            case "TURING_VOTE_TIED" -> "第" + event.getRoundNo() + "轮：图灵潜伏局投票平局";
            case "TURING_ROUND_FINISHED" -> "第" + event.getRoundNo() + "轮结束：错误淘汰 AI "
                    + payload.get("wrongAiEliminationCount") + " 名";
            case "GAME_FINISHED", "BATTLE_FINISHED", "TURING_GAME_FINISHED" -> "对局结束：" + payload;
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

    private Map<Long, ModelConfig> loadModelConfigs(List<GamePlayer> players) {
        List<Long> ids = players.stream().map(GamePlayer::getModelConfigId).filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return modelConfigMapper.selectListByQuery(QueryWrapper.create().in(ModelConfig::getId, ids)).stream()
                .collect(Collectors.toMap(ModelConfig::getId, config -> config));
    }

    private void ensureUserExists(Long userId) {
        if (userId == null || userMapper.selectOneById(userId) == null) {
            throw CustomException.create(ResultCodeEnum.USER_NOT_FOUND);
        }
    }

    private GameResponse toResponse(Game game, boolean ownerView, boolean includeTurnProjection) {
        GameResponse.GameResponseBuilder builder = GameResponse.builder().id(game.getId()).name(game.getName()).mode(game.getMode())
                .status(game.getStatus()).currentPhase(game.getCurrentPhase()).currentRound(game.getCurrentRound())
                .maxPlayers(game.getMaxPlayers()).publicGame(game.getPublicGame())
                .startTime(game.getStartTime()).finishTime(game.getFinishTime())
                .createTime(game.getCreateTime()).updateTime(game.getUpdateTime());
        if (!includeTurnProjection) {
            return builder.build();
        }
        if (game.getMode() == GameMode.TURING_HUNT) {
            return toTuringResponse(game, ownerView, builder);
        }
        if (game.getStatus() != GameStatus.RUNNING) return builder.build();
        List<GamePlayer> players = getPlayers(game.getId());
        Long actorId = null;
        String actionType = null;
        boolean hideNightActor = false;
        if (game.getMode() == GameMode.WEREWOLF) {
            WerewolfGameState state = snapshotService.loadLatestWerewolfSnapshot(game.getId());
            if (state != null && state.getPhase() != null) {
                WerewolfPlayerState actor = nextWerewolfActor(state);
                actorId = actor == null ? null : actor.getPlayerId();
                actionType = state.getPhase().name();
                hideNightActor = !ownerView && state.getPhase().name().startsWith("NIGHT_");
            }
        } else if (game.getMode() == GameMode.BATTLE_ROYALE) {
            BattleRoyaleGameState state = snapshotService.loadLatestBattleSnapshot(game.getId());
            if (state != null && !state.turnPlayers().isEmpty()) {
                List<BattlePlayerState> turnPlayers = state.turnPlayers();
                actorId = turnPlayers.get(state.getActionsInRound() % turnPlayers.size()).getPlayerId();
                actionType = "BATTLE_ACTION";
            }
        }
        if (hideNightActor) {
            return builder.currentActorName("夜间角色正在行动").currentActionType(actionType).build();
        }
        Long resolvedActorId = actorId;
        GamePlayer actor = players.stream().filter(player -> player.getId().equals(resolvedActorId)).findFirst().orElse(null);
        ModelConfig config = actor == null || actor.getModelConfigId() == null ? null
                : modelConfigMapper.selectOneById(actor.getModelConfigId());
        Map<String, Object> actorPublicState = actor == null || actor.getPublicState() == null
                ? Map.of() : actor.getPublicState();
        AgentPersonaSnapshot fallbackActorPersona = actor != null && game.getMode() == GameMode.WEREWOLF
                && actorPublicState.get("agentProfileName") == null
                ? AgentPersonaPresets.defaultSnapshot(actor.getSeatNo()) : null;
        return builder.currentActorPlayerId(actorId)
                .currentActorName(actor == null ? null : actor.getDisplayName())
                .currentActionType(actionType)
                .currentActorModelProvider(config == null ? null : config.getProvider())
                .currentActorModelName(config == null ? null : config.getModelName())
                .currentActorModelDisplayName(config == null ? null : config.getName())
                .currentActorLocalStrategy(actor == null ? null : actor.getModelConfigId() == null)
                .currentActorPersonaName(fallbackActorPersona == null
                        ? stringValue(actorPublicState.get("agentProfileName")) : fallbackActorPersona.getName())
                .currentActorPersonaCode(fallbackActorPersona == null
                        ? stringValue(actorPublicState.get("agentProfileCode")) : fallbackActorPersona.getCode())
                .build();
    }

    private GameResponse toTuringResponse(Game game, boolean ownerView,
                                          GameResponse.GameResponseBuilder builder) {
        List<GamePlayer> players = getPlayers(game.getId());
        GamePlayer humanEntity = players.stream()
                .filter(player -> player.getPlayerType() == com.zn.agentarena.common.enums.PlayerType.HUMAN)
                .findFirst().orElse(null);
        TuringHuntGameState state = snapshotService.loadLatestTuringSnapshot(game.getId());
        int configuredMaxRounds = game.getRuleConfig() != null
                && game.getRuleConfig().get("maxRounds") instanceof Number number
                ? number.intValue() : 3;
        if (state == null) {
            return builder.maxRounds(configuredMaxRounds)
                    .humanActionRequired(ownerView ? false : null)
                    .selfSeatNo(ownerView && humanEntity != null ? humanEntity.getSeatNo() : null)
                    .alivePlayerCount((int) players.stream()
                            .filter(player -> player.getStatus()
                                    != com.zn.agentarena.modules.game.domain.PlayerStatus.ELIMINATED).count())
                    .wrongAiEliminationCount(0)
                    .submissionCompletedCount(0).submissionRequiredCount(0)
                    .controversySeatNos(List.of()).publicSuspicionCounts(Map.of())
                    .publicVoteCounts(Map.of()).revoteCandidateSeatNos(List.of())
                    .identitiesRevealed(game.getStatus() == GameStatus.FINISHED)
                    .turingRecap(game.getStatus() == GameStatus.FINISHED ? loadTuringRecap(game.getId()) : null)
                    .build();
        }

        boolean finished = state.getPhase() == TuringHuntPhase.FINISHED || state.getWinner() != null
                || game.getStatus() == GameStatus.FINISHED;
        TuringHuntActionType required = finished ? null : turingGameEngine.requiredActionType(state);
        Long actorId = finished ? null : turingGameEngine.requiredActorPlayerId(state);
        TuringHuntPlayerState actor = state.findPlayer(actorId);
        boolean humanRequired = actor != null && actor.isHuman() && required != TuringHuntActionType.ADVANCE;
        TuringHuntPlayerState human = state.findPlayer(state.getHumanPlayerId());
        SubmissionProgress progress = turingSubmissionProgress(state);
        return builder.currentActorPlayerId(null)
                .currentActorName(finished ? null : "等待本阶段提交")
                .currentActionType(required == null ? null : required.name())
                .currentActorModelProvider(null).currentActorModelName(null)
                .currentActorModelDisplayName(null).currentActorLocalStrategy(null)
                .currentActorPersonaName(null).currentActorPersonaCode(null)
                .maxRounds(state.getMaxRounds()).currentQuestion(state.getCurrentQuestion())
                .humanActionRequired(ownerView ? humanRequired : null)
                .requiredHumanAction(ownerView && humanRequired && required != null ? required.name() : null)
                .selfSeatNo(ownerView && human != null ? human.getSeatNo() : null)
                .alivePlayerCount(state.alivePlayers().size())
                .wrongAiEliminationCount(state.getWrongAiEliminationCount())
                .submissionCompletedCount(progress.completed())
                .submissionRequiredCount(progress.required())
                .controversySeatNos(turingSeatNos(state, state.getControversyPlayerIds()))
                .publicSuspicionCounts(turingSeatCounts(state, state.getPublicSuspicionCounts()))
                .publicVoteCounts(turingSeatCounts(state, state.getPublicVoteCounts()))
                .revoteCandidateSeatNos(turingSeatNos(state, state.getRevoteCandidates()))
                .currentQuestionerSeatNo(turingSeatNo(state, state.getCurrentQuestionerPlayerId()))
                .currentQuestionTargetSeatNo(turingSeatNo(state, state.getCurrentQuestionTargetPlayerId()))
                .winner(state.getWinner() == null ? null : state.getWinner().name())
                .finishedReason(state.getFinishedReason() == null ? null : state.getFinishedReason().name())
                .identitiesRevealed(finished)
                .turingRecap(finished ? loadTuringRecap(game.getId()) : null)
                .build();
    }

    private SubmissionProgress turingSubmissionProgress(TuringHuntGameState state) {
        int alive = state.alivePlayers().size();
        return switch (state.getPhase()) {
            case ANSWER_COLLECTION -> new SubmissionProgress(state.getAnswersByPlayerId().size(), alive);
            case ANSWER_REVEAL -> new SubmissionProgress(alive, alive);
            case SUSPICION_COLLECTION -> new SubmissionProgress(state.getSuspicionsByPlayerId().size(), alive);
            case CONTROVERSY_REVEAL -> new SubmissionProgress(alive, alive);
            case QUESTION, RESPONSE -> {
                int required = Math.max(1, state.getQuestionQueue().size() * 2);
                int completed = state.getCurrentExchanges().stream().mapToInt(exchange ->
                        (exchange.question() == null ? 0 : 1) + (exchange.response() == null ? 0 : 1)).sum();
                yield new SubmissionProgress(Math.min(completed, required), required);
            }
            case DEFENSE -> new SubmissionProgress(
                    Math.min(state.getDefenseIndex(), state.getDefenseQueue().size()), state.getDefenseQueue().size());
            case VOTE_COLLECTION -> new SubmissionProgress(state.getVotesByPlayerId().size(), alive);
            case VOTE_REVEAL -> new SubmissionProgress(alive, alive);
            default -> new SubmissionProgress(0, 0);
        };
    }

    private Integer turingSeatNo(TuringHuntGameState state, Long playerId) {
        TuringHuntPlayerState player = state.findPlayer(playerId);
        return player == null ? null : player.getSeatNo();
    }

    private List<Integer> turingSeatNos(TuringHuntGameState state, List<Long> playerIds) {
        if (playerIds == null) return List.of();
        return playerIds.stream().map(id -> turingSeatNo(state, id)).filter(Objects::nonNull).toList();
    }

    private Map<Integer, Integer> turingSeatCounts(TuringHuntGameState state, Map<Long, Integer> counts) {
        if (counts == null || counts.isEmpty()) return Map.of();
        Map<Integer, Integer> result = new LinkedHashMap<>();
        counts.entrySet().stream().sorted(java.util.Comparator.comparing(entry ->
                        turingSeatNo(state, entry.getKey()), java.util.Comparator.nullsLast(Integer::compareTo)))
                .forEach(entry -> {
                    Integer seatNo = turingSeatNo(state, entry.getKey());
                    if (seatNo != null && entry.getValue() != null && entry.getValue() > 0) {
                        result.put(seatNo, entry.getValue());
                    }
                });
        return result;
    }

    private Map<String, Object> loadTuringRecap(Long gameId) {
        List<GameEvent> events = eventRecorder.listRecentEvents(gameId, 5_000);
        if (events == null) return Map.of();
        return events.stream().filter(event -> "TURING_GAME_FINISHED".equals(event.getEventType()))
                .reduce((first, second) -> second)
                .map(GameEvent::getPublicPayload).filter(Objects::nonNull)
                .map(LinkedHashMap::new).map(map -> (Map<String, Object>) map).orElse(Map.of());
    }

    private record SubmissionProgress(int completed, int required) {
    }

    private WerewolfPlayerState nextWerewolfActor(WerewolfGameState state) {
        return switch (state.getPhase()) {
            case NIGHT_WOLF -> state.getPlayers().stream()
                    .filter(player -> player.isAlive() && player.getRole() == WerewolfRole.WEREWOLF).findFirst().orElse(null);
            case NIGHT_GUARD -> aliveRole(state, WerewolfRole.GUARD);
            case NIGHT_SEER -> aliveRole(state, WerewolfRole.SEER);
            case NIGHT_WITCH -> aliveRole(state, WerewolfRole.WITCH);
            case DAY_DISCUSSION -> state.getDiscussionIndex() < state.alivePlayers().size()
                    ? state.alivePlayers().get(state.getDiscussionIndex()) : null;
            case DAY_QUESTION -> interactionActor(state, true);
            case DAY_RESPONSE -> interactionActor(state, false);
            case DAY_FINAL_STATEMENT -> state.getFinalStatementIndex() < state.alivePlayers().size()
                    ? state.alivePlayers().get(state.getFinalStatementIndex()) : null;
            case DAY_VOTE -> state.alivePlayers().stream()
                    .filter(player -> !state.getVotes().containsKey(player.getPlayerId())).findFirst().orElse(null);
            case FINISHED -> null;
        };
    }

    private WerewolfPlayerState interactionActor(WerewolfGameState state, boolean question) {
        if (state.getQuestionQueue() == null || state.getQuestionIndex() < 0
                || state.getQuestionIndex() >= state.getQuestionQueue().size()) return null;
        var pair = state.getQuestionQueue().get(state.getQuestionIndex());
        return state.findPlayer(question ? pair.getQuestionerPlayerId() : pair.getRespondentPlayerId());
    }

    private WerewolfPlayerState aliveRole(WerewolfGameState state, WerewolfRole role) {
        return state.getPlayers().stream().filter(player -> player.isAlive() && player.getRole() == role)
                .findFirst().orElse(null);
    }
}
