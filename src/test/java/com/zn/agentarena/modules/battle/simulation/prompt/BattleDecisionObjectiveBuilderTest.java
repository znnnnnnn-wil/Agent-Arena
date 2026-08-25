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

class BattleDecisionObjectiveBuilderTest {
    private BattleDecisionObjectiveBuilder builder;
    private BattleAgentContext context;

    @BeforeEach
    void setUp() {
        builder = new BattleDecisionObjectiveBuilder();
        BattleRoyaleGameState state = BattlePromptExperimentMain.state(BattlePromptScenario.HEAL, 1_300_000L);
        context = new BattleAiGameRunner(null, new BattleAgentContextBuilder()).createContext(state, state.findPlayer(1L));
    }

    @Test void d0EqualsCurrentProductionPrompt() {
        assertEquals(builder.productionPrompt(context), builder.build(BattleDecisionObjectiveVariant.D0_CURRENT, context).text());
        assertEquals(BattleDecisionObjectiveBuilder.PRODUCTION_OBJECTIVE, builder.build(BattleDecisionObjectiveVariant.D0_CURRENT, context).objective());
    }

    @Test void d1OnlyReplacesDecisionObjective() { assertOnlyObjectiveReplacement(BattleDecisionObjectiveVariant.D1_BALANCED_OBJECTIVE, BattleDecisionObjectiveBuilder.BALANCED_OBJECTIVE); }
    @Test void d2OnlyReplacesDecisionObjective() { assertOnlyObjectiveReplacement(BattleDecisionObjectiveVariant.D2_DECISION_CHECKLIST, BattleDecisionObjectiveBuilder.CHECKLIST_OBJECTIVE); }
    @Test void d3OnlyReplacesDecisionObjective() { assertOnlyObjectiveReplacement(BattleDecisionObjectiveVariant.D3_OPPORTUNITY_FIRST_NEUTRAL, BattleDecisionObjectiveBuilder.OPPORTUNITY_OBJECTIVE); }

    @Test void allVariantsRetainLastSurvivorVictoryCondition() {
        for (BattleDecisionObjectiveVariant v : BattleDecisionObjectiveVariant.values())
            assertTrue(builder.build(v, context).text().contains("最后幸存者"), v.name());
    }

    @Test void expectedRelevantActionNeverEntersPrompt() {
        for (BattlePromptScenario scenario : BattlePromptScenario.values()) {
            BattleRoyaleGameState state = BattlePromptExperimentMain.state(scenario, 55L);
            BattleAgentContext c = new BattleAiGameRunner(null, new BattleAgentContextBuilder()).createContext(state, state.findPlayer(1L));
            for (BattleDecisionObjectiveVariant v : BattleDecisionObjectiveVariant.values()) {
                String prompt = builder.build(v, c).text();
                assertFalse(prompt.contains("expectedRelevantAction"));
                assertFalse(prompt.contains("expected=" + scenario.expectedRelevantAction()));
            }
        }
    }

    @Test void actionOrderIsIdenticalAcrossVariants() { assertLineIdentical("action 只能是 "); }
    @Test void regionAffordanceIsIdenticalAcrossVariants() { assertLineIdentical("区域资源只是概率倾向"); }
    @Test void jsonExampleIsIdenticalAcrossVariants() { assertLineIdentical("格式: "); }

    @Test void socialContextIsIdenticalAcrossVariants() {
        assertPromptOutsideObjectiveIdentical();
        assertLineIdentical("【正式联盟与待处理邀请】");
    }

    @Test void personaIsIdenticalAcrossVariants() {
        assertPromptOutsideObjectiveIdentical();
        assertLineIdentical("<untrusted-persona-data>");
    }

    @Test void d3DoesNotListTargetActionNamesAndNoVariantContainsAnswerRules() {
        String d3 = builder.objective(BattleDecisionObjectiveVariant.D3_OPPORTUNITY_FIRST_NEUTRAL);
        for (String action : List.of("HEAL", "ATTACK", "REVIVE", "SEARCH", "治疗", "攻击", "救援", "搜索"))
            assertFalse(d3.contains(action));
        for (BattleDecisionObjectiveVariant v : BattleDecisionObjectiveVariant.values()) {
            String objective = builder.objective(v);
            for (String phrase : List.of("应该HEAL", "应该ATTACK", "应该REVIVE", "应该SEARCH", "必须HEAL", "必须ATTACK", "必须REVIVE", "必须SEARCH"))
                assertFalse(objective.contains(phrase));
        }
    }

    @Test void traceRecordsVariantAndSecretNeverEntersTracePromptOrReport() {
        ModelGateway fake = new ModelGateway() {
            public String generate(ModelRuntimeConfig c, String p) { return "{}"; }
            public <T> T generateStructured(ModelRuntimeConfig c, String p, Class<T> type) {
                return type.cast(new BattleAiDecision("HEAL", null, null, null, null, "生命值较低，使用医疗包恢复", 90));
            }
        };
        BattleRoyaleGameState state = BattlePromptExperimentMain.state(BattlePromptScenario.HEAL, 88L);
        BattlePlayerState actor = state.findPlayer(1L);
        BattleAiGameRunner runner = new BattleAiGameRunner(fake, new BattleAgentContextBuilder());
        BattleAgentContext c = runner.createContext(state, actor);
        BattleDecisionObjectivePrompt prompt = builder.build(BattleDecisionObjectiveVariant.D2_DECISION_CHECKLIST, c);
        String secret = "secret-objective-api-key";
        ModelRuntimeConfig config = new ModelRuntimeConfig(ModelProvider.QWEN, "test", null, secret,
                0.0, 100, Duration.ofSeconds(1), 0);
        BattleDecisionPipelineTrace trace = runner.runDiagnosticTurn(state, actor, config, "HEAL", 88L, 0,
                prompt.text(), Map.of("promptVariant", "D2_DECISION_CHECKLIST", "decisionObjectiveVariant", "D2_DECISION_CHECKLIST"));
        BattleDecisionObjectiveResult result = new BattleDecisionObjectiveResult(BattleDecisionObjectiveVariant.D2_DECISION_CHECKLIST,
                BattlePromptScenario.HEAL, "HEAL", prompt, trace);
        assertEquals("D2_DECISION_CHECKLIST", trace.context().get("decisionObjectiveVariant"));
        assertTrue(BattleDecisionObjectiveMain.contextReferenced(result));
        assertFalse(String.valueOf(trace).contains(secret));
        assertFalse(prompt.text().contains(secret));
        assertFalse(BattleDecisionObjectiveMain.report(List.of(result), 5, builder).contains(secret));
    }

    private void assertOnlyObjectiveReplacement(BattleDecisionObjectiveVariant variant, String replacement) {
        String expected = builder.productionPrompt(context).replace(BattleDecisionObjectiveBuilder.PRODUCTION_OBJECTIVE, replacement);
        BattleDecisionObjectivePrompt actual = builder.build(variant, context);
        assertEquals(expected, actual.text());
        assertEquals(List.of("DECISION_OBJECTIVE"), actual.changedSections());
    }

    private void assertPromptOutsideObjectiveIdentical() {
        String productionTail = builder.productionPrompt(context).substring(BattleDecisionObjectiveBuilder.PRODUCTION_OBJECTIVE.length());
        for (BattleDecisionObjectiveVariant v : BattleDecisionObjectiveVariant.values()) {
            String prompt = builder.build(v, context).text();
            assertTrue(prompt.endsWith(productionTail), v.name());
        }
    }

    private void assertLineIdentical(String prefix) {
        String expected = line(builder.productionPrompt(context), prefix);
        for (BattleDecisionObjectiveVariant v : BattleDecisionObjectiveVariant.values())
            assertEquals(expected, line(builder.build(v, context).text(), prefix), v.name());
    }

    private String line(String text, String prefix) {
        return Arrays.stream(text.split("\\R")).filter(x -> x.startsWith(prefix)).findFirst().orElseThrow();
    }
}
