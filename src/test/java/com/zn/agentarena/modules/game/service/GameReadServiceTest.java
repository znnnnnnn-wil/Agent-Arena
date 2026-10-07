package com.zn.agentarena.modules.game.service;

import com.zn.agentarena.common.enums.GameMode;
import com.zn.agentarena.common.enums.GameStatus;
import com.zn.agentarena.common.enums.PlayerType;
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
import com.zn.agentarena.modules.game.domain.PlayerStatus;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.spectator.service.GameEventRecorder;
import com.zn.agentarena.modules.werewolf.domain.WerewolfGameState;
import com.zn.agentarena.modules.werewolf.domain.WerewolfPlayerState;
import com.zn.agentarena.modules.werewolf.domain.WerewolfPhase;
import com.zn.agentarena.modules.werewolf.domain.WerewolfRole;
import com.zn.agentarena.modules.turing.domain.TuringHuntFinishedReason;
import com.zn.agentarena.modules.turing.domain.TuringHuntGameState;
import com.zn.agentarena.modules.turing.domain.TuringHuntPhase;
import com.zn.agentarena.modules.turing.domain.TuringHuntPlayerState;
import com.zn.agentarena.modules.turing.domain.TuringHuntWinner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameReadServiceTest {

    private GameMapper gameMapper;
    private GamePlayerMapper playerMapper;
    private ModelConfigMapper modelConfigMapper;
    private GameSnapshotService snapshotService;
    private GameEventRecorder eventRecorder;
    private GameReadService readService;

    @BeforeEach
    void setUp() {
        gameMapper = mock(GameMapper.class);
        playerMapper = mock(GamePlayerMapper.class);
        modelConfigMapper = mock(ModelConfigMapper.class);
        snapshotService = mock(GameSnapshotService.class);
        eventRecorder = mock(GameEventRecorder.class);
        readService = new GameReadService(gameMapper, playerMapper, modelConfigMapper,
                mock(UserMapper.class), snapshotService, eventRecorder);
    }

    @Test
    void summaryUsesCompleteCountsInsteadOfTheRecentEventWindow() {
        when(gameMapper.selectOneById(9L)).thenReturn(Game.builder().id(9L).ownerUserId(1L)
                .mode(GameMode.WEREWOLF).status(GameStatus.FINISHED).build());
        when(eventRecorder.listAllEvents(9L)).thenReturn(List.of(GameEvent.builder()
                .eventType("GAME_FINISHED").publicPayload(Map.of("winner", "VILLAGER")).build()));
        when(eventRecorder.countEvents(9L)).thenReturn(6_001L);
        when(eventRecorder.countEliminations(9L)).thenReturn(7L);

        var summary = readService.getOwnedSummary(1L, 9L);

        assertEquals(6_001L, summary.getEventCount());
        assertEquals(7L, summary.getEliminationCount());
        assertEquals("VILLAGER", summary.getWinner());
    }

    @Test
    void publicWerewolfViewHidesRoleModelAndPrivateSkillsUntilFinished() {
        Game game = Game.builder().id(9L).ownerUserId(1L).publicGame(true)
                .mode(GameMode.WEREWOLF).status(GameStatus.RUNNING).build();
        GamePlayer player = GamePlayer.builder().id(11L).gameId(9L).seatNo(1)
                .playerType(PlayerType.AI).modelConfigId(77L).displayName("一号")
                .status(PlayerStatus.ALIVE).roleCode("WITCH").build();
        WerewolfPlayerState state = WerewolfPlayerState.builder().playerId(11L).seatNo(1)
                .role(WerewolfRole.WITCH).alive(true).antidoteAvailable(true)
                .poisonAvailable(true).seerChecks(Map.of(12L, true)).build();

        when(gameMapper.selectOneById(9L)).thenReturn(game);
        when(playerMapper.selectListByQuery(any())).thenReturn(List.of(player));
        when(modelConfigMapper.selectListByQuery(any())).thenReturn(List.of(ModelConfig.builder().id(77L)
                .name("推理模型").provider(ModelProvider.QWEN).modelName("qwen-plus").build()));
        when(snapshotService.loadLatestWerewolfSnapshot(9L))
                .thenReturn(WerewolfGameState.builder().gameId(9L).players(List.of(state)).build());

        var response = readService.getPublicPlayers(9L).get(0);

        assertEquals(ModelProvider.QWEN, response.getModelProvider());
        assertEquals("qwen-plus", response.getModelName());
        assertEquals("推理模型", response.getModelDisplayName());
        assertNull(response.getRoleCode());
        assertNull(response.getAntidoteAvailable());
        assertNull(response.getPoisonAvailable());
        assertNull(response.getSeerChecks());
        assertEquals("激进领袖", response.getAgentProfileName());
        assertEquals("AGGRESSIVE_LEADER", response.getAgentProfileCode());
    }

    @Test
    void localStrategyPlayerCanBeReadWithoutModelConfiguration() {
        Game game = Game.builder().id(32L).ownerUserId(6L).publicGame(false)
                .mode(GameMode.WEREWOLF).status(GameStatus.RUNNING).build();
        GamePlayer player = GamePlayer.builder().id(301L).gameId(32L).seatNo(1)
                .playerType(PlayerType.AI).modelConfigId(null).displayName("规则 AI")
                .status(PlayerStatus.ALIVE).roleCode("VILLAGER").build();
        when(gameMapper.selectOneById(32L)).thenReturn(game);
        when(playerMapper.selectListByQuery(any())).thenReturn(List.of(player));

        var response = readService.getOwnedPlayers(6L, 32L).get(0);

        assertTrue(response.getLocalStrategy());
        assertNull(response.getModelProvider());
        assertNull(response.getModelName());
    }

    @Test
    void publicNightProjectionHidesCurrentActorAndModel() {
        Game game = Game.builder().id(9L).ownerUserId(1L).publicGame(true)
                .mode(GameMode.WEREWOLF).status(GameStatus.RUNNING).currentPhase("NIGHT_WOLF").build();
        GamePlayer wolf = GamePlayer.builder().id(11L).gameId(9L).seatNo(1).displayName("夜行者")
                .modelConfigId(77L).status(PlayerStatus.ALIVE).build();
        WerewolfPlayerState wolfState = WerewolfPlayerState.builder().playerId(11L).seatNo(1)
                .role(WerewolfRole.WEREWOLF).alive(true).build();
        when(gameMapper.selectOneById(9L)).thenReturn(game);
        when(playerMapper.selectListByQuery(any())).thenReturn(List.of(wolf));
        when(snapshotService.loadLatestWerewolfSnapshot(9L)).thenReturn(WerewolfGameState.builder()
                .gameId(9L).phase(WerewolfPhase.NIGHT_WOLF).players(List.of(wolfState)).build());

        var response = readService.getPublic(9L);

        assertNull(response.getCurrentActorPlayerId());
        assertEquals("夜间角色正在行动", response.getCurrentActorName());
        assertEquals("NIGHT_WOLF", response.getCurrentActionType());
        assertNull(response.getCurrentActorModelName());
        assertNull(response.getCurrentActorPersonaName());
    }

    @Test
    void ownerDiscussionProjectionUsesSnapshotDiscussionIndex() {
        Game game = Game.builder().id(9L).ownerUserId(1L).publicGame(false)
                .mode(GameMode.WEREWOLF).status(GameStatus.RUNNING).currentPhase("DAY_DISCUSSION").build();
        GamePlayer first = GamePlayer.builder().id(11L).gameId(9L).seatNo(1).displayName("一号")
                .status(PlayerStatus.ALIVE).build();
        GamePlayer second = GamePlayer.builder().id(12L).gameId(9L).seatNo(2).displayName("二号")
                .status(PlayerStatus.ALIVE).build();
        WerewolfPlayerState firstState = WerewolfPlayerState.builder().playerId(11L).seatNo(1)
                .role(WerewolfRole.VILLAGER).alive(true).build();
        WerewolfPlayerState secondState = WerewolfPlayerState.builder().playerId(12L).seatNo(2)
                .role(WerewolfRole.SEER).alive(true).build();
        when(gameMapper.selectOneById(9L)).thenReturn(game);
        when(playerMapper.selectListByQuery(any())).thenReturn(List.of(first, second));
        when(snapshotService.loadLatestWerewolfSnapshot(9L)).thenReturn(WerewolfGameState.builder()
                .gameId(9L).phase(WerewolfPhase.DAY_DISCUSSION).discussionIndex(1)
                .players(List.of(firstState, secondState)).build());

        var response = readService.getOwned(1L, 9L);

        assertEquals(12L, response.getCurrentActorPlayerId());
        assertEquals("二号", response.getCurrentActorName());
        assertEquals(Boolean.TRUE, response.getCurrentActorLocalStrategy());
    }

    @Test
    void battleProjectionUsesRotatedRoundOrder() {
        Game game = Game.builder().id(20L).ownerUserId(1L).publicGame(false)
                .mode(GameMode.BATTLE_ROYALE).status(GameStatus.RUNNING).currentPhase("ROUND").build();
        GamePlayer first = GamePlayer.builder().id(21L).gameId(20L).seatNo(1).displayName("Alpha")
                .status(PlayerStatus.ALIVE).build();
        GamePlayer second = GamePlayer.builder().id(22L).gameId(20L).seatNo(2).displayName("Bravo")
                .status(PlayerStatus.ALIVE).build();
        when(gameMapper.selectOneById(20L)).thenReturn(game);
        when(playerMapper.selectListByQuery(any())).thenReturn(List.of(first, second));
        when(snapshotService.loadLatestBattleSnapshot(20L)).thenReturn(BattleRoyaleGameState.builder()
                .gameId(20L).roundNo(2).actionsInRound(1).players(List.of(
                        BattlePlayerState.builder().playerId(21L).alive(true).build(),
                        BattlePlayerState.builder().playerId(22L).alive(true).build())).build());

        var response = readService.getOwned(1L, 20L);

        assertEquals(21L, response.getCurrentActorPlayerId());
        assertEquals("Alpha", response.getCurrentActorName());
        org.junit.jupiter.api.Assertions.assertNotNull(response.getBattleWorld());
        assertEquals("BATTLE_ACTION", response.getCurrentActionType());
    }

    @Test
    void finishedGameHasNoCurrentActorAndPlayerDtoHasNoSecretFields() {
        Game game = Game.builder().id(30L).ownerUserId(1L).publicGame(false)
                .mode(GameMode.WEREWOLF).status(GameStatus.FINISHED).currentPhase("FINISHED").build();
        when(gameMapper.selectOneById(30L)).thenReturn(game);

        var response = readService.getOwned(1L, 30L);
        List<String> fieldNames = Arrays.stream(com.zn.agentarena.modules.game.dto.response.GamePlayerResponse.class
                .getDeclaredFields()).map(java.lang.reflect.Field::getName).toList();

        assertNull(response.getCurrentActorPlayerId());
        assertNull(response.getCurrentActorName());
        assertFalse(fieldNames.contains("apiKey"));
        assertFalse(fieldNames.contains("encryptedApiKey"));
        assertFalse(fieldNames.contains("baseUrl"));
        assertFalse(fieldNames.contains("modelConfigId"));
        assertFalse(fieldNames.contains("agentProfileId"));
    }

    @Test
    void publicReplayUsesOnlyRedactedPayload() {
        when(gameMapper.selectOneById(9L)).thenReturn(Game.builder().id(9L).publicGame(true).build());
        when(eventRecorder.listEvents(anyLong(), anyLong(), anyInt())).thenReturn(List.of(
                GameEvent.builder().sequenceNo(1L).eventType("AI_DECISION")
                        .actorPlayerId(11L)
                        .payload(Map.of("secret", "night target"))
                        .publicPayload(Map.of("status", "submitted")).build()));

        var response = readService.getPublicReplay(9L, 0, 100).get(0);

        assertEquals(Map.of("status", "submitted"), response.getPayload());
        assertNull(response.getActorPlayerId());
    }

    @Test
    void turingRunningViewsHideIdsIdentitiesModelsAndSecretReplayPayloads() {
        Game game = Game.builder().id(42L).ownerUserId(1L).publicGame(true)
                .mode(GameMode.TURING_HUNT).status(GameStatus.RUNNING)
                .currentPhase(TuringHuntPhase.ANSWER_COLLECTION.name()).currentRound(1)
                .ruleConfig(Map.of("humanSeatNo", 1, "maxRounds", 3)).build();
        GamePlayer human = GamePlayer.builder().id(101L).gameId(42L).seatNo(1)
                .playerType(PlayerType.HUMAN).userId(1L).displayName("一号")
                .status(PlayerStatus.ALIVE).roleCode("HUMAN").build();
        GamePlayer ai = GamePlayer.builder().id(102L).gameId(42L).seatNo(2)
                .playerType(PlayerType.AI).modelConfigId(77L).displayName("二号")
                .status(PlayerStatus.ALIVE).roleCode("AI").build();
        TuringHuntGameState state = TuringHuntGameState.builder().gameId(42L).roundNo(1).maxRounds(3)
                .phase(TuringHuntPhase.ANSWER_COLLECTION).humanPlayerId(101L)
                .currentQuestion("如果记忆可以出售，你会保留哪一段？")
                .players(List.of(
                        TuringHuntPlayerState.builder().playerId(101L).seatNo(1).displayName("一号")
                                .human(true).alive(true).build(),
                        TuringHuntPlayerState.builder().playerId(102L).seatNo(2).displayName("二号")
                                .human(false).alive(true).build())).build();
        when(gameMapper.selectOneById(42L)).thenReturn(game);
        when(playerMapper.selectListByQuery(any())).thenReturn(List.of(human, ai));
        when(modelConfigMapper.selectListByQuery(any())).thenReturn(List.of(ModelConfig.builder().id(77L)
                .name("隐藏模型").provider(ModelProvider.QWEN).modelName("qwen-plus").build()));
        when(snapshotService.loadLatestTuringSnapshot(42L)).thenReturn(state);
        when(eventRecorder.listEvents(anyLong(), anyLong(), anyInt())).thenReturn(List.of(
                GameEvent.builder().sequenceNo(1L).eventType("TURING_ANSWER_SUBMITTED")
                        .actorPlayerId(101L).payload(Map.of("actorPlayerId", 101L, "answer", "秘密回答"))
                        .publicPayload(Map.of("submitted", true)).build()));

        var ownerPlayers = readService.getOwnedPlayers(1L, 42L);
        var ownerGame = readService.getOwned(1L, 42L);
        var publicGame = readService.getPublic(42L);
        var ownedReplay = readService.getOwnedReplay(1L, 42L, 0, 100).get(0);

        assertTrue(ownerPlayers.stream().allMatch(player -> player.getId() == null
                && player.getPlayerType() == null && player.getRoleCode() == null
                && player.getModelName() == null && player.getModelDisplayName() == null
                && player.getLocalStrategy() == null));
        assertEquals(1, ownerGame.getSelfSeatNo());
        assertEquals(Boolean.TRUE, ownerGame.getHumanActionRequired());
        assertEquals("ANSWER", ownerGame.getRequiredHumanAction());
        assertNull(ownerGame.getCurrentActorPlayerId());
        assertNull(ownerGame.getCurrentActorModelName());
        assertEquals("等待本阶段提交", ownerGame.getCurrentActorName());
        assertNull(ownerGame.getCurrentActorPersonaName());
        assertEquals(0, ownerGame.getSubmissionCompletedCount());
        assertEquals(2, ownerGame.getSubmissionRequiredCount());
        assertNull(publicGame.getSelfSeatNo());
        assertNull(publicGame.getHumanActionRequired());
        assertEquals("等待本阶段提交", publicGame.getCurrentActorName());
        assertEquals(ownerGame.getSubmissionCompletedCount(), publicGame.getSubmissionCompletedCount());
        assertEquals(ownerGame.getSubmissionRequiredCount(), publicGame.getSubmissionRequiredCount());
        assertEquals(state.getCurrentQuestion(), publicGame.getCurrentQuestion());
        assertEquals(Map.of("submitted", true), ownedReplay.getPayload());
        assertNull(ownedReplay.getActorPlayerId());
    }

    @Test
    void turingFinishedViewRevealsIdentityModelAndSafeRecapButNeverInternalPlayerIds() {
        Game game = Game.builder().id(43L).ownerUserId(1L).publicGame(true)
                .mode(GameMode.TURING_HUNT).status(GameStatus.FINISHED)
                .currentPhase(TuringHuntPhase.FINISHED.name()).currentRound(3).build();
        GamePlayer human = GamePlayer.builder().id(201L).gameId(43L).seatNo(1)
                .playerType(PlayerType.HUMAN).userId(1L).displayName("一号")
                .status(PlayerStatus.ALIVE).roleCode("HUMAN").build();
        GamePlayer ai = GamePlayer.builder().id(202L).gameId(43L).seatNo(2)
                .playerType(PlayerType.AI).modelConfigId(88L).displayName("二号")
                .status(PlayerStatus.ELIMINATED).roleCode("AI").build();
        TuringHuntGameState state = TuringHuntGameState.builder().gameId(43L).roundNo(3).maxRounds(3)
                .phase(TuringHuntPhase.FINISHED).humanPlayerId(201L).winner(TuringHuntWinner.HUMAN)
                .finishedReason(TuringHuntFinishedReason.HUMAN_SURVIVED_THREE_ROUNDS)
                .players(List.of(
                        TuringHuntPlayerState.builder().playerId(201L).seatNo(1).human(true).alive(true).build(),
                        TuringHuntPlayerState.builder().playerId(202L).seatNo(2).human(false).alive(false).build()))
                .build();
        Map<String, Object> recap = Map.of("winner", "HUMAN", "finishedReason",
                "HUMAN_SURVIVED_THREE_ROUNDS", "humanSeatNo", 1,
                "eliminationOrder", List.of(2));
        when(gameMapper.selectOneById(43L)).thenReturn(game);
        when(playerMapper.selectListByQuery(any())).thenReturn(List.of(human, ai));
        when(modelConfigMapper.selectListByQuery(any())).thenReturn(List.of(ModelConfig.builder().id(88L)
                .name("终局模型").provider(ModelProvider.OPENAI).modelName("gpt-test").build()));
        when(snapshotService.loadLatestTuringSnapshot(43L)).thenReturn(state);
        when(eventRecorder.listRecentEvents(eq(43L), anyInt())).thenReturn(List.of(
                GameEvent.builder().eventType("TURING_GAME_FINISHED").publicPayload(recap).build()));

        var players = readService.getPublicPlayers(43L);
        var response = readService.getPublic(43L);

        assertTrue(players.stream().allMatch(player -> player.getId() == null));
        assertEquals(PlayerType.HUMAN, players.get(0).getPlayerType());
        assertEquals("HUMAN", players.get(0).getRoleCode());
        assertEquals(PlayerType.AI, players.get(1).getPlayerType());
        assertEquals("AI", players.get(1).getRoleCode());
        assertEquals("终局模型", players.get(1).getModelDisplayName());
        assertEquals(ModelProvider.OPENAI, players.get(1).getModelProvider());
        assertEquals(Boolean.TRUE, response.getIdentitiesRevealed());
        assertEquals("HUMAN", response.getWinner());
        assertEquals(recap, response.getTuringRecap());
    }
}
