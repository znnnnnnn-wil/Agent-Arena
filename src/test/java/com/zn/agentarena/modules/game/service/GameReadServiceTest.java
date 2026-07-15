package com.zn.agentarena.modules.game.service;

import com.zn.agentarena.common.enums.GameMode;
import com.zn.agentarena.common.enums.GameStatus;
import com.zn.agentarena.common.enums.PlayerStatus;
import com.zn.agentarena.common.enums.PlayerType;
import com.zn.agentarena.dao.entity.Game;
import com.zn.agentarena.dao.entity.GameEvent;
import com.zn.agentarena.dao.entity.GamePlayer;
import com.zn.agentarena.dao.mapper.GameMapper;
import com.zn.agentarena.dao.mapper.GamePlayerMapper;
import com.zn.agentarena.dao.mapper.UserMapper;
import com.zn.agentarena.modules.spectator.service.GameEventRecorder;
import com.zn.agentarena.modules.werewolf.domain.WerewolfGameState;
import com.zn.agentarena.modules.werewolf.domain.WerewolfPlayerState;
import com.zn.agentarena.modules.werewolf.domain.WerewolfRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameReadServiceTest {

    private GameMapper gameMapper;
    private GamePlayerMapper playerMapper;
    private GameSnapshotService snapshotService;
    private GameEventRecorder eventRecorder;
    private GameReadService readService;

    @BeforeEach
    void setUp() {
        gameMapper = mock(GameMapper.class);
        playerMapper = mock(GamePlayerMapper.class);
        snapshotService = mock(GameSnapshotService.class);
        eventRecorder = mock(GameEventRecorder.class);
        readService = new GameReadService(gameMapper, playerMapper, mock(UserMapper.class),
                snapshotService, eventRecorder);
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
        when(snapshotService.loadLatestWerewolfSnapshot(9L))
                .thenReturn(WerewolfGameState.builder().gameId(9L).players(List.of(state)).build());

        var response = readService.getPublicPlayers(9L).get(0);

        assertNull(response.getModelConfigId());
        assertNull(response.getRoleCode());
        assertNull(response.getAntidoteAvailable());
        assertNull(response.getPoisonAvailable());
        assertNull(response.getSeerChecks());
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
}
