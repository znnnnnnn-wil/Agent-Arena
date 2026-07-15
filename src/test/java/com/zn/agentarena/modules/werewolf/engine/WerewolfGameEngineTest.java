package com.zn.agentarena.modules.werewolf.engine;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.modules.game.engine.EngineResult;
import com.zn.agentarena.modules.werewolf.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 狼人杀游戏引擎单元测试。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@DisplayName("狼人杀游戏引擎单元测试")
class WerewolfGameEngineTest {

    private WerewolfGameEngine engine;
    private WerewolfGameState state;

    @BeforeEach
    void setUp() {
        engine = new WerewolfGameEngine();
        state = createStandardState();
    }

    @Test
    @DisplayName("nightFlow: 狼人、预言家和女巫行动后应进入白天讨论")
    void testNightFlowSuccess() {
        EngineResult<WerewolfGameState> wolfResult = engine.execute(state,
                new WerewolfAction(1L, WerewolfActionType.WOLF_KILL, 5L, null));
        assertEquals(WerewolfPhase.NIGHT_SEER, state.getPhase());
        assertEquals(2, wolfResult.events().size());

        engine.execute(state, new WerewolfAction(3L, WerewolfActionType.SEER_CHECK, 1L, null));
        assertEquals(WerewolfPhase.NIGHT_WITCH, state.getPhase());

        EngineResult<WerewolfGameState> witchResult = engine.execute(state,
                new WerewolfAction(4L, WerewolfActionType.WITCH_SAVE, null, null));
        assertEquals(WerewolfPhase.DAY_DISCUSSION, state.getPhase());
        assertTrue(state.findPlayer(5L).isAlive());
        assertFalse(state.findPlayer(4L).isAntidoteAvailable());
        assertTrue(witchResult.events().stream().anyMatch(event -> "PHASE_CHANGED".equals(event.type())));
    }

    @Test
    @DisplayName("invalidAction: 村民在狼人阶段行动时应被拒绝")
    void testVillagerActInWolfPhaseShouldThrowException() {
        CustomException exception = assertThrows(CustomException.class, () ->
                engine.execute(state, new WerewolfAction(5L, WerewolfActionType.WOLF_KILL, 1L, null)));

        assertEquals(ResultCodeEnum.ACTION_NOT_ALLOWED, exception.getResultCodeEnum());
        assertEquals(WerewolfPhase.NIGHT_WOLF, state.getPhase());
    }

    @Test
    @DisplayName("dayFlow: 完成所有发言和投票后应放逐最高票玩家并进入下一夜")
    void testDayDiscussionAndVoteSuccess() {
        state.setPhase(WerewolfPhase.DAY_DISCUSSION);
        List<Long> alivePlayerIds = state.alivePlayers().stream()
                .map(WerewolfPlayerState::getPlayerId)
                .toList();

        for (Long playerId : alivePlayerIds) {
            engine.execute(state, new WerewolfAction(playerId, WerewolfActionType.SPEAK, null,
                    playerId + "号发言"));
        }
        assertEquals(WerewolfPhase.DAY_VOTE, state.getPhase());

        for (Long playerId : alivePlayerIds) {
            Long targetId = playerId.equals(5L) ? 6L : 5L;
            engine.execute(state, new WerewolfAction(playerId, WerewolfActionType.VOTE, targetId, null));
        }

        assertFalse(state.findPlayer(5L).isAlive());
        assertEquals(WerewolfPhase.NIGHT_WOLF, state.getPhase());
        assertEquals(2, state.getRoundNo());
    }

    @Test
    @DisplayName("winner: 最后一名狼人出局后好人阵营应获胜")
    void testGoodCampWinsWhenLastWolfEliminated() {
        state.findPlayer(2L).setAlive(false);
        state.setPhase(WerewolfPhase.DAY_VOTE);
        List<Long> voterIds = state.alivePlayers().stream().map(WerewolfPlayerState::getPlayerId).toList();

        for (Long voterId : voterIds) {
            Long targetId = voterId.equals(1L) ? 5L : 1L;
            engine.execute(state, new WerewolfAction(voterId, WerewolfActionType.VOTE, targetId, null));
        }

        assertEquals(WerewolfPhase.FINISHED, state.getPhase());
        assertEquals(WerewolfWinner.GOOD, state.getWinner());
        assertTrue(engine.isFinished(state));
    }

    @Test
    @DisplayName("guard: 守卫保护目标后应抵消狼人袭击")
    void testGuardProtectsNightTarget() {
        state.findPlayer(8L).setRole(WerewolfRole.GUARD);

        engine.execute(state, new WerewolfAction(1L, WerewolfActionType.WOLF_KILL, 5L, null));
        assertEquals(WerewolfPhase.NIGHT_GUARD, state.getPhase());
        engine.execute(state, new WerewolfAction(8L, WerewolfActionType.GUARD_PROTECT, 5L, null));
        engine.execute(state, new WerewolfAction(3L, WerewolfActionType.SEER_CHECK, 1L, null));
        EngineResult<WerewolfGameState> result = engine.execute(state,
                new WerewolfAction(4L, WerewolfActionType.SKIP, null, null));

        assertTrue(state.findPlayer(5L).isAlive());
        assertEquals(WerewolfPhase.DAY_DISCUSSION, state.getPhase());
        assertTrue(result.events().stream().anyMatch(event -> "DAWN_RESULT".equals(event.type())));
    }

    @Test
    @DisplayName("nightSkip: 神职死亡后夜间阶段应自动跳过而不是卡局")
    void testDeadSpecialRolesAreSkipped() {
        state.findPlayer(3L).setAlive(false);
        state.findPlayer(4L).setAlive(false);

        engine.execute(state, new WerewolfAction(1L, WerewolfActionType.WOLF_KILL, 5L, null));

        assertEquals(WerewolfPhase.DAY_DISCUSSION, state.getPhase());
        assertFalse(state.findPlayer(5L).isAlive());
    }

    @Test
    @DisplayName("guard: 守卫不能连续两晚保护同一名玩家")
    void testGuardCannotProtectSameTargetTwice() {
        state.findPlayer(8L).setRole(WerewolfRole.GUARD);
        state.setPhase(WerewolfPhase.NIGHT_GUARD);
        state.setLastGuardTargetId(5L);

        assertFalse(engine.validate(state,
                new WerewolfAction(8L, WerewolfActionType.GUARD_PROTECT, 5L, null)).valid());
    }

    private WerewolfGameState createStandardState() {
        List<WerewolfPlayerState> players = new ArrayList<>();
        players.add(player(1L, 1, WerewolfRole.WEREWOLF));
        players.add(player(2L, 2, WerewolfRole.WEREWOLF));
        players.add(player(3L, 3, WerewolfRole.SEER));
        players.add(witch(4L, 4));
        players.add(player(5L, 5, WerewolfRole.VILLAGER));
        players.add(player(6L, 6, WerewolfRole.VILLAGER));
        players.add(player(7L, 7, WerewolfRole.VILLAGER));
        players.add(player(8L, 8, WerewolfRole.VILLAGER));
        return WerewolfGameState.builder()
                .gameId(100L)
                .roundNo(1)
                .phase(WerewolfPhase.NIGHT_WOLF)
                .players(players)
                .votes(new LinkedHashMap<>())
                .build();
    }

    private WerewolfPlayerState player(Long id, int seatNo, WerewolfRole role) {
        return WerewolfPlayerState.builder()
                .playerId(id)
                .seatNo(seatNo)
                .displayName(seatNo + "号玩家")
                .role(role)
                .alive(true)
                .build();
    }

    private WerewolfPlayerState witch(Long id, int seatNo) {
        WerewolfPlayerState witch = player(id, seatNo, WerewolfRole.WITCH);
        witch.setAntidoteAvailable(true);
        witch.setPoisonAvailable(true);
        return witch;
    }
}
