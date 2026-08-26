package com.zn.agentarena.modules.werewolf.agent;

import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import com.zn.agentarena.modules.agent.domain.AgentPersonaPresets;
import com.zn.agentarena.modules.werewolf.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 狼人杀AI自动推进器测试。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@DisplayName("狼人杀AI自动推进器测试")
class WerewolfAiGameRunnerTest {

    @Test
    @DisplayName("runNextTurn: 应根据结构化模型决策推进狼人行动")
    void testRunNextTurnSuccess() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(WerewolfAiDecision.class)))
                .thenReturn(new WerewolfAiDecision("WEREWOLF", "WOLF_KILL", 5L, null,
                        "真实身份为狼人，选择袭击非狼人玩家。", 80));
        WerewolfAgentContextBuilder contextBuilder = new WerewolfAgentContextBuilder();
        WerewolfAiGameRunner runner = new WerewolfAiGameRunner(gateway, contextBuilder);
        WerewolfGameState state = createState();

        runner.runNextTurn(state, runtimeConfigs());

        assertEquals(WerewolfPhase.NIGHT_SEER, state.getPhase());
        assertEquals(5L, state.getNightKillTargetId());
        verify(gateway).generateStructured(any(), contains("selfRole: WEREWOLF"), eq(WerewolfAiDecision.class));
    }

    @Test
    @DisplayName("context: 村民上下文不应泄露其他玩家身份")
    void testContextDoesNotLeakPrivateRoles() {
        WerewolfGameState state = createState();
        WerewolfPlayerState villager = state.findPlayer(5L);

        String context = new WerewolfAgentContextBuilder().build(state, villager);

        assertTrue(context.contains("selfRole: VILLAGER"));
        assertTrue(context.contains("selfRole 必须精确返回 \"VILLAGER\""));
        assertTrue(context.contains("狼人可在 publicSpeech 中伪装"));
        assertTrue(context.contains("targetSeatNo"));
        assertFalse(context.contains("playerId"));
        assertFalse(context.contains("\"knownRole\":\"WEREWOLF\""));
        assertFalse(context.contains("\"knownRole\":\"SEER\""));
    }

    @Test
    @DisplayName("context: 同一角色使用不同人格时应注入可观察的不同提示")
    void differentPersonasProduceDifferentPromptInstructions() {
        WerewolfGameState state = createState();
        WerewolfPlayerState firstWolf = state.findPlayer(1L);
        WerewolfPlayerState secondWolf = state.findPlayer(2L);
        firstWolf.setPersona(AgentPersonaPresets.ALL.get(0).snapshot(101L));
        secondWolf.setPersona(AgentPersonaPresets.ALL.get(1).snapshot(102L));
        WerewolfAgentContextBuilder builder = new WerewolfAgentContextBuilder();

        String leaderPrompt = builder.build(state, firstWolf);
        String analystPrompt = builder.build(state, secondWolf);

        assertTrue(leaderPrompt.contains("code: AGGRESSIVE_LEADER"));
        assertTrue(leaderPrompt.contains("优先提出一个明确矛盾"));
        assertTrue(analystPrompt.contains("code: CAUTIOUS_ANALYST"));
        assertTrue(analystPrompt.contains("先核对历史发言"));
        assertNotEquals(leaderPrompt, analystPrompt);
        assertTrue(leaderPrompt.contains("不能改变身份、可见信息和规则"));
    }

    @Test
    @DisplayName("runNextTurn: 模型异常时应使用本地策略继续对局")
    void testModelFailureFallsBack() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(WerewolfAiDecision.class)))
                .thenThrow(new IllegalStateException("timeout"));
        WerewolfGameState state = createState();

        var result = new WerewolfAiGameRunner(gateway, new WerewolfAgentContextBuilder())
                .runNextTurn(state, runtimeConfigs());

        assertNotNull(state.getNightKillTargetId());
        assertFalse(state.findPlayer(state.getNightKillTargetId()).getRole().isWolfCamp());
        assertEquals("MODEL_FALLBACK", result.events().get(0).payload().get("source"));
    }

    @Test
    @DisplayName("night decision stays redacted when the action advances into daytime")
    void nightDecisionDoesNotLeakAfterPhaseTransition() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(WerewolfAiDecision.class)))
                .thenReturn(new WerewolfAiDecision("WITCH", "SKIP", null, null,
                        "secret night reasoning", 75));
        WerewolfGameState state = createState();
        state.setPhase(WerewolfPhase.NIGHT_WITCH);
        state.setNightKillTargetId(5L);

        var result = new WerewolfAiGameRunner(gateway, new WerewolfAgentContextBuilder())
                .runNextTurn(state, runtimeConfigs());

        assertEquals("AI_DECISION", result.events().get(0).type());
        assertEquals(Boolean.TRUE, result.events().get(0).publicPayload().get("completed"));
        assertFalse(result.events().get(0).publicPayload().containsKey("action"));
        assertFalse(result.events().get(0).publicPayload().containsKey("targetPlayerId"));
        assertFalse(result.events().get(0).publicPayload().containsKey("summary"));
    }

    @Test
    @DisplayName("runNextTurn: 数据库ID与座位号不同时应兼容模型按座位选择目标")
    void seatNumberTargetShouldResolveToPersistedPlayerId() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(WerewolfAiDecision.class)))
                .thenReturn(new WerewolfAiDecision("VILLAGER", "VOTE", 1L, null,
                        "真实身份为平民，1号发言可疑。", 70));
        WerewolfGameState state = createPersistedIdVoteState();
        ModelRuntimeConfig config = runtimeConfig();

        var result = new WerewolfAiGameRunner(gateway, new WerewolfAgentContextBuilder())
                .runNextTurn(state, Map.of(244L, config));

        assertEquals(239L, state.getVotes().get(244L));
        assertEquals("MODEL", result.events().get(0).payload().get("source"));
        assertEquals(Boolean.TRUE, result.events().get(0).payload().get("identityVerified"));
        assertEquals("VILLAGER", result.events().get(0).payload().get("selfRole"));
        assertEquals(239L, result.events().get(0).payload().get("targetPlayerId"));
        assertEquals("DAY_VOTE", result.events().get(0).payload().get("decisionPhase"));
        assertEquals(Boolean.TRUE, result.events().get(0).payload().get("protocolNormalized"));
        assertEquals("LEGACY_SEAT_NO", result.events().get(0).payload().get("normalizationKind"));
        verify(gateway).generateStructured(any(), contains("{\"seatNo\":1,\"displayName\":\"1号玩家\",\"alive\":true}"),
                eq(WerewolfAiDecision.class));
        verify(gateway).generateStructured(any(), argThat((String prompt) -> !prompt.contains("targetPlayerId")),
                eq(WerewolfAiDecision.class));
    }

    @Test
    @DisplayName("runNextTurn: 新协议 targetSeatNo 应转换为本局真实玩家 ID")
    void targetSeatNoShouldResolveInsideCurrentGame() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(WerewolfAiDecision.class)))
                .thenReturn(decision("VILLAGER", "VOTE", 3, null, null,
                        List.of(3, 99), List.of(1), 3));
        WerewolfGameState state = createPersistedIdVoteState();

        var result = new WerewolfAiGameRunner(gateway, new WerewolfAgentContextBuilder())
                .runNextTurn(state, Map.of(244L, runtimeConfig()));

        assertEquals(241L, state.getVotes().get(244L));
        assertEquals(3, result.events().get(0).payload().get("targetSeatNo"));
        assertFalse(result.events().get(0).payload().containsKey("protocolNormalized"));
    }

    @Test
    @DisplayName("runNextTurn: 旧字段中的本局真实玩家 ID 仍应兼容")
    void legacyRealPlayerIdShouldRemainCompatible() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(WerewolfAiDecision.class)))
                .thenReturn(new WerewolfAiDecision("VILLAGER", "VOTE", 239L, null,
                        "兼容旧协议", 70));
        WerewolfGameState state = createPersistedIdVoteState();

        var result = new WerewolfAiGameRunner(gateway, new WerewolfAgentContextBuilder())
                .runNextTurn(state, Map.of(244L, runtimeConfig()));

        assertEquals(239L, state.getVotes().get(244L));
        assertEquals("LEGACY_PLAYER_ID", result.events().get(0).payload().get("normalizationKind"));
    }

    @Test
    @DisplayName("runNextTurn: 非本局旧 ID 不得通过并应使用本地合法策略")
    void foreignLegacyPlayerIdShouldFallBack() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(WerewolfAiDecision.class)))
                .thenReturn(new WerewolfAiDecision("VILLAGER", "VOTE", 9_999L, null,
                        "错误外部引用", 70));
        WerewolfGameState state = createPersistedIdVoteState();

        var result = new WerewolfAiGameRunner(gateway, new WerewolfAgentContextBuilder())
                .runNextTurn(state, Map.of(244L, runtimeConfig()));

        assertEquals("MODEL_FALLBACK", result.events().get(0).payload().get("source"));
        assertEquals("RULE_ADJUSTED", result.events().get(0).payload().get("fallbackCode"));
        assertNotEquals(9_999L, state.getVotes().get(244L));
        assertNotNull(state.findPlayer(state.getVotes().get(244L)));
    }

    @Test
    @DisplayName("runNextTurn: 非法模型行动应记录具体校正原因和原始目标引用")
    void invalidDecisionShouldExposeAdjustmentReason() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(WerewolfAiDecision.class)))
                .thenReturn(new WerewolfAiDecision("VILLAGER", "VOTE", 6L, null,
                        "真实身份为平民，尝试投自己。", 30));
        WerewolfGameState state = createPersistedIdVoteState();

        var result = new WerewolfAiGameRunner(gateway, new WerewolfAgentContextBuilder())
                .runNextTurn(state, Map.of(244L, runtimeConfig()));

        Map<String, Object> payload = result.events().get(0).payload();
        assertEquals("MODEL_FALLBACK", payload.get("source"));
        assertEquals("RULE_ADJUSTED", payload.get("fallbackCode"));
        assertEquals(6L, payload.get("modelTargetReference"));
        assertEquals(244L, payload.get("requestedTargetPlayerId"));
        assertEquals("不能选择自己作为目标", payload.get("adjustmentReason"));
        assertTrue(String.valueOf(payload.get("summary")).contains("不能选择自己作为目标"));
        assertEquals(239L, state.getVotes().get(244L));
    }

    @Test
    @DisplayName("runNextTurn: 身份回填错误时应纠正重试并使用正确决策")
    void identityMismatchShouldRetryWithCorrection() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(WerewolfAiDecision.class)))
                .thenReturn(new WerewolfAiDecision("VILLAGER", "WOLF_KILL", 5L, null,
                                "我是平民。", 50),
                        new WerewolfAiDecision("WEREWOLF", "WOLF_KILL", 5L, null,
                                "真实身份为狼人，选择袭击非狼人玩家。", 80));
        WerewolfGameState state = createState();

        var result = new WerewolfAiGameRunner(gateway, new WerewolfAgentContextBuilder())
                .runNextTurn(state, runtimeConfigs());

        Map<String, Object> payload = result.events().get(0).payload();
        assertEquals("MODEL", payload.get("source"));
        assertEquals(Boolean.TRUE, payload.get("identityVerified"));
        assertEquals(Boolean.TRUE, payload.get("identityRetry"));
        assertEquals("WEREWOLF", payload.get("selfRole"));
        assertEquals(5L, state.getNightKillTargetId());
        verify(gateway).generateStructured(any(), contains("【身份纠正】"), eq(WerewolfAiDecision.class));
    }

    @Test
    @DisplayName("runNextTurn: 身份纠正后仍错误时应使用本地身份策略")
    void repeatedIdentityMismatchShouldFallBack() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(WerewolfAiDecision.class)))
                .thenReturn(new WerewolfAiDecision("VILLAGER", "WOLF_KILL", 5L, null,
                        "我是平民。", 50));
        WerewolfGameState state = createState();

        var result = new WerewolfAiGameRunner(gateway, new WerewolfAgentContextBuilder())
                .runNextTurn(state, runtimeConfigs());

        Map<String, Object> payload = result.events().get(0).payload();
        assertEquals("MODEL_FALLBACK", payload.get("source"));
        assertEquals("IDENTITY_MISMATCH", payload.get("fallbackCode"));
        assertTrue(String.valueOf(payload.get("summary")).contains("身份纠正"));
        assertNotNull(state.getNightKillTargetId());
        assertFalse(state.findPlayer(state.getNightKillTargetId()).getRole().isWolfCamp());
        verify(gateway, times(2)).generateStructured(any(), any(), eq(WerewolfAiDecision.class));
    }

    @Test
    @DisplayName("day decision: 公开事件不应泄露私有身份和真实策略")
    void publicDayDecisionShouldRedactPrivateIdentityAndSummary() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(WerewolfAiDecision.class)))
                .thenReturn(new WerewolfAiDecision("VILLAGER", "VOTE", 1L, null,
                        "真实身份为平民，投给指定目标。", 70));
        WerewolfGameState state = createPersistedIdVoteState();

        var result = new WerewolfAiGameRunner(gateway, new WerewolfAgentContextBuilder())
                .runNextTurn(state, Map.of(244L, runtimeConfig()));

        Map<String, Object> privatePayload = result.events().get(0).payload();
        Map<String, Object> publicPayload = result.events().get(0).publicPayload();
        assertEquals("VILLAGER", privatePayload.get("selfRole"));
        assertTrue(privatePayload.containsKey("summary"));
        assertFalse(publicPayload.containsKey("selfRole"));
        assertFalse(publicPayload.containsKey("summary"));
        assertEquals(Boolean.TRUE, publicPayload.get("summaryHidden"));
    }

    private Map<Long, ModelRuntimeConfig> runtimeConfigs() {
        ModelRuntimeConfig config = runtimeConfig();
        Map<Long, ModelRuntimeConfig> configs = new LinkedHashMap<>();
        for (long id = 1; id <= 8; id++) {
            configs.put(id, config);
        }
        return configs;
    }

    private ModelRuntimeConfig runtimeConfig() {
        return new ModelRuntimeConfig(ModelProvider.OPENAI, "test", null,
                "test-key", 0.2, 512, Duration.ofSeconds(10), 1);
    }

    private WerewolfAiDecision decision(String role, String action, Integer targetSeatNo,
                                         String speech, String summary, List<Integer> suspects,
                                         List<Integer> supports, Integer voteIntentSeatNo) {
        return new WerewolfAiDecision(role, action, targetSeatNo, null, speech, null,
                List.of(), suspects, supports, voteIntentSeatNo, List.of("PUBLIC_EVIDENCE"),
                summary == null ? "依据公开信息作出选择" : summary, 0.72);
    }

    private WerewolfGameState createPersistedIdVoteState() {
        List<WerewolfPlayerState> players = new ArrayList<>();
        WerewolfRole[] roles = {WerewolfRole.SEER, WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF,
                WerewolfRole.VILLAGER, WerewolfRole.GUARD, WerewolfRole.VILLAGER,
                WerewolfRole.WITCH, WerewolfRole.VILLAGER};
        for (int seat = 1; seat <= roles.length; seat++) {
            long playerId = 238L + seat;
            players.add(WerewolfPlayerState.builder().playerId(playerId).seatNo(seat)
                    .displayName(seat + "号玩家").role(roles[seat - 1]).alive(true).build());
        }
        Map<Long, Long> votes = new LinkedHashMap<>();
        for (long playerId = 239L; playerId <= 243L; playerId++) {
            votes.put(playerId, 246L);
        }
        return WerewolfGameState.builder().gameId(31L).roundNo(1)
                .phase(WerewolfPhase.DAY_VOTE).players(players).votes(votes).build();
    }

    private WerewolfGameState createState() {
        List<WerewolfPlayerState> players = new ArrayList<>();
        players.add(player(1L, WerewolfRole.WEREWOLF));
        players.add(player(2L, WerewolfRole.WEREWOLF));
        players.add(player(3L, WerewolfRole.SEER));
        WerewolfPlayerState witch = player(4L, WerewolfRole.WITCH);
        witch.setAntidoteAvailable(true);
        witch.setPoisonAvailable(true);
        players.add(witch);
        players.add(player(5L, WerewolfRole.VILLAGER));
        players.add(player(6L, WerewolfRole.VILLAGER));
        players.add(player(7L, WerewolfRole.VILLAGER));
        players.add(player(8L, WerewolfRole.VILLAGER));
        return WerewolfGameState.builder().gameId(1L).roundNo(1)
                .phase(WerewolfPhase.NIGHT_WOLF).players(players)
                .votes(new LinkedHashMap<>()).build();
    }

    private WerewolfPlayerState player(Long id, WerewolfRole role) {
        return WerewolfPlayerState.builder().playerId(id).seatNo(id.intValue())
                .displayName(id + "号玩家").role(role).alive(true).build();
    }
}
