package com.zn.agentarena.modules.battle.agent;

import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.decision.BattleCandidateDecision;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("大逃杀AI自动推进器测试")
class BattleAiGameRunnerTest {

    @Test
    @DisplayName("runNextTurn: 模型异常时应降级并继续执行合法行动")
    void testModelFailureFallsBack() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(BattleCandidateDecision.class)))
                .thenThrow(new IllegalStateException("timeout"));
        BattlePlayerState actor = player(1L);
        BattleRoyaleGameState state = BattleRoyaleGameState.builder().gameId(1L).maxRounds(20)
                .roundNo(1).randomSeed(42L).regions(List.of("A", "B", "C", "D", "E"))
                .safeRegion("A").safeRegions(List.of("A", "B", "C", "D", "E"))
                .players(List.of(actor, player(2L))).build();
        ModelRuntimeConfig config = new ModelRuntimeConfig(ModelProvider.OPENAI, "test", null,
                "test-key", 0.2, 256, Duration.ofSeconds(5), 0);

        var result = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder())
                .runNextTurn(state, actor, config);

        assertEquals("MODEL_FALLBACK", result.events().get(0).payload().get("source"));
        assertTrue(result.events().stream().anyMatch(event -> "BATTLE_ACTION_EXECUTED".equals(event.type())));
    }

    @Test
    @DisplayName("runNextTurn: 空候选编号应标记模型降级并执行本地策略")
    void blankCandidateIdFallsBackInsteadOfSilentlySkipping() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(BattleCandidateDecision.class)))
                .thenReturn(new BattleCandidateDecision("", null, null, "", 0));
        BattlePlayerState actor = player(1L);
        actor.setArmed(true);
        actor.setWeaponLevel(1);
        actor.setWeaponName("简易手枪");
        actor.setAmmo(5);
        BattlePlayerState target = player(2L);
        BattleRoyaleGameState state = BattleRoyaleGameState.builder().gameId(1L).maxRounds(20)
                .roundNo(4).randomSeed(42L).regions(List.of("A", "B", "C", "D", "E"))
                .safeRegion("A").safeRegions(List.of("A", "B", "C", "D", "E"))
                .players(List.of(actor, target)).build();
        ModelRuntimeConfig config = new ModelRuntimeConfig(ModelProvider.OPENAI, "test", null,
                "test-key", 0.2, 256, Duration.ofSeconds(5), 0);

        var result = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder())
                .runNextTurn(state, actor, config);

        assertEquals("ATTACK", result.events().get(0).payload().get("action"));
        assertEquals("MODEL_FALLBACK", result.events().get(0).payload().get("source"));
        assertEquals("INVALID_CANDIDATE_SELECTION", result.events().get(0).payload().get("fallbackCode"));
        assertTrue(target.getHealth() < 100);
    }

    @Test
    @DisplayName("runNextTurn: 模型选择合法搜索候选时不得被策略改成攻击")
    void testLegalSearchCandidateIsPreserved() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(BattleCandidateDecision.class)))
                .thenReturn(new BattleCandidateDecision("T03", null, null, "继续搜集资源", 70));
        BattlePlayerState actor = player(1L);
        actor.setArmed(true);
        actor.setWeaponLevel(1);
        actor.setWeaponName("简易手枪");
        actor.setAmmo(4);
        BattlePlayerState target = player(2L);
        target.setHealth(20);
        BattleRoyaleGameState state = BattleRoyaleGameState.builder().gameId(1L).maxRounds(20)
                .roundNo(4).randomSeed(42L).regions(List.of("A", "B", "C", "D", "E"))
                .safeRegion("A").safeRegions(List.of("A", "B", "C", "D", "E"))
                .players(List.of(actor, target)).build();
        ModelRuntimeConfig config = new ModelRuntimeConfig(ModelProvider.OPENAI, "test", null,
                "test-key", 0.2, 256, Duration.ofSeconds(5), 0);

        var result = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder())
                .runNextTurn(state, actor, config);

        assertEquals("SEARCH", result.events().get(0).payload().get("action"));
        assertEquals(20, target.getHealth());
    }

    @Test
    @DisplayName("runNextTurn: 中期持械玩家遭遇敌人时本地策略应主动攻击")
    void testLocalStrategyAttacksOnArmedEncounter() {
        ModelGateway gateway = mock(ModelGateway.class);
        BattlePlayerState actor = player(1L);
        actor.setArmed(true);
        actor.setWeaponLevel(1);
        actor.setWeaponName("简易手枪");
        actor.setAmmo(5);
        BattlePlayerState target = player(2L);
        BattleRoyaleGameState state = BattleRoyaleGameState.builder().gameId(1L).maxRounds(20)
                .roundNo(4).randomSeed(42L).regions(List.of("A", "B", "C", "D", "E"))
                .safeRegion("A").safeRegions(List.of("A", "B", "C", "D", "E"))
                .players(List.of(actor, target)).build();

        var result = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder())
                .runNextTurn(state, actor, null);

        assertEquals("ATTACK", result.events().get(0).payload().get("action"));
        assertTrue(target.getHealth() < 100);
    }

    @Test
    void omittedConfidenceDoesNotAbortLegalModelAction() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(BattleCandidateDecision.class)))
                .thenReturn(new BattleCandidateDecision("T03", null, null, "搜索补给", null));
        var actor = player(1L);
        var state = BattleRoyaleGameState.builder().gameId(1L).maxRounds(20).roundNo(1)
                .randomSeed(42L).safeRegions(List.of("A", "B", "C", "D", "E"))
                .players(List.of(actor, player(2L))).build();
        var config = new ModelRuntimeConfig(ModelProvider.QWEN, "test", null,
                "test-key", 0.4, 256, Duration.ofSeconds(5), 0);
        var result = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder()).runNextTurn(state, actor, config);
        assertEquals("MODEL", result.events().get(0).payload().get("source"));
        assertEquals("SEARCH", result.events().get(0).payload().get("action"));
        assertEquals(0, result.events().get(0).payload().get("confidence"));
    }

    private BattlePlayerState player(Long id) {
        return BattlePlayerState.builder().playerId(id).displayName(id + "号幸存者")
                .region("A").health(100).food(5).water(5).alive(true).build();
    }
}
