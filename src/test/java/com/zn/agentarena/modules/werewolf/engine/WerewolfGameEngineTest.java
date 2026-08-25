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
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    @DisplayName("wolfTarget: 狼人不能袭击本局存活狼人队友")
    void wolfCannotTargetWolfTeammate() {
        var validation = engine.validate(state,
                new WerewolfAction(1L, WerewolfActionType.WOLF_KILL, 2L, null));

        assertFalse(validation.valid());
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
    @DisplayName("speech: 结构化公开立场应写入事件和快照状态")
    void structuredSpeechIsPersistedAsPublicStance() {
        state.setPhase(WerewolfPhase.DAY_DISCUSSION);
        WerewolfAction action = new WerewolfAction(1L, WerewolfActionType.SPEAK, null,
                "我怀疑5号的立场变化，同时暂时支持3号。", List.of("VILLAGER"),
                List.of(5), List.of(3), 5, List.of("VOTE_SHIFT"),
                "5号的投票意向发生变化", 0.76D);

        EngineResult<WerewolfGameState> result = engine.execute(state, action);

        WerewolfPublicStance stance = state.getPublicStances().get(1L);
        assertNotNull(stance);
        assertEquals(List.of(5), stance.getSuspects());
        assertEquals(List.of(3), stance.getSupports());
        assertEquals(5, stance.getVoteIntentSeatNo());
        assertEquals(List.of("VOTE_SHIFT"), result.events().get(0).publicPayload().get("reasonTags"));
        assertEquals("我怀疑5号的立场变化，同时暂时支持3号。",
                result.events().get(0).publicPayload().get("publicSpeech"));
        assertFalse(result.events().get(0).publicPayload().containsKey("role"));
    }

    @Test
    @DisplayName("enhancedDay: 公开陈述后应进入质询、回应、最终陈述再投票")
    void enhancedDiscussionFlowsThroughQuestionAndResponse() {
        state.setPhase(WerewolfPhase.DAY_DISCUSSION);
        state.setDiscussionVersion(2);
        List<Long> alive = state.alivePlayers().stream().map(WerewolfPlayerState::getPlayerId).toList();
        for (Long playerId : alive) {
            int seat = state.findPlayer(playerId).getSeatNo();
            List<Integer> suspects = seat == 1 || seat == 2 ? List.of(3) : List.of();
            engine.execute(state, publicAction(playerId, WerewolfActionType.SPEAK, null,
                    "这是" + seat + "号的公开陈述。", suspects, suspects.isEmpty() ? null : 3, 0.7D));
        }

        assertEquals(WerewolfPhase.DAY_QUESTION, state.getPhase());
        assertEquals(1, state.getQuestionQueue().size());
        WerewolfQuestionPair pair = state.getQuestionQueue().get(0);
        assertEquals(3L, pair.getRespondentPlayerId());

        EngineResult<WerewolfGameState> question = engine.execute(state,
                publicAction(pair.getQuestionerPlayerId(), WerewolfActionType.QUESTION,
                        pair.getRespondentPlayerId(), "请解释你的公开立场变化。", List.of(3), 3, 0.8D));
        assertEquals(WerewolfPhase.DAY_RESPONSE, state.getPhase());
        assertTrue(question.events().stream().anyMatch(event -> "PLAYER_QUESTIONED".equals(event.type())));
        assertEquals(3, question.events().get(0).publicPayload().get("targetSeatNo"));

        EngineResult<WerewolfGameState> response = engine.execute(state,
                publicAction(pair.getRespondentPlayerId(), WerewolfActionType.RESPOND,
                        pair.getQuestionerPlayerId(), "我根据新公开信息更新了判断。", List.of(5), 5, 0.65D));
        assertEquals(WerewolfPhase.DAY_FINAL_STATEMENT, state.getPhase());
        assertTrue(response.events().stream().anyMatch(event -> "PLAYER_RESPONDED".equals(event.type())));

        for (Long playerId : alive) {
            engine.execute(state, publicAction(playerId, WerewolfActionType.FINAL_STATEMENT, null,
                    "确认最终投票意向。", List.of(5), 5, 0.6D));
        }
        assertEquals(WerewolfPhase.DAY_VOTE, state.getPhase());
    }

    @Test
    @DisplayName("enhancedDay: 没有有效争议时应跳过质询直接投票")
    void noControversySkipsQuestioning() {
        state.setPhase(WerewolfPhase.DAY_DISCUSSION);
        state.setDiscussionVersion(2);
        for (WerewolfPlayerState player : state.alivePlayers()) {
            engine.execute(state, publicAction(player.getPlayerId(), WerewolfActionType.SPEAK, null,
                    "当前没有足够证据。", List.of(), null, 0.3D));
        }

        assertEquals(WerewolfPhase.DAY_VOTE, state.getPhase());
        assertTrue(state.getQuestionQueue().isEmpty());
    }

    @Test
    @DisplayName("enhancedDay: 死亡玩家不能质询且死亡目标不能回应")
    void deadPlayersCannotQuestionOrRespond() {
        state.setPhase(WerewolfPhase.DAY_QUESTION);
        state.setQuestionQueue(new ArrayList<>(List.of(WerewolfQuestionPair.builder()
                .questionerPlayerId(1L).respondentPlayerId(3L).build())));
        state.findPlayer(1L).setAlive(false);
        assertFalse(engine.validate(state, publicAction(1L, WerewolfActionType.QUESTION, 3L,
                "问题", List.of(3), 3, 0.5D)).valid());

        state.findPlayer(1L).setAlive(true);
        state.findPlayer(3L).setAlive(false);
        assertFalse(engine.validate(state, publicAction(1L, WerewolfActionType.QUESTION, 3L,
                "问题", List.of(3), 3, 0.5D)).valid());
    }

    @Test
    @DisplayName("compatibility: 缺少新增字段的旧快照应反序列化并走旧版发言到投票流程")
    void oldSnapshotDeserializesAndContinues() throws Exception {
        String oldJson = """
                {"gameId":88,"sequenceNo":0,"roundNo":2,"phase":"DAY_DISCUSSION","discussionIndex":0,
                 "players":[
                   {"playerId":101,"seatNo":1,"displayName":"一号","role":"VILLAGER","alive":true},
                   {"playerId":102,"seatNo":2,"displayName":"二号","role":"WEREWOLF","alive":true}
                 ],"votes":{},"publicHistory":[]}
                """;
        WerewolfGameState oldState = new ObjectMapper().readValue(oldJson, WerewolfGameState.class);

        assertEquals(0, oldState.getDiscussionVersion());
        engine.execute(oldState, new WerewolfAction(101L, WerewolfActionType.SPEAK, null, "一号发言"));
        engine.execute(oldState, new WerewolfAction(102L, WerewolfActionType.SPEAK, null, "二号发言"));

        assertEquals(WerewolfPhase.DAY_VOTE, oldState.getPhase());
        assertNotNull(oldState.getPublicStances());
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

    private WerewolfAction publicAction(Long actorId, WerewolfActionType type, Long targetId,
                                         String speech, List<Integer> suspects,
                                         Integer voteIntent, double confidence) {
        return new WerewolfAction(actorId, type, targetId, speech, List.of("VILLAGER"),
                suspects, List.of(), voteIntent, List.of("PUBLIC_EVIDENCE"),
                "依据公开信息", confidence);
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
