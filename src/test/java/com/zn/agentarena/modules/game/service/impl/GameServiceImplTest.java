package com.zn.agentarena.modules.game.service.impl;

import com.zn.agentarena.dao.entity.Game;
import com.zn.agentarena.dao.entity.GamePlayer;
import com.zn.agentarena.dao.entity.ModelConfig;
import com.zn.agentarena.dao.entity.User;
import com.zn.agentarena.dao.mapper.GameMapper;
import com.zn.agentarena.dao.mapper.GamePlayerMapper;
import com.zn.agentarena.dao.mapper.ModelConfigMapper;
import com.zn.agentarena.dao.mapper.UserMapper;
import com.zn.agentarena.modules.game.dto.request.CreateWerewolfGameRequest;
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
    private GameServiceImpl gameService;

    @BeforeEach
    void setUp() {
        gameMapper = mock(GameMapper.class);
        gamePlayerMapper = mock(GamePlayerMapper.class);
        modelConfigMapper = mock(ModelConfigMapper.class);
        userMapper = mock(UserMapper.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        GameEventRecorder eventRecorder = mock(GameEventRecorder.class);
        com.zn.agentarena.modules.game.service.GameSnapshotService snapshotService =
                mock(com.zn.agentarena.modules.game.service.GameSnapshotService.class);
        GameReadService readService = new GameReadService(gameMapper, gamePlayerMapper, userMapper,
                snapshotService, eventRecorder);
        gameService = new GameServiceImpl(gameMapper, gamePlayerMapper, modelConfigMapper, userMapper,
                mock(IModelConfigService.class), mock(WerewolfAiGameRunner.class), mock(BattleAiGameRunner.class), eventRecorder,
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

        var response = gameService.createWerewolfGame(1L, request);

        assertEquals(100L, response.getId());
        assertEquals(8, verifyInsertPlayerCount());
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

    private int verifyInsertPlayerCount() {
        ArgumentCaptor<GamePlayer> captor = ArgumentCaptor.forClass(GamePlayer.class);
        verify(gamePlayerMapper, times(8)).insertSelective(captor.capture());
        return captor.getAllValues().size();
    }

    private List<GamePlayer> players() {
        List<String> roles = List.of("WEREWOLF", "WEREWOLF", "SEER", "WITCH",
                "VILLAGER", "VILLAGER", "VILLAGER", "VILLAGER");
        List<GamePlayer> players = new ArrayList<>();
        for (int i = 0; i < roles.size(); i++) {
            players.add(GamePlayer.builder().id((long) (i + 1)).gameId(100L).seatNo(i + 1)
                    .modelConfigId(1L).roleCode(roles.get(i)).displayName((i + 1) + "号玩家").build());
        }
        return players;
    }
}
