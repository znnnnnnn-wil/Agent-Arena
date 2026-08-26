package com.zn.agentarena.modules.game.service.impl;

import com.zn.agentarena.dao.entity.Game;
import com.zn.agentarena.dao.entity.GamePlayer;
import com.zn.agentarena.dao.entity.ModelConfig;
import com.zn.agentarena.dao.entity.User;
import com.zn.agentarena.dao.entity.AgentProfile;
import com.zn.agentarena.modules.agent.domain.AgentPersonaPresets;
import com.zn.agentarena.dao.mapper.GameMapper;
import com.zn.agentarena.dao.mapper.GamePlayerMapper;
import com.zn.agentarena.dao.mapper.ModelConfigMapper;
import com.zn.agentarena.dao.mapper.UserMapper;
import com.zn.agentarena.modules.game.dto.request.CreateWerewolfGameRequest;
import com.zn.agentarena.modules.game.dto.request.RematchGameRequest;
import com.zn.agentarena.modules.battle.dto.request.CreateBattleGameRequest;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.model.service.IModelConfigService;
import com.zn.agentarena.modules.spectator.service.GameEventRecorder;
import com.zn.agentarena.modules.werewolf.agent.WerewolfAiGameRunner;
import com.zn.agentarena.modules.battle.agent.BattleAiGameRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zn.agentarena.common.utils.FixedWindowRateLimiter;
import com.zn.agentarena.config.AgentArenaProperties;
import com.zn.agentarena.modules.game.service.GameTurnCoordinator;
import com.zn.agentarena.modules.game.service.GameReadService;
import com.zn.agentarena.modules.agent.service.AgentProfileService;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import com.zn.agentarena.modules.game.service.GameSnapshotService;
import com.zn.agentarena.modules.turing.agent.TuringHuntAiGameRunner;
import com.zn.agentarena.modules.turing.agent.TuringHuntTurnRequirement;
import com.zn.agentarena.modules.turing.domain.TuringHuntActionType;
import com.zn.agentarena.modules.turing.domain.TuringHuntGameState;
import com.zn.agentarena.modules.turing.domain.TuringHuntPhase;
import com.zn.agentarena.modules.turing.domain.TuringHuntPlayerState;
import com.zn.agentarena.modules.turing.dto.request.CreateTuringHuntGameRequest;
import com.zn.agentarena.modules.turing.dto.request.TuringHuntHumanActionRequest;
import com.zn.agentarena.modules.turing.engine.TuringHuntGameEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 游戏服务单元测试。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@DisplayName("游戏服务单元测试")
class GameServiceImplTest {

    private GameMapper gameMapper;
    private GamePlayerMapper gamePlayerMapper;
    private ModelConfigMapper modelConfigMapper;
    private UserMapper userMapper;
    private AgentProfileService agentProfileService;
    private GameSnapshotService snapshotService;
    private GameEventRecorder eventRecorder;
    private TuringHuntAiGameRunner turingRunner;
    private GameServiceImpl gameService;

    @BeforeEach
    void setUp() {
        gameMapper = mock(GameMapper.class);
        gamePlayerMapper = mock(GamePlayerMapper.class);
        modelConfigMapper = mock(ModelConfigMapper.class);
        userMapper = mock(UserMapper.class);
        agentProfileService = mock(AgentProfileService.class);
        List<AgentProfile> builtIns = new ArrayList<>();
        for (int index = 0; index < AgentPersonaPresets.ALL.size(); index++) {
            var preset = AgentPersonaPresets.ALL.get(index);
            builtIns.add(AgentProfile.builder().id(100L + index).userId(1L).name(preset.name())
                    .description(preset.description()).personality(Map.of("code", preset.code()))
                    .deleted(false).build());
        }
        when(agentProfileService.ensureBuiltInProfiles(anyLong())).thenReturn(builtIns);
        when(agentProfileService.publicSnapshot(any(AgentProfile.class))).thenAnswer(invocation -> {
            AgentProfile profile = invocation.getArgument(0);
            return Map.of("agentProfileName", profile.getName(), "agentProfileCode",
                    profile.getPersonality().get("code"), "agentTraits", List.of("内置"));
        });
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        eventRecorder = mock(GameEventRecorder.class);
        snapshotService = mock(GameSnapshotService.class);
        turingRunner = mock(TuringHuntAiGameRunner.class);
        GameReadService readService = new GameReadService(gameMapper, gamePlayerMapper, modelConfigMapper,
                userMapper, snapshotService, eventRecorder);
        gameService = new GameServiceImpl(gameMapper, gamePlayerMapper, modelConfigMapper, userMapper,
                mock(IModelConfigService.class), agentProfileService,
                mock(WerewolfAiGameRunner.class), mock(BattleAiGameRunner.class),
                turingRunner, eventRecorder,
                snapshotService, new GameTurnCoordinator(),
                transactions, new ObjectMapper(), new FixedWindowRateLimiter(),
                new AgentArenaProperties(null, null, null), readService);
    }

    @Test
    @DisplayName("createWerewolfGame: 应创建标准8人狼人杀席位")
    void testCreateWerewolfGameSuccess() {
        when(userMapper.selectOneById(1L)).thenReturn(User.builder().id(1L).build());
        when(modelConfigMapper.selectOneById(anyLong())).thenAnswer(invocation -> ModelConfig.builder()
                .id(invocation.getArgument(0)).userId(1L).provider(ModelProvider.OPENAI)
                .modelName("test").enabled(true).build());
        doAnswer(invocation -> {
            Game game = invocation.getArgument(0);
            game.setId(100L);
            return 1;
        }).when(gameMapper).insertSelective(any(Game.class));

        CreateWerewolfGameRequest request = new CreateWerewolfGameRequest();
        request.setName("测试狼人杀");
        request.setPublicGame(true);
        request.setModelConfigIds(List.of(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L));
        request.setPlayerDisplayNames(List.of("Alpha", "Bravo", "Charlie", "Delta",
                "Echo", "Foxtrot", "Golf", "Hotel"));

        var response = gameService.createWerewolfGame(1L, request);

        assertEquals(100L, response.getId());
        ArgumentCaptor<GamePlayer> captor = ArgumentCaptor.forClass(GamePlayer.class);
        verify(gamePlayerMapper, times(8)).insertSelective(captor.capture());
        assertEquals(8, captor.getAllValues().size());
        assertEquals("Alpha", captor.getAllValues().get(0).getDisplayName());
        assertEquals("Hotel", captor.getAllValues().get(7).getDisplayName());
        assertEquals(8, captor.getAllValues().stream().map(GamePlayer::getAgentProfileId).distinct().count());
        assertEquals("激进领袖", captor.getAllValues().get(0).getPublicState().get("agentProfileName"));
    }

    @Test
    @DisplayName("createWerewolfGame: 应允许模型与本地策略混合分配")
    void testCreateWerewolfGameWithMixedSeats() {
        when(userMapper.selectOneById(1L)).thenReturn(User.builder().id(1L).build());
        when(modelConfigMapper.selectOneById(1L)).thenReturn(ModelConfig.builder()
                .id(1L).userId(1L).provider(ModelProvider.OPENAI)
                .modelName("test").enabled(true).deleted(false).build());
        doAnswer(invocation -> {
            Game game = invocation.getArgument(0);
            game.setId(101L);
            return 1;
        }).when(gameMapper).insertSelective(any(Game.class));
        CreateWerewolfGameRequest request = new CreateWerewolfGameRequest();
        request.setName("混合席位");
        request.setPublicGame(false);
        request.setModelConfigIds(Arrays.asList(1L, null, 1L, null, null, 1L, null, null));

        gameService.createWerewolfGame(1L, request);

        ArgumentCaptor<GamePlayer> captor = ArgumentCaptor.forClass(GamePlayer.class);
        verify(gamePlayerMapper, times(8)).insertSelective(captor.capture());
        assertEquals(1L, captor.getAllValues().get(0).getModelConfigId());
        assertNull(captor.getAllValues().get(1).getModelConfigId());
    }

    @Test
    @DisplayName("createTuringHuntGame: 应创建一名真人和五名 AI，且真人不绑定模型")
    void createTuringHuntGameCreatesFixedHumanAiLineup() {
        when(userMapper.selectOneById(1L)).thenReturn(User.builder().id(1L).build());
        when(modelConfigMapper.selectOneById(77L)).thenReturn(ModelConfig.builder().id(77L).userId(1L)
                .provider(ModelProvider.OPENAI).modelName("gpt-test").enabled(true).deleted(false).build());
        doAnswer(invocation -> {
            Game game = invocation.getArgument(0);
            game.setId(500L);
            return 1;
        }).when(gameMapper).insertSelective(any(Game.class));
        CreateTuringHuntGameRequest request = new CreateTuringHuntGameRequest();
        request.setName("图灵实验");
        request.setPublicGame(true);
        request.setHumanSeatNo(2);
        request.setPlayerDisplayNames(Map.of(1, "Alpha", 2, "Beta", 3, "Gamma",
                4, "Delta", 5, "Echo", 6, "Foxtrot"));
        request.setSeatModelConfigIds(Map.of(1, 77L, 3, 77L, 4, 77L, 5, 77L, 6, 77L));

        var response = gameService.createTuringHuntGame(1L, request);

        assertEquals(500L, response.getId());
        assertEquals(com.zn.agentarena.common.enums.GameMode.TURING_HUNT, response.getMode());
        ArgumentCaptor<GamePlayer> captor = ArgumentCaptor.forClass(GamePlayer.class);
        verify(gamePlayerMapper, times(6)).insertSelective(captor.capture());
        List<GamePlayer> seats = captor.getAllValues();
        assertEquals(1, seats.stream().filter(player -> player.getPlayerType()
                == com.zn.agentarena.common.enums.PlayerType.HUMAN).count());
        assertEquals(5, seats.stream().filter(player -> player.getPlayerType()
                == com.zn.agentarena.common.enums.PlayerType.AI).count());
        GamePlayer human = seats.get(1);
        assertEquals(1L, human.getUserId());
        assertNull(human.getModelConfigId());
        assertEquals("HUMAN", human.getRoleCode());
        assertEquals("Beta", human.getDisplayName());
        assertTrue(seats.stream().filter(player -> player.getPlayerType()
                        == com.zn.agentarena.common.enums.PlayerType.AI)
                .allMatch(player -> Long.valueOf(77L).equals(player.getModelConfigId())));
    }

    @Test
    @DisplayName("createTuringHuntGame: 应拒绝给真人席位注入模型")
    void createTuringHuntGameRejectsHumanModelBinding() {
        when(userMapper.selectOneById(1L)).thenReturn(User.builder().id(1L).build());
        CreateTuringHuntGameRequest request = new CreateTuringHuntGameRequest();
        request.setName("非法图灵实验");
        request.setPublicGame(true);
        request.setHumanSeatNo(2);
        request.setSeatModelConfigIds(Map.of(2, 77L));

        CustomException exception = assertThrows(CustomException.class,
                () -> gameService.createTuringHuntGame(1L, request));

        assertEquals(ResultCodeEnum.PARAMS_ERROR, exception.getResultCodeEnum());
        verify(gameMapper, never()).insertSelective(any());
    }

    @Test
    @DisplayName("createTuringHuntGame: 应拒绝其他用户的模型配置")
    void createTuringHuntGameRejectsForeignModelConfiguration() {
        when(userMapper.selectOneById(1L)).thenReturn(User.builder().id(1L).build());
        when(modelConfigMapper.selectOneById(77L)).thenReturn(ModelConfig.builder().id(77L).userId(2L)
                .provider(ModelProvider.OPENAI).modelName("foreign").enabled(true).deleted(false).build());
        CreateTuringHuntGameRequest request = new CreateTuringHuntGameRequest();
        request.setName("非法模型实验");
        request.setPublicGame(true);
        request.setHumanSeatNo(2);
        request.setSeatModelConfigIds(Map.of(1, 77L));

        CustomException exception = assertThrows(CustomException.class,
                () -> gameService.createTuringHuntGame(1L, request));

        assertEquals(ResultCodeEnum.MODEL_CONFIG_NOT_FOUND, exception.getResultCodeEnum());
        verify(gameMapper, never()).insertSelective(any());
    }

    @Test
    @DisplayName("createTuringHuntGame: 应拒绝 1 到 6 之外的映射座位")
    void createTuringHuntGameRejectsIllegalSeatMapKey() {
        when(userMapper.selectOneById(1L)).thenReturn(User.builder().id(1L).build());
        CreateTuringHuntGameRequest request = new CreateTuringHuntGameRequest();
        request.setName("非法座位实验");
        request.setPublicGame(true);
        request.setHumanSeatNo(2);
        request.setPlayerDisplayNames(Map.of(7, "越界席位"));

        CustomException exception = assertThrows(CustomException.class,
                () -> gameService.createTuringHuntGame(1L, request));

        assertEquals(ResultCodeEnum.PARAMS_ERROR, exception.getResultCodeEnum());
        verify(gameMapper, never()).insertSelective(any());
    }

    @Test
    @DisplayName("submitTuringHumanAction: 应只接受当前真人一次并保持回答私密")
    void submitTuringHumanActionAcceptsOnceAndKeepsSubmissionPrivate() {
        Game game = Game.builder().id(500L).ownerUserId(1L).name("图灵实验")
                .mode(com.zn.agentarena.common.enums.GameMode.TURING_HUNT)
                .status(com.zn.agentarena.common.enums.GameStatus.RUNNING)
                .currentPhase(TuringHuntPhase.ANSWER_COLLECTION.name()).currentRound(1)
                .maxPlayers(6).publicGame(true).ruleConfig(Map.of("humanSeatNo", 1, "maxRounds", 3)).build();
        List<GamePlayer> players = turingPlayers(500L, 1);
        TuringHuntGameState initial = TuringHuntGameState.builder().gameId(500L).roundNo(1).maxRounds(3)
                .phase(TuringHuntPhase.ANSWER_COLLECTION).humanPlayerId(101L).randomSeed(9L)
                .players(new ArrayList<>(players.stream().map(player -> TuringHuntPlayerState.builder()
                        .playerId(player.getId()).seatNo(player.getSeatNo()).displayName(player.getDisplayName())
                        .human(player.getPlayerType() == com.zn.agentarena.common.enums.PlayerType.HUMAN)
                        .alive(true).build()).toList())).build();
        AtomicReference<TuringHuntGameState> stored = new AtomicReference<>(initial);
        when(gameMapper.selectOneById(500L)).thenReturn(game);
        when(gameMapper.update(any(Game.class))).thenReturn(1);
        when(gamePlayerMapper.selectListByQuery(any())).thenReturn(players);
        when(snapshotService.loadLatestTuringSnapshot(500L)).thenAnswer(ignored -> stored.get());
        doAnswer(invocation -> { stored.set(invocation.getArgument(0)); return null; })
                .when(snapshotService).saveTuringSnapshot(any(TuringHuntGameState.class));
        TuringHuntGameEngine inspectionEngine = new TuringHuntGameEngine();
        when(turingRunner.inspect(any(TuringHuntGameState.class))).thenAnswer(invocation -> {
            TuringHuntGameState state = invocation.getArgument(0);
            TuringHuntActionType type = inspectionEngine.requiredActionType(state);
            Long actorId = inspectionEngine.requiredActorPlayerId(state);
            TuringHuntPlayerState actor = state.findPlayer(actorId);
            return new TuringHuntTurnRequirement(type, actorId, actor != null && actor.isHuman());
        });
        TuringHuntHumanActionRequest request = new TuringHuntHumanActionRequest();
        request.setAction("ANSWER");
        request.setAnswer("我会先界定问题，再给出一个简短例子，因为具体场景更容易公开复核，也能避免误解。");

        var response = gameService.submitTuringHumanAction(1L, 500L, request);

        assertEquals(Boolean.FALSE, response.getHumanActionRequired());
        assertTrue(stored.get().getAnswersByPlayerId().containsKey(101L));
        ArgumentCaptor<EngineEvent> eventCaptor = ArgumentCaptor.forClass(EngineEvent.class);
        verify(eventRecorder).record(eq(500L), eq(1), eq(TuringHuntPhase.ANSWER_COLLECTION.name()),
                eq(1L), eventCaptor.capture());
        assertEquals("TURING_ANSWER_SUBMITTED", eventCaptor.getValue().type());
        assertEquals(Map.of("submitted", true), eventCaptor.getValue().publicPayload());
        assertFalse(eventCaptor.getValue().publicPayload().containsKey("answer"));

        CustomException duplicate = assertThrows(CustomException.class,
                () -> gameService.submitTuringHumanAction(1L, 500L, request));
        assertEquals(ResultCodeEnum.GAME_PHASE_MISMATCH, duplicate.getResultCodeEnum());
        verify(eventRecorder, times(1)).record(anyLong(), anyInt(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("submitTuringHumanAction: 应拒绝客户端注入内部玩家 ID")
    void submitTuringHumanActionRejectsInjectedPlayerId() {
        Game game = Game.builder().id(500L).ownerUserId(1L)
                .mode(com.zn.agentarena.common.enums.GameMode.TURING_HUNT)
                .status(com.zn.agentarena.common.enums.GameStatus.RUNNING).build();
        when(gameMapper.selectOneById(500L)).thenReturn(game);
        TuringHuntHumanActionRequest request = new TuringHuntHumanActionRequest();
        request.setAction("ANSWER");
        request.setAnswer("回答");
        request.setPlayerId(999L);

        CustomException exception = assertThrows(CustomException.class,
                () -> gameService.submitTuringHumanAction(1L, 500L, request));

        assertEquals(ResultCodeEnum.PARAMS_ERROR, exception.getResultCodeEnum());
        verify(snapshotService, never()).loadLatestTuringSnapshot(anyLong());
    }

    @Test
    @DisplayName("submitTuringHumanAction: 应拒绝非创建者代替真人行动")
    void submitTuringHumanActionRejectsNonOwner() {
        Game game = Game.builder().id(500L).ownerUserId(1L)
                .mode(com.zn.agentarena.common.enums.GameMode.TURING_HUNT)
                .status(com.zn.agentarena.common.enums.GameStatus.RUNNING).build();
        when(gameMapper.selectOneById(500L)).thenReturn(game);
        TuringHuntHumanActionRequest request = new TuringHuntHumanActionRequest();
        request.setAction("ANSWER");
        request.setAnswer("试图越权提交的回答");

        CustomException exception = assertThrows(CustomException.class,
                () -> gameService.submitTuringHumanAction(2L, 500L, request));

        assertEquals(ResultCodeEnum.GAME_NOT_FOUND, exception.getResultCodeEnum());
        verify(snapshotService, never()).loadLatestTuringSnapshot(anyLong());
        verify(eventRecorder, never()).record(anyLong(), anyInt(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("createBattleGame: 模型席位数量必须与玩家人数一致")
    void testCreateBattleGameRejectsPartialSeatList() {
        when(userMapper.selectOneById(1L)).thenReturn(User.builder().id(1L).build());
        CreateBattleGameRequest request = new CreateBattleGameRequest();
        request.setName("错误席位");
        request.setPublicGame(true);
        request.setPlayerCount(8);
        request.setModelConfigIds(List.of(1L));

        CustomException exception = assertThrows(CustomException.class,
                () -> gameService.createBattleGame(1L, request));

        assertEquals(ResultCodeEnum.PARAMS_ERROR, exception.getResultCodeEnum());
        verify(gameMapper, never()).insertSelective(any());
    }

    @Test
    @DisplayName("startGame: 等待中的游戏应初始化为运行状态")
    void testStartGameSuccess() {
        Game game = Game.builder().id(100L).ownerUserId(1L)
                .mode(com.zn.agentarena.common.enums.GameMode.WEREWOLF)
                .status(com.zn.agentarena.common.enums.GameStatus.WAITING)
                .name("测试").build();
        when(gameMapper.selectOneById(100L)).thenReturn(game);
        when(gamePlayerMapper.selectListByQuery(any())).thenReturn(players());
        when(gameMapper.update(any(Game.class))).thenReturn(1);
        when(gamePlayerMapper.update(any(GamePlayer.class))).thenReturn(1);

        var response = gameService.startGame(1L, 100L);

        assertEquals(com.zn.agentarena.common.enums.GameStatus.RUNNING, response.getStatus());
        assertEquals("NIGHT_WOLF", response.getCurrentPhase());
        verify(gameMapper).update(game);
        verify(gamePlayerMapper, times(8)).update(any(GamePlayer.class));
    }

    @Test
    @DisplayName("listGames: 应返回当前用户最近更新的真实对局")
    void testListGames() {
        when(userMapper.selectOneById(1L)).thenReturn(User.builder().id(1L).build());
        when(gameMapper.selectListByQuery(any())).thenReturn(List.of(
                Game.builder().id(101L).ownerUserId(1L).name("最近对局")
                        .mode(com.zn.agentarena.common.enums.GameMode.BATTLE_ROYALE)
                        .status(com.zn.agentarena.common.enums.GameStatus.RUNNING).currentRound(4).build()));

        var games = gameService.listGames(1L);

        assertEquals(1, games.size());
        assertEquals(101L, games.get(0).getId());
        assertEquals(4, games.get(0).getCurrentRound());
        verify(gameMapper).selectListByQuery(any());
    }

    @Test
    @DisplayName("rematch: 应复用模型和人格但清空运行态与私有状态")
    void rematchCopiesConfigurationWithoutRuntimeState() {
        Game source = Game.builder().id(100L).ownerUserId(1L).name("原对局")
                .mode(com.zn.agentarena.common.enums.GameMode.WEREWOLF)
                .status(com.zn.agentarena.common.enums.GameStatus.FINISHED)
                .maxPlayers(8).publicGame(true)
                .ruleConfig(Map.of("roles", List.of("WEREWOLF", "WEREWOLF", "SEER", "WITCH",
                        "GUARD", "VILLAGER", "VILLAGER", "VILLAGER"))).build();
        List<GamePlayer> sourcePlayers = players();
        sourcePlayers.forEach(player -> {
            player.setStatus(com.zn.agentarena.modules.game.domain.PlayerStatus.ELIMINATED);
            player.setPrivateState(Map.of("runtimeSecret", "must-not-copy"));
        });
        sourcePlayers.get(0).setAgentProfileId(7L);
        sourcePlayers.get(0).setPublicState(Map.of(
                "agentProfileName", "冷静分析师",
                "agentTraits", List.of("冷静", "怀疑"),
                "agentSpeakingStyle", "先给证据",
                "agentRiskPreference", "CAUTIOUS",
                "agentAvatarEmoji", "AI",
                "agentAccentColor", "#8B7CFF",
                "runtimeHealth", 0));
        when(gameMapper.selectOneById(100L)).thenReturn(source);
        when(gamePlayerMapper.selectListByQuery(any())).thenReturn(sourcePlayers);
        doAnswer(invocation -> {
            Game game = invocation.getArgument(0);
            game.setId(200L);
            return 1;
        }).when(gameMapper).insertSelective(any(Game.class));

        RematchGameRequest request = new RematchGameRequest();
        request.setName("同阵容重赛");
        var response = gameService.rematch(1L, 100L, request);

        assertEquals(200L, response.getId());
        assertEquals(com.zn.agentarena.common.enums.GameStatus.WAITING, response.getStatus());
        ArgumentCaptor<GamePlayer> playersCaptor = ArgumentCaptor.forClass(GamePlayer.class);
        verify(gamePlayerMapper, times(8)).insertSelective(playersCaptor.capture());
        List<GamePlayer> rematchPlayers = playersCaptor.getAllValues();
        assertEquals(1L, rematchPlayers.get(0).getModelConfigId());
        assertEquals(7L, rematchPlayers.get(0).getAgentProfileId());
        assertEquals(com.zn.agentarena.modules.game.domain.PlayerStatus.WAITING,
                rematchPlayers.get(0).getStatus());
        assertNull(rematchPlayers.get(0).getPrivateState());
        assertEquals("冷静分析师", rematchPlayers.get(0).getPublicState().get("agentProfileName"));
        assertFalse(rematchPlayers.get(0).getPublicState().containsKey("runtimeHealth"));

        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameMapper).insertSelective(gameCaptor.capture());
        assertEquals(rematchPlayers.stream().map(GamePlayer::getRoleCode).toList(),
                gameCaptor.getValue().getRuleConfig().get("roles"));
    }

    private List<GamePlayer> players() {
        List<String> roles = List.of("WEREWOLF", "WEREWOLF", "SEER", "WITCH",
                "GUARD", "VILLAGER", "VILLAGER", "VILLAGER");
        List<GamePlayer> players = new ArrayList<>();
        for (int i = 0; i < roles.size(); i++) {
            players.add(GamePlayer.builder().id((long) (i + 1)).gameId(100L).seatNo(i + 1)
                    .modelConfigId(1L).roleCode(roles.get(i)).displayName((i + 1) + "号玩家").build());
        }
        return players;
    }

    private List<GamePlayer> turingPlayers(Long gameId, int humanSeatNo) {
        List<GamePlayer> players = new ArrayList<>();
        for (int seatNo = 1; seatNo <= 6; seatNo++) {
            boolean human = seatNo == humanSeatNo;
            players.add(GamePlayer.builder().id(100L + seatNo).gameId(gameId).seatNo(seatNo)
                    .playerType(human ? com.zn.agentarena.common.enums.PlayerType.HUMAN
                            : com.zn.agentarena.common.enums.PlayerType.AI)
                    .userId(human ? 1L : null).modelConfigId(null)
                    .roleCode(human ? "HUMAN" : "AI").displayName(seatNo + "号玩家")
                    .status(com.zn.agentarena.modules.game.domain.PlayerStatus.ALIVE).build());
        }
        return players;
    }
}
