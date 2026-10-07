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
import com.zn.agentarena.dao.entity.AgentProfile;
import com.zn.agentarena.dao.mapper.GameMapper;
import com.zn.agentarena.dao.mapper.GamePlayerMapper;
import com.zn.agentarena.dao.mapper.ModelConfigMapper;
import com.zn.agentarena.dao.mapper.UserMapper;
import com.zn.agentarena.modules.game.dto.request.CreateWerewolfGameRequest;
import com.zn.agentarena.modules.game.dto.request.RematchGameRequest;
import com.zn.agentarena.modules.game.dto.response.GameResponse;
import com.zn.agentarena.modules.game.dto.response.GamePlayerResponse;
import com.zn.agentarena.modules.game.domain.PlayerStatus;
import com.zn.agentarena.modules.agent.domain.AgentPersonaSnapshot;
import com.zn.agentarena.modules.agent.domain.AgentPersonaPresets;
import com.zn.agentarena.modules.agent.service.AgentProfileService;
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
import com.zn.agentarena.modules.turing.agent.TuringHuntAiGameRunner;
import com.zn.agentarena.modules.turing.agent.TuringHuntTurnRequirement;
import com.zn.agentarena.modules.turing.domain.*;
import com.zn.agentarena.modules.turing.dto.request.CreateTuringHuntGameRequest;
import com.zn.agentarena.modules.turing.dto.request.TuringHuntHumanActionRequest;
import com.zn.agentarena.modules.turing.engine.TuringHuntGameEngine;
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
    private static final int TURING_PLAYER_COUNT = 6;
    private static final int TURING_MAX_ROUNDS = 3;

    private final GameMapper gameMapper;
    private final GamePlayerMapper gamePlayerMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final UserMapper userMapper;
    private final IModelConfigService modelConfigService;
    private final AgentProfileService agentProfileService;
    private final WerewolfAiGameRunner aiGameRunner;
    private final BattleAiGameRunner battleAiGameRunner;
    private final TuringHuntAiGameRunner turingAiGameRunner;
    private final GameEventRecorder eventRecorder;
    private final com.zn.agentarena.modules.game.service.GameSnapshotService snapshotService;
    private final GameTurnCoordinator turnCoordinator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final FixedWindowRateLimiter rateLimiter;
    private final AgentArenaProperties properties;
    private final GameReadService gameReadService;
    private final BattleRoyaleGameEngine battleGameEngine = new BattleRoyaleGameEngine();
    private final TuringHuntGameEngine turingGameEngine = new TuringHuntGameEngine();

    @Override
    /**
     * 查询当前用户拥有的游戏。
     *
     * @param userId 用户标识
     * @return 游戏列表
     */
    public List<GameResponse> listGames(Long userId) {
        return gameReadService.listOwned(userId);
    }

    @Override
    public List<GameResponse> listGames(Long userId, int page, int size) {
        return gameReadService.listOwned(userId, page, size);
    }

    /** 创建狼人杀游戏及其 AI 玩家。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GameResponse createWerewolfGame(Long userId, CreateWerewolfGameRequest request) {
        ensureUserExists(userId);
        validateWerewolfModelConfigs(userId, request.getModelConfigIds());
        validatePlayerDisplayNames(request.getPlayerDisplayNames(), STANDARD_ROLES.size());
        List<Long> profileIds = request.getAgentProfileIds();
        Map<Long, AgentProfile> profiles;
        if (profileIds == null || profileIds.isEmpty()) {
            List<AgentProfile> defaults = agentProfileService.ensureBuiltInProfiles(userId);
            if (defaults == null || defaults.size() < STANDARD_ROLES.size()) {
                throw CustomException.create(ResultCodeEnum.FAIL, "内置人格初始化失败，请重试");
            }
            profileIds = defaults.stream().limit(STANDARD_ROLES.size()).map(AgentProfile::getId).toList();
            profiles = defaults.stream().collect(java.util.stream.Collectors.toMap(AgentProfile::getId,
                    profile -> profile, (left, right) -> left, LinkedHashMap::new));
        } else {
            profiles = validateAgentProfiles(userId, profileIds, STANDARD_ROLES.size());
        }
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
                    .agentProfileId(profileIdAt(profileIds, i))
                    .publicState(agentPublicState(profileAt(profiles, profileIds, i)))
                    .displayName(displayNameAt(request.getPlayerDisplayNames(), i, (i + 1) + "号玩家"))
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
        validatePlayerDisplayNames(request.getPlayerDisplayNames(), playerCount);
        validateOptionalModelConfigs(userId, request.getModelConfigIds());
        Map<Long, AgentProfile> profiles = validateAgentProfiles(userId, request.getAgentProfileIds(), playerCount);
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
                    .agentProfileId(profileIdAt(request.getAgentProfileIds(), i))
                    .publicState(agentPublicState(profileAt(profiles, request.getAgentProfileIds(), i)))
                    .displayName(displayNameAt(request.getPlayerDisplayNames(), i, (i + 1) + "号幸存者"))
                    .status(PlayerStatus.WAITING).version(0).build());
        }
        return toResponse(game);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建图灵潜伏游戏及其初始玩家配置。
     *
     * @param userId 创建者标识
     * @param request 创建参数
     * @return 已创建的游戏
     */
    public GameResponse createTuringHuntGame(Long userId, CreateTuringHuntGameRequest request) {
        ensureUserExists(userId);
        if (request == null || request.getHumanSeatNo() == null
                || request.getHumanSeatNo() < 1 || request.getHumanSeatNo() > TURING_PLAYER_COUNT) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "真人座位号必须在 1 到 6 之间");
        }
        validateTuringSeatKeys(request.getPlayerDisplayNames(), "显示名称");
        validateTuringSeatKeys(request.getSeatModelConfigIds(), "模型配置");
        validateTuringSeatKeys(request.getSeatAgentProfileIds(), "人格配置");

        Map<Integer, Long> modelIds = request.getSeatModelConfigIds() == null
                ? Map.of() : request.getSeatModelConfigIds();
        if (modelIds.get(request.getHumanSeatNo()) != null) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "真人席位不能绑定模型配置");
        }
        validateOptionalModelConfigs(userId, modelIds.values().stream().filter(Objects::nonNull).toList());

        List<AgentProfile> defaults = agentProfileService.ensureBuiltInProfiles(userId);
        if (defaults == null || defaults.size() < TURING_PLAYER_COUNT) {
            throw CustomException.create(ResultCodeEnum.FAIL, "内置人格初始化失败，请重试");
        }
        Map<Integer, AgentProfile> profilesBySeat = new LinkedHashMap<>();
        Map<Integer, Long> requestedProfiles = request.getSeatAgentProfileIds() == null
                ? Map.of() : request.getSeatAgentProfileIds();
        for (int seatNo = 1; seatNo <= TURING_PLAYER_COUNT; seatNo++) {
            Long profileId = requestedProfiles.get(seatNo);
            profilesBySeat.put(seatNo, profileId == null
                    ? defaults.get(seatNo - 1) : agentProfileService.getOwned(userId, profileId));
        }

        long randomSeed = SECURE_RANDOM.nextLong();
        Game game = Game.builder().ownerUserId(userId)
                .name(normalize(request.getName(), "游戏名称不能为空"))
                .mode(GameMode.TURING_HUNT).status(GameStatus.WAITING)
                .currentPhase(TuringHuntPhase.WAITING.name()).currentRound(0)
                .maxPlayers(TURING_PLAYER_COUNT).publicGame(request.getPublicGame())
                .ruleConfig(Map.of("humanSeatNo", request.getHumanSeatNo(), "maxRounds", TURING_MAX_ROUNDS))
                .randomSeed(randomSeed).version(0).build();
        gameMapper.insertSelective(game);

        Map<Integer, String> names = request.getPlayerDisplayNames() == null
                ? Map.of() : request.getPlayerDisplayNames();
        for (int seatNo = 1; seatNo <= TURING_PLAYER_COUNT; seatNo++) {
            boolean human = seatNo == request.getHumanSeatNo();
            AgentProfile profile = profilesBySeat.get(seatNo);
            gamePlayerMapper.insertSelective(GamePlayer.builder().gameId(game.getId()).seatNo(seatNo)
                    .playerType(human ? PlayerType.HUMAN : PlayerType.AI)
                    .userId(human ? userId : null)
                    .modelConfigId(human ? null : modelIds.get(seatNo))
                    .agentProfileId(profile.getId()).publicState(agentPublicState(profile))
                    .displayName(turingDisplayName(names.get(seatNo), seatNo))
                    .status(PlayerStatus.WAITING).roleCode(human ? "HUMAN" : "AI")
                    .version(0).build());
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
                        .discussionVersion(2).players(new ArrayList<>(playerStates))
                        .votes(new LinkedHashMap<>()).build();
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
                List<String> regions = List.of("A", "B", "C", "D", "E");
                List<String> spawnRegions = BattleRoyaleRules.spawnAssignments(
                        regions, players.size(), game.getRandomSeed());
                List<String> ringOrder = BattleRoyaleRules.ringOrder(regions, game.getRandomSeed());
                List<BattlePlayerState> states = players.stream().map(player -> BattlePlayerState.builder()
                        .playerId(player.getId()).displayName(player.getDisplayName())
                        .persona(toAgentPersona(player))
                        .region(spawnRegions.get((player.getSeatNo() - 1) % spawnRegions.size()))
                        .health(100).food(5).water(5).alive(true).build()).toList();
                int maxRounds = Optional.ofNullable(game.getRuleConfig()).map(config -> config.get("maxRounds"))
                        .filter(Number.class::isInstance).map(Number.class::cast).map(Number::intValue).orElse(20);
                BattleRoyaleGameState state = BattleRoyaleGameState.builder().gameId(gameId).roundNo(1)
                        .maxRounds(maxRounds).randomSeed(game.getRandomSeed()).players(new ArrayList<>(states))
                        .regions(new ArrayList<>(regions)).safeRegions(new ArrayList<>(regions))
                        .ringOrder(new ArrayList<>(ringOrder)).safeRegion(ringOrder.get(0)).build();
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
    /**
     * 启动等待中的图灵潜伏游戏。
     *
     * @param userId 操作用户标识
     * @param gameId 游戏标识
     * @return 启动后的游戏状态
     */
    public GameResponse startTuringHuntGame(Long userId, Long gameId) {
        rateLimiter.check(userId, "game-turn", properties.game().turnsPerMinute());
        return turnCoordinator.withGameLock(gameId, () -> {
            transactionTemplate.execute(ignored -> {
                Game game = getOwnedGame(userId, gameId);
                if (game.getMode() != GameMode.TURING_HUNT || game.getStatus() != GameStatus.WAITING) {
                    throw CustomException.create(ResultCodeEnum.GAME_STATUS_ERROR, "当前图灵潜伏局不能启动");
                }
                List<GamePlayer> players = getPlayers(gameId);
                validateTuringLineup(userId, players);
                GamePlayer human = players.stream().filter(player -> player.getPlayerType() == PlayerType.HUMAN)
                        .findFirst().orElseThrow(() -> CustomException.create(ResultCodeEnum.PLAYER_NOT_FOUND));
                List<TuringHuntPlayerState> states = players.stream().map(player -> {
                    AgentPersonaSnapshot persona = toAgentPersona(player);
                    if (persona == null) persona = AgentPersonaPresets.defaultSnapshot(player.getSeatNo());
                    return TuringHuntPlayerState.builder().playerId(player.getId()).seatNo(player.getSeatNo())
                            .displayName(player.getDisplayName()).persona(persona)
                            .human(player.getPlayerType() == PlayerType.HUMAN).alive(true).build();
                }).toList();
                TuringHuntGameState state = TuringHuntGameState.builder().gameId(gameId).roundNo(1)
                        .maxRounds(TURING_MAX_ROUNDS).phase(TuringHuntPhase.ROUND_PROMPT)
                        .humanPlayerId(human.getId()).randomSeed(game.getRandomSeed())
                        .players(new ArrayList<>(states)).build();
                long previousSequence = state.getSequenceNo();
                var result = turingGameEngine.execute(state, TuringHuntAction.advance());
                game.setStartTime(LocalDateTime.now());
                persistTuringResult(game, state, result, previousSequence);
                return null;
            });
            return gameReadService.getOwned(userId, gameId);
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
            boolean modelRateLimited = !configs.isEmpty() && !tryConsumeModelQuota(userId);
            if (modelRateLimited) configs = Map.of();
            long previousSequence = state.getSequenceNo();
            var result = aiGameRunner.runNextTurn(state, configs, modelRateLimited);
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
            BattlePlayerState actor = battleGameEngine.currentTurnPlayer(state);
            if (actor == null) throw CustomException.create(ResultCodeEnum.GAME_ENGINE_ERROR, "没有可行动玩家");
            GamePlayer actorEntity = getPlayers(gameId).stream()
                    .filter(player -> player.getId().equals(actor.getPlayerId())).findFirst().orElse(null);
            ModelRuntimeConfig sourceConfig = actorEntity == null || actorEntity.getModelConfigId() == null ? null
                    : loadRuntimeConfigOrNull(userId, actorEntity.getModelConfigId());
            ModelRuntimeConfig runtimeConfig = sourceConfig == null ? null : optimizeRuntimeConfig(sourceConfig);
            boolean modelRateLimited = runtimeConfig != null && !tryConsumeModelQuota(userId);
            if (modelRateLimited) runtimeConfig = null;
            long previousSequence = state.getSequenceNo();
            var result = battleAiGameRunner.runNextTurn(state, actor, runtimeConfig, modelRateLimited);
            GameResponse response = transactionTemplate.execute(ignored -> {
                recordBattleResult(game, state, result, previousSequence);
                return toResponse(game);
            });
            return response;
        });
    }

    @Override
    /**
     * 推进图灵潜伏游戏的下一次 AI 行动或确定性状态转换。
     *
     * @param userId 操作用户标识
     * @param gameId 游戏标识
     * @return 推进后的游戏状态
     */
    public GameResponse runNextTuringTurn(Long userId, Long gameId) {
        rateLimiter.check(userId, "game-turn", properties.game().turnsPerMinute());
        return turnCoordinator.withGameLock(gameId, () -> {
            Game game = getOwnedGame(userId, gameId);
            if (game.getMode() != GameMode.TURING_HUNT || game.getStatus() != GameStatus.RUNNING) {
                throw CustomException.create(ResultCodeEnum.GAME_STATUS_ERROR,
                        "只有运行中的图灵潜伏局可以推进");
            }
            TuringHuntGameState state = copyTuringState(loadTuringState(gameId));
            TuringHuntTurnRequirement requirement = turingAiGameRunner.inspect(state);
            if (requirement.actionType() == null) {
                throw CustomException.create(ResultCodeEnum.GAME_STATUS_ERROR, "图灵潜伏局已经结束");
            }
            if (requirement.humanActionRequired()) {
                return gameReadService.getOwned(userId, gameId);
            }

            ModelRuntimeConfig runtimeConfig = null;
            boolean modelRateLimited = false;
            if (requirement.actorPlayerId() != null) {
                GamePlayer actor = getPlayers(gameId).stream()
                        .filter(player -> Objects.equals(player.getId(), requirement.actorPlayerId()))
                        .findFirst().orElseThrow(() -> CustomException.create(ResultCodeEnum.PLAYER_NOT_FOUND));
                if (actor.getPlayerType() != PlayerType.AI) {
                    throw CustomException.create(ResultCodeEnum.ACTION_NOT_ALLOWED, "当前正在等待真人操作");
                }
                if (actor.getModelConfigId() != null) {
                    ModelRuntimeConfig source = loadRuntimeConfigOrNull(userId, actor.getModelConfigId());
                    runtimeConfig = source == null ? null : optimizeRuntimeConfig(source);
                    modelRateLimited = runtimeConfig != null && !tryConsumeModelQuota(userId);
                    if (modelRateLimited) runtimeConfig = null;
                }
            }
            long previousSequence = state.getSequenceNo();
            var result = turingAiGameRunner.runNextTurn(state, runtimeConfig, modelRateLimited);
            transactionTemplate.execute(ignored -> {
                persistTuringResult(game, state, result, previousSequence);
                return null;
            });
            return gameReadService.getOwned(userId, gameId);
        });
    }

    @Override
    /**
     * 提交图灵潜伏游戏中真人玩家的当前行动。
     *
     * @param userId 操作用户标识
     * @param gameId 游戏标识
     * @param request 真人行动内容
     * @return 提交后的游戏状态
     */
    public GameResponse submitTuringHumanAction(Long userId, Long gameId,
                                                TuringHuntHumanActionRequest request) {
        rateLimiter.check(userId, "game-turn", properties.game().turnsPerMinute());
        return turnCoordinator.withGameLock(gameId, () -> {
            Game game = getOwnedGame(userId, gameId);
            if (game.getMode() != GameMode.TURING_HUNT || game.getStatus() != GameStatus.RUNNING) {
                throw CustomException.create(ResultCodeEnum.GAME_STATUS_ERROR,
                        "当前图灵潜伏局不能提交真人操作");
            }
            if (request == null || request.getPlayerId() != null || request.getActorPlayerId() != null) {
                throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "请求不能包含 playerId");
            }
            TuringHuntGameState state = copyTuringState(loadTuringState(gameId));
            TuringHuntPlayerState human = state.findPlayer(state.getHumanPlayerId());
            GamePlayer humanEntity = getPlayers(gameId).stream()
                    .filter(player -> Objects.equals(player.getId(), state.getHumanPlayerId())).findFirst()
                    .orElseThrow(() -> CustomException.create(ResultCodeEnum.PLAYER_NOT_FOUND));
            if (human == null || !human.isAlive() || humanEntity.getPlayerType() != PlayerType.HUMAN
                    || !Objects.equals(humanEntity.getUserId(), userId)) {
                throw CustomException.create(ResultCodeEnum.ACTION_NOT_ALLOWED,
                        "只有该对局仍存活的真人创建者可以操作");
            }
            TuringHuntTurnRequirement requirement = turingAiGameRunner.inspect(state);
            TuringHuntActionType requestedType = parseTuringActionType(request.getAction());
            if (!requirement.humanActionRequired()
                    || !Objects.equals(requirement.actorPlayerId(), human.getPlayerId())
                    || requirement.actionType() != requestedType) {
                throw CustomException.create(ResultCodeEnum.GAME_PHASE_MISMATCH,
                        "当前阶段不需要该真人动作");
            }
            TuringHuntAction action = toHumanTuringAction(state, human, requestedType, request);
            long previousSequence = state.getSequenceNo();
            var result = turingGameEngine.execute(state, action);
            transactionTemplate.execute(ignored -> {
                persistTuringResult(game, state, result, previousSequence);
                return null;
            });
            return gameReadService.getOwned(userId, gameId);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 基于已有游戏复制规则和席位，创建一场重赛。
     *
     * @param userId 创建者标识
     * @param gameId 原游戏标识
     * @param request 重赛参数
     * @return 新建游戏状态
     */
    public GameResponse rematch(Long userId, Long gameId, RematchGameRequest request) {
        Game source = getOwnedGame(userId, gameId);
        List<GamePlayer> sourcePlayers = getPlayers(gameId);
        if (sourcePlayers.isEmpty()) {
            throw CustomException.create(ResultCodeEnum.GAME_ENGINE_ERROR, "原对局没有可复制的席位");
        }
        long randomSeed = SECURE_RANDOM.nextLong();
        String requestedName = request == null ? null : request.getName();
        String name = StringUtils.hasText(requestedName) ? normalize(requestedName, "对局名称不能为空")
                : source.getName() + " · 重赛";
        Boolean publicGame = request != null && request.getPublicGame() != null
                ? request.getPublicGame() : source.getPublicGame();
        Map<String, Object> ruleConfig = source.getRuleConfig() == null ? new LinkedHashMap<>()
                : objectMapper.convertValue(source.getRuleConfig(), new com.fasterxml.jackson.core.type.TypeReference<>() { });
        List<WerewolfRole> roles = new ArrayList<>(STANDARD_ROLES);
        if (source.getMode() == GameMode.WEREWOLF) {
            Collections.shuffle(roles, new Random(randomSeed));
            ruleConfig.put("roles", roles.stream().map(Enum::name).toList());
        }
        String initialPhase = switch (source.getMode()) {
            case WEREWOLF -> WerewolfPhase.NIGHT_WOLF.name();
            case BATTLE_ROYALE -> "ROUND";
            case TURING_HUNT -> TuringHuntPhase.WAITING.name();
        };
        int initialRound = source.getMode() == GameMode.BATTLE_ROYALE ? 1 : 0;
        Game rematch = Game.builder().ownerUserId(userId).name(name).mode(source.getMode())
                .status(GameStatus.WAITING).currentPhase(initialPhase)
                .currentRound(initialRound)
                .maxPlayers(source.getMaxPlayers()).publicGame(publicGame).ruleConfig(ruleConfig)
                .randomSeed(randomSeed).version(0).build();
        gameMapper.insertSelective(rematch);

        for (int index = 0; index < sourcePlayers.size(); index++) {
            GamePlayer sourcePlayer = sourcePlayers.get(index);
            gamePlayerMapper.insertSelective(GamePlayer.builder().gameId(rematch.getId())
                    .seatNo(sourcePlayer.getSeatNo()).playerType(sourcePlayer.getPlayerType())
                    .userId(sourcePlayer.getUserId()).modelConfigId(sourcePlayer.getModelConfigId())
                    .agentProfileId(sourcePlayer.getAgentProfileId()).displayName(sourcePlayer.getDisplayName())
                    .status(PlayerStatus.WAITING)
                    .roleCode(source.getMode() == GameMode.WEREWOLF ? roles.get(index).name() : sourcePlayer.getRoleCode())
                    .publicState(copyAgentPublicState(sourcePlayer)).privateState(null).version(0).build());
        }
        return toResponse(rematch);
    }

    private void recordBattleResult(Game game, BattleRoyaleGameState state,
                                    com.zn.agentarena.modules.game.engine.EngineResult<BattleRoyaleGameState> result,
                                    long previousSequence) {
        for (int i = 0; i < result.events().size(); i++) {
            Object eventRound = result.events().get(i).payload().get("roundNo");
            eventRecorder.record(game.getId(), eventRound instanceof Number value ? value.intValue() : state.getRoundNo(),
                    "ROUND", previousSequence + 1 + i,
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
            PlayerStatus expected = playerState.isDowned() ? PlayerStatus.DOWNED
                    : playerState.isActive() ? PlayerStatus.ALIVE : PlayerStatus.ELIMINATED;
            if (player.getStatus() != expected) {
                player.setStatus(expected);
                updatePlayerOrThrow(player);
            }
        }
        snapshotService.saveBattleSnapshot(state);
    }

    private void persistTuringResult(Game game, TuringHuntGameState state,
                                     com.zn.agentarena.modules.game.engine.EngineResult<TuringHuntGameState> result,
                                     long previousSequence) {
        for (int index = 0; index < result.events().size(); index++) {
            var event = result.events().get(index);
            eventRecorder.record(game.getId(), state.getRoundNo(), inferTuringEventPhase(event, state),
                    previousSequence + 1 + index, event);
        }
        syncTuringGameState(game, state);
        snapshotService.saveTuringSnapshot(state);
    }

    private String inferTuringEventPhase(com.zn.agentarena.modules.game.engine.EngineEvent event,
                                         TuringHuntGameState state) {
        return switch (event.type()) {
            case "TURING_GAME_STARTED", "TURING_ROUND_STARTED", "TURING_PROMPT_PUBLISHED" ->
                    TuringHuntPhase.ROUND_PROMPT.name();
            case "TURING_ANSWER_SUBMITTED" -> TuringHuntPhase.ANSWER_COLLECTION.name();
            case "TURING_ANSWERS_REVEALED" -> TuringHuntPhase.ANSWER_REVEAL.name();
            case "TURING_SUSPICION_SUBMITTED" -> TuringHuntPhase.SUSPICION_COLLECTION.name();
            case "TURING_CONTROVERSY_REVEALED" -> TuringHuntPhase.CONTROVERSY_REVEAL.name();
            case "TURING_QUESTION_ASKED" -> TuringHuntPhase.QUESTION.name();
            case "TURING_RESPONSE_GIVEN" -> TuringHuntPhase.RESPONSE.name();
            case "TURING_DEFENSE_GIVEN", "TURING_VOTE_TIED" -> TuringHuntPhase.DEFENSE.name();
            case "TURING_VOTE_SUBMITTED" -> TuringHuntPhase.VOTE_COLLECTION.name();
            case "TURING_VOTES_REVEALED" -> TuringHuntPhase.VOTE_REVEAL.name();
            case "TURING_PLAYER_ELIMINATED", "TURING_IDENTITY_REVEALED", "TURING_ROUND_FINISHED" ->
                    TuringHuntPhase.ROUND_RESULT.name();
            case "TURING_GAME_FINISHED" -> TuringHuntPhase.FINISHED.name();
            case "TURING_PHASE_CHANGED" -> String.valueOf(event.payload().getOrDefault("phase",
                    state.getPhase().name()));
            default -> state.getPhase().name();
        };
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
    /**
     * 查询公开游戏详情。
     *
     * @param gameId 游戏标识
     * @return 游戏详情
     */
    public GameResponse getPublicGame(Long gameId) {
        return gameReadService.getPublic(gameId);
    }

    @Override
    /**
     * 查询公开游戏中的玩家。
     *
     * @param gameId 游戏标识
     * @return 玩家列表
     */
    public List<GamePlayerResponse> getPublicGamePlayers(Long gameId) {
        return gameReadService.getPublicPlayers(gameId);
    }

    @Override
    /**
     * 查询公开游戏的回放事件。
     *
     * @param gameId 游戏标识
     * @param afterSequence 排除该序号及之前的事件
     * @param limit 最大返回数量
     * @return 回放事件列表
     */
    public List<ReplayEventResponse> getPublicReplay(Long gameId, long afterSequence, int limit) {
        return gameReadService.getPublicReplay(gameId, afterSequence, limit);
    }

    @Override
    /**
     * 查询公开游戏的结算摘要。
     *
     * @param gameId 游戏标识
     * @return 游戏摘要
     */
    public GameSummaryResponse getPublicSummary(Long gameId) {
        return gameReadService.getPublicSummary(gameId);
    }

    @Override
    /**
     * 校验指定游戏可供公开访问。
     *
     * @param gameId 游戏标识
     */
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
            case "CONTROVERSY_IDENTIFIED", "PLAYER_QUESTIONED" -> WerewolfPhase.DAY_QUESTION;
            case "PLAYER_RESPONDED" -> WerewolfPhase.DAY_RESPONSE;
            case "PLAYER_FINAL_STATEMENT" -> WerewolfPhase.DAY_FINAL_STATEMENT;
            case "PLAYER_VOTED", "VOTE_RESULT", "VOTE_TIED" -> WerewolfPhase.DAY_VOTE;
            case "AI_DECISION" -> parseWerewolfPhase(event.payload().get("decisionPhase"), fallback);
            case "PHASE_CHANGED" -> WerewolfPhase.valueOf(String.valueOf(event.payload().get("phase")));
            default -> fallback;
        };
    }

    private WerewolfPhase parseWerewolfPhase(Object value, WerewolfPhase fallback) {
        if (value == null) return fallback;
        try {
            return WerewolfPhase.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
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

    private Long profileIdAt(List<Long> profileIds, int index) {
        return profileIds == null || index < 0 || index >= profileIds.size() ? null : profileIds.get(index);
    }

    private AgentProfile profileAt(Map<Long, AgentProfile> profiles, List<Long> profileIds, int index) {
        Long profileId = profileIdAt(profileIds, index);
        return profileId == null ? null : profiles.get(profileId);
    }

    private Map<Long, AgentProfile> validateAgentProfiles(Long userId, List<Long> profileIds, int playerCount) {
        if (profileIds == null || profileIds.isEmpty()) return Map.of();
        if (profileIds.size() != playerCount) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "人格配置必须为空或与席位数量一一对应");
        }
        Map<Long, AgentProfile> profiles = new LinkedHashMap<>();
        for (Long profileId : profileIds) {
            if (profileId != null && !profiles.containsKey(profileId)) {
                profiles.put(profileId, agentProfileService.getOwned(userId, profileId));
            }
        }
        return profiles;
    }

    private Map<String, Object> agentPublicState(AgentProfile profile) {
        return profile == null ? null : agentProfileService.publicSnapshot(profile);
    }

    /** A rematch carries only immutable persona presentation, never runtime or private player state. */
    private Map<String, Object> copyAgentPublicState(GamePlayer sourcePlayer) {
        AgentPersonaSnapshot persona = toAgentPersona(sourcePlayer);
        if (persona == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentProfileName", persona.getName());
        result.put("agentProfileCode", persona.getCode());
        result.put("agentDescription", persona.getDescription());
        result.put("agentTraits", persona.getTraits() == null ? List.of() : List.copyOf(persona.getTraits()));
        result.put("agentSpeakingStyle", persona.getSpeakingStyle());
        result.put("agentRiskPreference", persona.getRiskPreference());
        result.put("agentReasoningPreference", persona.getReasoningPreference());
        result.put("agentBehaviorGuidance", persona.getBehaviorGuidance());
        result.put("agentBuiltIn", persona.isBuiltIn());
        result.put("agentAvatarEmoji", persona.getAvatarEmoji());
        result.put("agentAccentColor", persona.getAccentColor());
        return result;
    }

    private ModelRuntimeConfig optimizeRuntimeConfig(ModelRuntimeConfig source) {
        return new ModelRuntimeConfig(source.provider(), source.modelName(), source.baseUrl(), source.apiKey(),
                source.temperature(), source.maxTokens() == null ? 384 : Math.min(512, source.maxTokens()),
                properties.model().readTimeout(), properties.model().maxRetries());
    }

    private boolean tryConsumeModelQuota(Long userId) {
        try {
            rateLimiter.check(userId, "model-call", properties.model().callsPerMinute());
            return true;
        } catch (CustomException exception) {
            if (exception.getResultCodeEnum() == ResultCodeEnum.TOO_MANY_REQUESTS) {
                log.info("模型调用达到频率保护上限，本回合改用本地策略: userId={}", userId);
                return false;
            }
            throw exception;
        }
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

    private TuringHuntGameState loadTuringState(Long gameId) {
        TuringHuntGameState state = snapshotService.loadLatestTuringSnapshot(gameId);
        if (state == null) {
            throw CustomException.create(ResultCodeEnum.GAME_ENGINE_ERROR,
                    "图灵潜伏局状态不存在且没有可用快照");
        }
        return state;
    }

    private WerewolfGameState copyWerewolfState(WerewolfGameState state) {
        return objectMapper.convertValue(state, WerewolfGameState.class);
    }

    private BattleRoyaleGameState copyBattleState(BattleRoyaleGameState state) {
        return objectMapper.convertValue(state, BattleRoyaleGameState.class);
    }

    private TuringHuntGameState copyTuringState(TuringHuntGameState state) {
        return objectMapper.convertValue(state, TuringHuntGameState.class);
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
        AgentPersonaSnapshot persona = toAgentPersona(player);
        if (persona == null) persona = AgentPersonaPresets.defaultSnapshot(player.getSeatNo());
        return WerewolfPlayerState.builder()
                .playerId(player.getId()).seatNo(player.getSeatNo()).displayName(player.getDisplayName())
                .persona(persona)
                .role(role).alive(true)
                .antidoteAvailable(role == WerewolfRole.WITCH)
                .poisonAvailable(role == WerewolfRole.WITCH)
                .build();
    }

    private AgentPersonaSnapshot toAgentPersona(GamePlayer player) {
        Map<String, Object> state = player.getPublicState();
        if (player.getAgentProfileId() == null || state == null || state.get("agentProfileName") == null) return null;
        return AgentPersonaSnapshot.builder().profileId(player.getAgentProfileId())
                .code(stringState(state, "agentProfileCode"))
                .name(stringState(state, "agentProfileName"))
                .description(stringState(state, "agentDescription"))
                .traits(listState(state, "agentTraits"))
                .speakingStyle(stringState(state, "agentSpeakingStyle"))
                .riskPreference(stringState(state, "agentRiskPreference"))
                .reasoningPreference(stringState(state, "agentReasoningPreference"))
                .behaviorGuidance(stringState(state, "agentBehaviorGuidance"))
                .promptInstruction(stringState(state, "agentBehaviorGuidance"))
                .builtIn(Boolean.TRUE.equals(state.get("agentBuiltIn")))
                .avatarEmoji(stringState(state, "agentAvatarEmoji"))
                .accentColor(stringState(state, "agentAccentColor")).build();
    }

    private String stringState(Map<String, Object> state, String key) {
        Object value = state.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private List<String> listState(Map<String, Object> state, String key) {
        Object value = state.get(key);
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
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

    private void syncTuringGameState(Game game, TuringHuntGameState state) {
        game.setStatus(turingGameEngine.isFinished(state) ? GameStatus.FINISHED : GameStatus.RUNNING);
        game.setCurrentPhase(state.getPhase().name());
        game.setCurrentRound(state.getRoundNo());
        if (game.getStatus() == GameStatus.FINISHED) game.setFinishTime(LocalDateTime.now());
        updateGameOrThrow(game);
        Map<Long, TuringHuntPlayerState> stateByPlayerId = state.getPlayers().stream()
                .collect(java.util.stream.Collectors.toMap(TuringHuntPlayerState::getPlayerId, player -> player));
        for (GamePlayer player : getPlayers(game.getId())) {
            TuringHuntPlayerState playerState = stateByPlayerId.get(player.getId());
            if (playerState == null) continue;
            PlayerStatus expected = playerState.isAlive() ? PlayerStatus.ALIVE : PlayerStatus.ELIMINATED;
            if (player.getStatus() != expected) {
                player.setStatus(expected);
                updatePlayerOrThrow(player);
            }
        }
    }

    private void validateTuringLineup(Long userId, List<GamePlayer> players) {
        Set<Integer> seats = players.stream().map(GamePlayer::getSeatNo).collect(java.util.stream.Collectors.toSet());
        long humanCount = players.stream().filter(player -> player.getPlayerType() == PlayerType.HUMAN).count();
        long aiCount = players.stream().filter(player -> player.getPlayerType() == PlayerType.AI).count();
        GamePlayer human = players.stream().filter(player -> player.getPlayerType() == PlayerType.HUMAN)
                .findFirst().orElse(null);
        if (players.size() != TURING_PLAYER_COUNT || seats.size() != TURING_PLAYER_COUNT
                || !seats.containsAll(List.of(1, 2, 3, 4, 5, 6)) || humanCount != 1 || aiCount != 5
                || human == null || !Objects.equals(human.getUserId(), userId)
                || human.getModelConfigId() != null) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR,
                    "图灵潜伏局必须恰好包含创建者控制的一名真人和五名 AI");
        }
    }

    private void validateTuringSeatKeys(Map<Integer, ?> values, String label) {
        if (values == null) return;
        if (values.keySet().stream().anyMatch(seat -> seat == null || seat < 1 || seat > TURING_PLAYER_COUNT)) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, label + "包含非法座位号");
        }
    }

    private String turingDisplayName(String requestedName, int seatNo) {
        if (!StringUtils.hasText(requestedName)) return seatNo + "号玩家";
        String normalized = requestedName.trim();
        if (normalized.codePointCount(0, normalized.length()) > 64
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR,
                    "显示名称不能超过 64 个字符且不能包含控制字符");
        }
        return normalized;
    }

    private TuringHuntActionType parseTuringActionType(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "action 不能为空");
        }
        try {
            TuringHuntActionType type = TuringHuntActionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            if (type == TuringHuntActionType.ADVANCE) throw new IllegalArgumentException("ADVANCE");
            return type;
        } catch (IllegalArgumentException exception) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "不支持的图灵潜伏局真人动作");
        }
    }

    private TuringHuntAction toHumanTuringAction(TuringHuntGameState state, TuringHuntPlayerState human,
                                                  TuringHuntActionType type,
                                                  TuringHuntHumanActionRequest request) {
        Long primary = type == TuringHuntActionType.SUSPECT
                ? turingPlayerIdAtSeat(state, request.getPrimarySuspectSeatNo()) : null;
        List<Long> suspects = type == TuringHuntActionType.SUSPECT
                ? Optional.ofNullable(request.getSuspectSeatNos()).orElse(List.of()).stream()
                        .map(seat -> turingPlayerIdAtSeat(state, seat)).toList()
                : List.of();
        Long target = switch (type) {
            case QUESTION, RESPOND, VOTE -> turingPlayerIdAtSeat(state, request.getTargetSeatNo());
            default -> null;
        };
        Long counter = type == TuringHuntActionType.DEFEND
                ? turingPlayerIdAtSeat(state, request.getCounterSuspectSeatNo()) : null;
        return new TuringHuntAction(human.getPlayerId(), type, request.getAnswer(), primary, suspects,
                Optional.ofNullable(request.getReasonTags()).orElse(List.of()), request.getReasonSummary(),
                target, request.getQuestion(), request.getResponse(), request.getDefense(), counter);
    }

    private Long turingPlayerIdAtSeat(TuringHuntGameState state, Integer seatNo) {
        if (seatNo == null) return null;
        TuringHuntPlayerState player = state.findSeat(seatNo);
        if (player == null) {
            throw CustomException.create(ResultCodeEnum.ACTION_TARGET_INVALID, "目标座位不存在");
        }
        return player.getPlayerId();
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

    private void validatePlayerDisplayNames(List<String> names, int playerCount) {
        if (names == null || names.isEmpty()) {
            return;
        }
        if (names.size() != playerCount) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "玩家名称必须为空或与席位数量一一对应");
        }
        for (String name : names) {
            if (StringUtils.hasText(name) && name.trim().length() > 64) {
                throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "玩家名称不能超过64个字符");
            }
        }
    }

    private String displayNameAt(List<String> names, int index, String fallback) {
        if (names == null || index >= names.size() || !StringUtils.hasText(names.get(index))) {
            return fallback;
        }
        return names.get(index).trim();
    }

    private GameResponse toResponse(Game game) {
        return GameResponse.builder().id(game.getId()).name(game.getName()).mode(game.getMode())
                .status(game.getStatus()).currentPhase(game.getCurrentPhase()).currentRound(game.getCurrentRound())
                .maxPlayers(game.getMaxPlayers()).publicGame(game.getPublicGame())
                .startTime(game.getStartTime()).finishTime(game.getFinishTime())
                .createTime(game.getCreateTime()).updateTime(game.getUpdateTime()).build();
    }
}
