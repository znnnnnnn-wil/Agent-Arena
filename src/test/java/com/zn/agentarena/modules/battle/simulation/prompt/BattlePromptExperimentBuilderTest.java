package com.zn.agentarena.modules.battle.simulation.prompt;

import com.zn.agentarena.modules.battle.agent.*;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.simulation.trace.BattleDecisionPipelineTrace;
import com.zn.agentarena.modules.model.domain.*;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BattlePromptExperimentBuilderTest {
    private BattleAiGameRunner runner;
    private BattlePromptExperimentBuilder builder;
    private BattleAgentContext context;

    @BeforeEach
    void setUp() {
        runner = new BattleAiGameRunner(null, new BattleAgentContextBuilder());
        BattleRoyaleGameState state = BattlePromptExperimentMain.state(BattlePromptScenario.REVIVE, 1_100_000L);
        context = runner.createContext(state, state.findPlayer(1L));
        builder = new BattlePromptExperimentBuilder();
    }

    @Test
    void variantsDoNotMutateGameStateOrLeakExpectedLabel() {
        BattleRoyaleGameState state = BattlePromptExperimentMain.state(BattlePromptScenario.HEAL, 42L);
        BattleAgentContext c = runner.createContext(state, state.findPlayer(1L));
        int hp = state.findPlayer(1L).getHealth(), players = state.getPlayers().size(), round = state.getRoundNo();
        for (BattlePromptVariant variant : BattlePromptVariant.values()) {
            String prompt = builder.build(variant, BattlePromptScenario.HEAL, c, 9L, 0).text();
            assertFalse(prompt.contains("expectedRelevantAction"));
            assertFalse(prompt.contains("DASHSCOPE_API_KEY"));
        }
        assertEquals(hp, state.findPlayer(1L).getHealth());
        assertEquals(players, state.getPlayers().size());
        assertEquals(round, state.getRoundNo());
    }

    @Test
    void moveLastAndTargetFirstHaveRequiredOrder() {
        var p3 = builder.build(BattlePromptVariant.P3_MOVE_LAST, BattlePromptScenario.HEAL, context, 1L, 0);
        assertEquals("MOVE", p3.actionOrder().get(p3.actionOrder().size()-1));
        for (BattlePromptScenario scenario : BattlePromptScenario.values()) {
            var p4 = builder.build(BattlePromptVariant.P4_TARGET_FIRST, scenario, context, 1L, 0);
            assertEquals(scenario.expectedRelevantAction(), p4.actionOrder().get(0));
        }
    }

    @Test
    void randomOrderIsSeedReproducible() {
        var a = builder.build(BattlePromptVariant.P5_RANDOM_ORDER, BattlePromptScenario.ATTACK, context, 1234L, 2);
        var b = builder.build(BattlePromptVariant.P5_RANDOM_ORDER, BattlePromptScenario.ATTACK, context, 1234L, 2);
        var c = builder.build(BattlePromptVariant.P5_RANDOM_ORDER, BattlePromptScenario.ATTACK, context, 1234L, 3);
        assertEquals(a.actionOrder(), b.actionOrder());
        assertNotEquals(a.actionOrder(), c.actionOrder());
    }

    @Test
    void socialAndPersonaAblationsRemoveOnlyRequestedKinds() {
        String noSocial = builder.build(BattlePromptVariant.P6_NO_SOCIAL_CONTEXT, BattlePromptScenario.REVIVE, context, 1L, 0).text();
        assertFalse(noSocial.contains("【历史记忆"));
        assertFalse(noSocial.contains("【你对其他玩家的主观看法】"));
        assertFalse(noSocial.contains("【正式联盟与待处理邀请】"));
        assertTrue(noSocial.contains("【当前真实观察】"));
        assertTrue(noSocial.contains("【游戏规则】"));
        String noPersona = builder.build(BattlePromptVariant.P7_NO_PERSONA, BattlePromptScenario.REVIVE, context, 1L, 0).text();
        assertFalse(noPersona.contains("<agent-persona>"));
        assertTrue(noPersona.contains("【正式联盟与待处理邀请】"));
    }

    @Test
    void everyVariantUsesSameDecisionTypeAndTraceRecordsVariantWithoutCredential() {
        ModelGateway fake = new ModelGateway() {
            public String generate(ModelRuntimeConfig c, String p) { return "{}"; }
            public <T> T generateStructured(ModelRuntimeConfig c, String p, Class<T> type) {
                assertEquals(BattleAiDecision.class, type);
                return type.cast(new BattleAiDecision("HEAL", null, null, null, null, "heal", 90));
            }
        };
        BattleAiGameRunner diagnostic = new BattleAiGameRunner(fake, new BattleAgentContextBuilder());
        BattleRoyaleGameState state = BattlePromptExperimentMain.state(BattlePromptScenario.HEAL, 77L);
        BattlePlayerState actor = state.findPlayer(1L);
        BattleAgentContext c = diagnostic.createContext(state, actor);
        String prompt = builder.build(BattlePromptVariant.P1_MINIMAL, BattlePromptScenario.HEAL, c, 1L, 0).text();
        ModelRuntimeConfig config = new ModelRuntimeConfig(ModelProvider.QWEN, "test", null, "secret-api-key",
                0.0, 100, Duration.ofSeconds(1), 0);
        BattleDecisionPipelineTrace trace = diagnostic.runDiagnosticTurn(state, actor, config, "HEAL", 77L, 0,
                prompt, Map.of("promptVariant", "P1_MINIMAL", "promptActionOrder", List.of("HEAL", "MOVE")));
        assertEquals("P1_MINIMAL", trace.context().get("promptVariant"));
        assertFalse(trace.prompt().contains("secret-api-key"));
        assertFalse(String.valueOf(trace).contains("secret-api-key"));
        assertEquals("HEAL", trace.parsedAction());
        assertEquals("HEAL", trace.executedAction());
    }
}
