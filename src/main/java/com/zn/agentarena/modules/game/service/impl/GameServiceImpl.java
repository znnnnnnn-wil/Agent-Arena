package com.zn.agentarena.modules.game.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.zn.agentarena.common.enums.*;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.common.utils.FixedWindowRateLimiter;
import com.zn.agentarena.config.AgentArenaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zn.agentarena.dao.entity.Game;
import com.zn.agentarena.dao.entity.GamePlayer;
import com.zn.agentarena.dao.entity.ModelConfig;
import com.zn.agentarena.dao.mapper.GameMapper;
import com.zn.agentarena.dao.mapper.GamePlayerMapper;
import com.zn.agentarena.dao.mapper.ModelConfigMapper;
import com.zn.agentarena.dao.mapper.UserMapper;
import com.zn.agentarena.modules.game.dto.request.CreateWerewolfGameRequest;
import com.zn.agentarena.modules.game.dto.response.GameResponse;
import com.zn.agentarena.modules.game.dto.response.GamePlayerResponse;
import com.zn.agentarena.modules.game.service.IGameService;
import com.zn.agentarena.modules.game.service.GameTurnCoordinator;
import com.zn.agentarena.modules.game.service.GameReadService;
import com.zn.agentarena.modules.replay.dto.response.GameSummaryResponse;
import com.zn.agentarena.modules.replay.dto.response.ReplayEventResponse;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.agent.BattleAiGameRunner;
import com.zn.agentarena.modules.battle.engine.BattleRoyaleGameEngine;
import com.zn.agentarena.modules.battle.dto.request.CreateBattleGameRequest;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.service.IModelConfigService;
import com.zn.agentarena.modules.spectator.service.GameEventRecorder;
import com.zn.agentarena.modules.werewolf.agent.WerewolfAiGameRunner;
import com.zn.agentarena.modules.werewolf.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.*;

/**
 * 游戏服务实现类。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameServiceImpl implements IGameService {

    private static final List<WerewolfRole> STANDARD_ROLES = List.of(
            WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF, WerewolfRole.SEER,
            WerewolfRole.WITCH, WerewolfRole.GUARD, WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER, WerewolfRole.VILLAGER);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final GameMapper gameMapper;
    private final GamePlayerMapper gamePlayerMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final UserMapper userMapper;
    private final IModelConfigService modelConfigService;
    private final WerewolfAiGameRunner aiGameRunner;
    private final BattleAiGameRunner battleAiGameRunner;
    private final GameEventRecorder eventRecorder;
    private final com.zn.agentarena.modules.game.service.GameSnapshotService snapshotService;
    private final GameTurnCoordinator turnCoordinator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final FixedWindowRateLimiter rateLimiter;
    private final AgentArenaProperties properties;
    private final GameReadService gameReadService;
    private final BattleRoyaleGameEngine battleGameEngine = new BattleRoyaleGameEngine();

    @Override
    public List<GameResponse> listGames(Long userId) {
        return gameReadService.listOwned(userId);
    }

    /** 创建狼人杀游戏及其 AI 玩家。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GameResponse createWerewolfGame(Long userId, CreateWerewolfGameRequest request) {
        ensureUserExists(userId);
        validateWerewolfModelConfigs(userId, request.getModelConfigIds());
        long randomSeed = SECURE_RANDOM.nextLong();
        List<WerewolfRole> shuffledRoles = new ArrayList<>(STANDARD_ROLES);
        Collections.shuffle(shuffledRoles, new Random(randomSeed));
        Game game = Game.builder()
                .ownerUserId(userId)
                .name(normalize(request.getName(), "游戏名称不能为空"))
                .mode(GameMode.WEREWOLF)
                .status(GameStatus.WAITING)
                .currentPhase(WerewolfPhase.NIGHT_WOLF.name())
                .currentRound(0)
                .maxPlayers(8)
                .publicGame(request.getPublicGame())
                .ruleConfig(Map.of("roles", shuffledRoles.stream().map(Enum::name).toList()))
                .randomSeed(randomSeed)
                .version(0)
                .build();
        gameMapper.insertSelective(game);

        for (int i = 0; i < shuffledRoles.size(); i++) {
            GamePlayer player = GamePlayer.builder()
                    .gameId(game.getId())
                    .seatNo(i + 1)
                    .playerType(PlayerType.AI)
                    .modelConfigId(modelConfigIdAt(request.getModelConfigIds(), i))
                    .displayName((i + 1) + "号玩家")
                    .status(PlayerStatus.WAITING)
                    .roleCode(shuffledRoles.get(i).name())
                    .version(0)
                    .build();
            gamePlayerMapper.insertSelective(player);
        }
        return toResponse(game);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** 创建大逃杀游戏及其 AI 玩家。 */
    public GameResponse createBattleGame(Long userId, CreateBattleGameRequest request) {
        ensureUserExists(userId);
        int playerCount = Optional.ofNullable(request.getPlayerCount()).orElse(8);
        if (playerCount < 6 || playerCount > 10) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "大逃杀玩家人数必须在6到10之间");
        }
        if (request.getModelConfigIds() != null && !request.getModelConfigIds().isEmpty()
                && request.getModelConfigIds().size() != playerCount) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR,
                    "大逃杀模型配置必须为空或与玩家人数一一对应");
        }
        validateOptionalModelConfigs(userId, request.getModelConfigIds());
        int maxRounds = Math.max(12, Math.min(30, Optional.ofNullable(request.getMaxRounds()).orElse(20)));
        Game game = Game.builder().ownerUserId(userId)
                .name(normalize(request.getName(), "游戏名称不能为空"))
                .mode(GameMode.BATTLE_ROYALE).status(GameStatus.WAITING).currentPhase("ROUND")
                .currentRound(1).maxPlayers(playerCount)
                .publicGame(request.getPublicGame()).ruleConfig(Map.of(
                        "regions", List.of("A", "B", "C", "D", "E"), "maxRounds", maxRounds))
                .randomSeed(SECURE_RANDOM.nextLong()).version(0).build();
        gameMapper.insertSelective(game);
        for (int i = 0; i < playerCount; i++) {
            gamePlayerMapper.insertSelective(GamePlayer.builder().gameId(game.getId()).seatNo(i + 1)
                    .playerType(PlayerType.AI).modelConfigId(modelConfigIdAt(request.getModelConfigIds(), i))
                    .displayName((i + 1) + "号幸存者").status(PlayerStatus.WAITING).version(0).build());
        }
        return toResponse(game);
    }

    @Override
    /** 初始化并启动狼人杀游戏状态。 */
    public GameResponse startGame(Long userId, Long gameId) {
        rateLimiter.check(userId, "game-turn", properties.game().turnsPerMinute());
        return turnCoordinator.withGameLock(gameId, () -> {
            GameResponse response = transactionTemplate.execute(ignored -> {
                Game game = getOwnedGame(userId, gameId);
                if (game.getMode() != GameMode.WEREWOLF || game.getStatus() != GameStatus.WAITING) {
                    throw CustomException.create(ResultCodeEnum.GAME_STATUS_ERROR, "当前狼人杀游戏不能启动");
                }
                List<GamePlayer> players = getPlayers(gameId);
                if (players.size() != 8) {
                    throw CustomException.create(ResultCodeEnum.GAME_PLAYER_FULL, "狼人杀必须拥有8名玩家");
                }
                List<WerewolfPlayerState> playerStates = players.stream().map(this::toWerewolfPlayer).toList();
                WerewolfGameState state = WerewolfGameState.builder()
                        .gameId(gameId).roundNo(1).phase(WerewolfPhase.NIGHT_WOLF)
                        .players(new ArrayList<>(playerStates)).votes(new LinkedHashMap<>()).build();
                snapshotService.saveWerewolfSnapshot(state);
                game.setStatus(GameStatus.RUNNING);
                game.setCurrentPhase(state.getPhase().name());
                game.setCurrentRound(state.getRoundNo());
                game.setStartTime(LocalDateTime.now());
                updateGameOrThrow(game);
                players.forEach(player -> {
                    player.setStatus(PlayerStatus.ALIVE);
                    updatePlayerOrThrow(player);
                });
                return toResponse(game);
            });
            return response;
        });
    }

    @Override
    /** 初始化并启动大逃杀游戏状态。 */
    public GameResponse startBattleGame(Long userId, Long gameId) {
        rateLimiter.check(userId, "game-turn", properties.game().turnsPerMinute());
        return turnCoordinator.withGameLock(gameId, () -> {
            GameResponse response = transactionTemplate.execute(ignored -> {
                Game game = getOwnedGame(userId, gameId);
                if (game.getMode() != GameMode.BATTLE_ROYALE || game.getStatus() != GameStatus.WAITING) {
                    throw CustomException.create(ResultCodeEnum.GAME_STATUS_ERROR, "当前大逃杀游戏不能启动");
                }
                List<GamePlayer> players = getPlayers(gameId);
                List<String> spawnRegions = List.of("A", "B", "C", "D", "E");
                List<BattlePlayerState> states = players.stream().map(player -> BattlePlayerState.builder()
                        .playerId(player.getId()).displayName(player.getDisplayName())
                        .region(spawnRegions.get((player.getSeatNo() - 1) % spawnRegions.size()))
                        .health(100).food(5).water(5).alive(true).build()).toList();
                int maxRounds = Optional.ofNullable(game.getRuleConfig()).map(config -> config.get("maxRounds"))
                        .filter(Number.class::isInstance).map(Number.class::cast).map(Number::intValue).orElse(20);
                BattleRoyaleGameState state = BattleRoyaleGameState.builder().gameId(gameId).roundNo(1)
                        .maxRounds(maxRounds).randomSeed(game.getRandomSeed()).players(new ArrayList<>(states))
                        .safeRegion("A").build();
                snapshotService.saveBattleSnapshot(state);
                game.setStatus(GameStatus.RUNNING);
                game.setCurrentPhase("ROUND");
                game.setCurrentRound(1);
                game.setStartTime(LocalDateTime.now());
                updateGameOrThrow(game);
                players.forEach(player -> {
                    player.setStatus(PlayerStatus.ALIVE);
                    updatePlayerOrThrow(player);
                });
                return toResponse(game);
            });
            return response;
        });
    }

    @Override
    /** 推进狼人杀 AI 的下一次行动并持久化状态。 */
    public GameResponse runNextAiTurn(Long userId, Long gameId) {
        rateLimiter.check(userId, "game-turn", properties.game().turnsPerMinute());
        return turnCoordinator.withGameLock(gameId, () -> {
            Game game = getOwnedGame(userId, gameId);
            if (game.getMode() != GameMode.WEREWOLF || game.getStatus() != GameStatus.RUNNING) {
                throw CustomException.create(ResultCodeEnum.GAME_STATUS_ERROR, "只有运行中的狼人杀可以推进");
            }
            WerewolfGameState state = copyWerewolfState(loadWerewolfState(gameId));
            Map<Long, ModelRuntimeConfig> configs = new LinkedHashMap<>();
            for (GamePlayer player : getPlayers(gameId)) {
                if (player.getModelConfigId() != null) {
                    ModelRuntimeConfig source = loadRuntimeConfigOrNull(userId, player.getModelConfigId());
                    if (source != null) {
                        configs.put(player.getId(), optimizeRuntimeConfig(source));
                    }
                }
            }
            if (!configs.isEmpty()) {
                rateLimiter.check(userId, "model-call", properties.model().callsPerMinute());
            }
            long previousSequence = state.getSequenceNo();
            var result = aiGameRunner.runNextTurn(state, configs);
            GameResponse response = transactionTemplate.execute(ignored -> {
                persistEngineResult(game, state, result, previousSequence);
                return toResponse(game);
            });
            return response;
        });
    }

    @Override
    /** 为当前大逃杀玩家生成并执行下一次 AI 行动。 */
    public GameResponse runNextBattleTurn(Long userId, Long gameId) {
        rateLimiter.check(userId, "game-turn", properties.game().turnsPerMinute());
        return turnCoordinator.withGameLock(gameId, () -> {
            Game game = getOwnedGame(userId, gameId);
            if (game.getMode() != GameMode.BATTLE_ROYALE || game.getStatus() != GameStatus.RUNNING) {
                throw CustomException.create(ResultCodeEnum.GAME_STATUS_ERROR, "当前不是运行中的大逃杀");
            }
            BattleRoyaleGameState state = copyBattleState(loadBattleState(gameId));
            List<BattlePlayerState> alive = state.alivePlayers();
            if (alive.isEmpty()) throw CustomException.create(ResultCodeEnum.GAME_ENGINE_ERROR, "没有可行动玩家");
            if (state.getRoundNo() == 1 && state.getActionsInRound() == 0
                    && state.getSequenceNo() >= state.getPlayers().size()) {
                state.setRoundNo(1 + (int) (state.getSequenceNo() / state.getPlayers().size()));
            }
            BattlePlayerState actor = alive.get(state.getActionsInRound() % alive.size());
            GamePlayer actorEntity = getPlayers(gameId).stream()
                    .filter(player -> player.getId().equals(actor.getPlayerId())).findFirst().orElse(null);
            ModelRuntimeConfig sourceConfig = actorEntity == null || actorEntity.getModelConfigId() == null ? null
                    : loadRuntimeConfigOrNull(userId, actorEntity.getModelConfigId());
            ModelRuntimeConfig runtimeConfig = sourceConfig == null ? null : optimizeRuntimeConfig(sourceConfig);
            if (runtimeConfig != null) {
                rateLimiter.check(userId, "model-call", properties.model().callsPerMinute());
            }
            long previousSequence = state.getSequenceNo();
            var result = battleAiGameRunner.runNextTurn(state, actor, runtimeConfig);
            GameResponse response = transactionTemplate.execute(ignored -> {
                recordBattleResult(game, state, result, previousSequence);
                return toResponse(game);
            });
            return response;
        });
    }

    private void recordBattleResult(Game game, BattleRoyaleGameState state,
                                    com.zn.agentarena.modules.game.engine.EngineResult<BattleRoyaleGameState> result,
                                    long previousSequence) {
        for (int i = 0; i < result.events().size(); i++) {
            eventRecorder.record(game.getId(), state.getRoundNo(), "ROUND", previousSequence + 1 + i,
                    result.events().get(i));
        }
        game.setStatus(battleGameEngine.isFinished(state) ? GameStatus.FINISHED : GameStatus.RUNNING);
        game.setCurrentRound(state.getRoundNo());
        if (game.getStatus() == GameStatus.FINISHED) game.setFinishTime(LocalDateTime.now());
        updateGameOrThrow(game);
        Map<Long, BattlePlayerState> stateByPlayerId = state.getPlayers().stream()
                .collect(java.util.stream.Collectors.toMap(BattlePlayerState::getPlayerId, player -> player));
        for (GamePlayer player : getPlayers(game.getId())) {
            BattlePlayerState playerState = stateByPlayerId.get(player.getId());
            if (playerState == null) continue;
            PlayerStatus expected = playerState.isAlive() ? PlayerStatus.ALIVE : PlayerStatus.ELIMINATED;
            if (player.getStatus() != expected) {
                player.setStatus(expected);
                updatePlayerOrThrow(player);
            }
        }
        snapshotService.saveBattleSnapshot(state);
    }

    @Override
    /** 查询用户拥有的游戏详情。 */
    public GameResponse getGame(Long userId, Long gameId) {
        return gameReadService.getOwned(userId, gameId);
    }

    /** 查询用户拥有游戏中的玩家列表。 */
    @Override
    public List<GamePlayerResponse> getGamePlayers(Long userId, Long gameId) {
        return gameReadService.getOwnedPlayers(userId, gameId);
    }

    @Override
    /** 查询用户有权访问的游戏回放事件。 */
    public List<ReplayEventResponse> getReplay(Long userId, Long gameId, long afterSequence, int limit) {
        return gameReadService.getOwnedReplay(userId, gameId, afterSequence, limit);
    }

    @Override
    /** 汇总用户有权访问的游戏结算信息。 */
    public GameSummaryResponse getSummary(Long userId, Long gameId) {
        return gameReadService.getOwnedSummary(userId, gameId);
    }

    @Override
    public List<GameResponse> listPublicGames(int requestedLimit) {
        return gameReadService.listPublic(requestedLimit);
    }

    @Override
    public GameResponse getPublicGame(Long gameId) {
        return gameReadService.getPublic(gameId);
    }

    @Override
    public List<GamePlayerResponse> getPublicGamePlayers(Long gameId) {
        return gameReadService.getPublicPlayers(gameId);
    }

    @Override
    public List<ReplayEventResponse> getPublicReplay(Long gameId, long afterSequence, int limit) {
        return gameReadService.getPublicReplay(gameId, afterSequence, limit);
    }

    @Override
    public GameSummaryResponse getPublicSummary(Long gameId) {
        return gameReadService.getPublicSummary(gameId);
    }

    @Override
    public void assertPublicGame(Long gameId) {
        gameReadService.assertPublic(gameId);
    }

    private void persistEngineResult(Game game, WerewolfGameState state,
                                     com.zn.agentarena.modules.game.engine.EngineResult<WerewolfGameState> result,
                                     long previousSequence) {
        long firstSequence = previousSequence + 1;
        boolean advancesRound = result.events().stream().anyMatch(event -> "PHASE_CHANGED".equals(event.type())
                && WerewolfPhase.NIGHT_WOLF.name().equals(event.payload().get("phase")));
        int eventRound = advancesRound ? Math.max(1, state.getRoundNo() - 1) : state.getRoundNo();
        for (int i = 0; i < result.events().size(); i++) {
            var event = result.events().get(i);
            if ("PHASE_CHANGED".equals(event.type()) && WerewolfPhase.NIGHT_WOLF.name().equals(event.payload().get("phase"))) {
                eventRound = state.getRoundNo();
            }
            eventRecorder.record(game.getId(), eventRound, inferWerewolfEventPhase(event, state.getPhase()).name(),
                    firstSequence + i, event);
        }
        syncGameState(game, state);
        snapshotService.saveWerewolfSnapshot(state);
    }

    private WerewolfPhase inferWerewolfEventPhase(com.zn.agentarena.modules.game.engine.EngineEvent event,
                                                    WerewolfPhase fallback) {
        return switch (event.type()) {
            case "WOLF_TARGET_SELECTED" -> WerewolfPhase.NIGHT_WOLF;
            case "GUARD_PROTECTED" -> WerewolfPhase.NIGHT_GUARD;
            case "SEER_CHECKED" -> WerewolfPhase.NIGHT_SEER;
            case "WITCH_ACTION_COMPLETED", "DAWN_RESULT" -> WerewolfPhase.NIGHT_WITCH;
            case "PLAYER_SPOKE" -> WerewolfPhase.DAY_DISCUSSION;
            case "PLAYER_VOTED", "VOTE_RESULT", "VOTE_TIED" -> WerewolfPhase.DAY_VOTE;
            case "PHASE_CHANGED" -> WerewolfPhase.valueOf(String.valueOf(event.payload().get("phase")));
            default -> fallback;
        };
    }

    private Game getOwnedGame(Long userId, Long gameId) {
        if (userId == null || gameId == null) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "用户ID和游戏ID不能为空");
        }
        Game game = gameMapper.selectOneById(gameId);
        if (game == null || !userId.equals(game.getOwnerUserId())) {
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

    private void validateModelConfigs(Long userId, List<Long> configIds) {
        for (Long configId : configIds) {
            if (configId == null) {
                continue;
            }
            ModelConfig config = modelConfigMapper.selectOneById(configId);
            if (config == null || Boolean.TRUE.equals(config.getDeleted()) || !userId.equals(config.getUserId())
                    || !Boolean.TRUE.equals(config.getEnabled())) {
                throw CustomException.create(ResultCodeEnum.MODEL_CONFIG_NOT_FOUND, "模型配置不存在或已停用");
            }
        }
    }

    private void validateOptionalModelConfigs(Long userId, List<Long> configIds) {
        if (configIds != null && !configIds.isEmpty()) {
            validateModelConfigs(userId, configIds);
        }
    }

    private void validateWerewolfModelConfigs(Long userId, List<Long> configIds) {
        if (configIds != null && !configIds.isEmpty() && configIds.size() != STANDARD_ROLES.size()) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "狼人杀模型配置必须为空或恰好包含8个席位");
        }
        validateOptionalModelConfigs(userId, configIds);
    }

    private Long modelConfigIdAt(List<Long> configIds, int index) {
        return configIds == null || index < 0 || index >= configIds.size() ? null : configIds.get(index);
    }

    private ModelRuntimeConfig optimizeRuntimeConfig(ModelRuntimeConfig source) {
        return new ModelRuntimeConfig(source.provider(), source.modelName(), source.baseUrl(), source.apiKey(),
                source.temperature(), source.maxTokens() == null ? 256 : Math.min(256, source.maxTokens()),
                properties.model().readTimeout(), properties.model().maxRetries());
    }

    private ModelRuntimeConfig loadRuntimeConfigOrNull(Long userId, Long configId) {
        try {
            return modelConfigService.getRuntimeConfig(userId, configId);
        } catch (CustomException exception) {
            if (exception.getResultCodeEnum() == ResultCodeEnum.MODEL_CONFIG_NOT_FOUND) {
                log.info("对局模型配置不可用，改用本地策略: configId={}", configId);
                return null;
            }
            throw exception;
        }
    }

    private WerewolfGameState loadWerewolfState(Long gameId) {
        WerewolfGameState state = snapshotService.loadLatestWerewolfSnapshot(gameId);
        if (state == null) {
            throw CustomException.create(ResultCodeEnum.GAME_ENGINE_ERROR, "游戏状态不存在且没有可用快照");
        }
        return state;
    }

    private BattleRoyaleGameState loadBattleState(Long gameId) {
        BattleRoyaleGameState state = snapshotService.loadLatestBattleSnapshot(gameId);
        if (state == null) {
            throw CustomException.create(ResultCodeEnum.GAME_ENGINE_ERROR, "大逃杀状态不存在且没有可用快照");
        }
        return state;
    }

    private WerewolfGameState copyWerewolfState(WerewolfGameState state) {
        return objectMapper.convertValue(state, WerewolfGameState.class);
    }

    private BattleRoyaleGameState copyBattleState(BattleRoyaleGameState state) {
        return objectMapper.convertValue(state, BattleRoyaleGameState.class);
    }

    private void updateGameOrThrow(Game game) {
        if (gameMapper.update(game) != 1) {
            throw CustomException.create(ResultCodeEnum.GAME_STATUS_ERROR, "游戏已被其他请求推进，请刷新后重试");
        }
    }

    private void updatePlayerOrThrow(GamePlayer player) {
        if (gamePlayerMapper.update(player) != 1) {
            throw CustomException.create(ResultCodeEnum.GAME_STATUS_ERROR, "玩家状态发生并发冲突，请刷新后重试");
        }
    }

    private WerewolfPlayerState toWerewolfPlayer(GamePlayer player) {
        WerewolfRole role = WerewolfRole.valueOf(player.getRoleCode());
        return WerewolfPlayerState.builder()
                .playerId(player.getId()).seatNo(player.getSeatNo()).displayName(player.getDisplayName())
                .role(role).alive(true)
                .antidoteAvailable(role == WerewolfRole.WITCH)
                .poisonAvailable(role == WerewolfRole.WITCH)
                .build();
    }

    private void syncGameState(Game game, WerewolfGameState state) {
        game.setStatus(state.getPhase() == WerewolfPhase.FINISHED ? GameStatus.FINISHED : GameStatus.RUNNING);
        game.setCurrentPhase(state.getPhase().name());
        game.setCurrentRound(state.getRoundNo());
        if (state.getPhase() == WerewolfPhase.FINISHED) {
            game.setFinishTime(LocalDateTime.now());
        }
        updateGameOrThrow(game);
        Map<Long, WerewolfPlayerState> stateByPlayerId = state.getPlayers().stream()
                .collect(java.util.stream.Collectors.toMap(WerewolfPlayerState::getPlayerId, player -> player));
        for (GamePlayer player : getPlayers(game.getId())) {
            WerewolfPlayerState playerState = stateByPlayerId.get(player.getId());
            if (playerState == null) continue;
            PlayerStatus expected = playerState.isAlive() ? PlayerStatus.ALIVE : PlayerStatus.ELIMINATED;
            if (player.getStatus() != expected) {
                player.setStatus(expected);
                updatePlayerOrThrow(player);
            }
        }
    }

    private String normalize(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, message);
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "游戏名称不能超过128个字符");
        }
        return normalized;
    }

    private GameResponse toResponse(Game game) {
        return GameResponse.builder().id(game.getId()).name(game.getName()).mode(game.getMode())
                .status(game.getStatus()).currentPhase(game.getCurrentPhase()).currentRound(game.getCurrentRound())
                .maxPlayers(game.getMaxPlayers()).publicGame(game.getPublicGame())
                .startTime(game.getStartTime()).finishTime(game.getFinishTime())
                .createTime(game.getCreateTime()).updateTime(game.getUpdateTime()).build();
    }
}
