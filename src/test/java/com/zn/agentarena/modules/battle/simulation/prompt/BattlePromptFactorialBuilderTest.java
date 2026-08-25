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

class BattlePromptFactorialBuilderTest {
    private BattlePromptFactorialBuilder builder;
    private BattleAgentContext context;

    @BeforeEach
    void setUp() {
        builder = new BattlePromptFactorialBuilder();
        BattleAiGameRunner runner = new BattleAiGameRunner(null, new BattleAgentContextBuilder());
        BattleRoyaleGameState state = BattlePromptExperimentMain.state(BattlePromptScenario.HEAL, 1_200_000L);
        context = runner.createContext(state, state.findPlayer(1L));
    }

    @Test
    void v00IsExactlyProductionPrompt() {
        assertEquals(builder.productionPrompt(context), builder.build(BattlePromptFactorialVariant.V00, context).text());
        assertEquals(List.of(), builder.build(BattlePromptFactorialVariant.V00, context).changedSections());
    }

    @Test
    void eachVariantChangesOnlyItsDeclaredFactor() {
        String production = builder.productionPrompt(context);
        String v01 = builder.build(BattlePromptFactorialVariant.V01, context).text();
        String v10 = builder.build(BattlePromptFactorialVariant.V10, context).text();
        String v11 = builder.build(BattlePromptFactorialVariant.V11, context).text();
        assertEquals(production.replace(BattlePromptFactorialBuilder.REGION_AFFORDANCE_PRODUCTION, ""), v01);
        assertEquals(production.replace(BattlePromptFactorialBuilder.LONG_TERM_PRODUCTION,
                BattlePromptFactorialBuilder.LONG_TERM_NEUTRAL), v10);
        assertEquals(production.replace(BattlePromptFactorialBuilder.LONG_TERM_PRODUCTION,
                        BattlePromptFactorialBuilder.LONG_TERM_NEUTRAL)
                .replace(BattlePromptFactorialBuilder.REGION_AFFORDANCE_PRODUCTION, ""), v11);
        assertEquals(List.of("REGION_AFFORDANCE"), builder.build(BattlePromptFactorialVariant.V01, context).changedSections());
        assertEquals(List.of("LONG_TERM_GOAL"), builder.build(BattlePromptFactorialVariant.V10, context).changedSections());
        assertEquals(List.of("LONG_TERM_GOAL", "REGION_AFFORDANCE"), builder.build(BattlePromptFactorialVariant.V11, context).changedSections());
    }

    @Test
    void negativeFactorsHaveNoResidualStrategicMapping() {
        String noRegion = builder.build(BattlePromptFactorialVariant.V01, context).text();
        for (String phrase : List.of("区域资源只是概率倾向", "A偏武器", "B偏食物", "C偏饮水与弹药", "D偏护甲", "E偏医疗"))
            assertFalse(noRegion.contains(phrase));
        assertTrue(noRegion.contains("region=A"));
        assertTrue(noRegion.contains("地图连接:"));
        assertTrue(noRegion.contains("safeRegions="));
        String noLongTerm = builder.build(BattlePromptFactorialVariant.V10, context).text();
        for (String phrase : List.of("目标是成为最后幸存者", "长期生存", "提高长期生存概率", "生存到最后"))
            assertFalse(noLongTerm.contains(phrase));
        assertTrue(noLongTerm.startsWith(BattlePromptFactorialBuilder.LONG_TERM_NEUTRAL));
    }

    @Test
    void actionOrderAndJsonExampleAreIdenticalAcrossVariants() {
        String expectedActions = line(builder.productionPrompt(context), "action 只能是 ");
        String expectedExample = line(builder.productionPrompt(context), "格式: ");
        for (BattlePromptFactorialVariant variant : BattlePromptFactorialVariant.values()) {
            String prompt = builder.build(variant, context).text();
            assertEquals(expectedActions, line(prompt, "action 只能是 "));
            assertEquals(expectedExample, line(prompt, "格式: "));
            assertFalse(prompt.contains("expectedRelevantAction"));
        }
    }

    @Test
    void gameStateFixturesAreIdenticalForAllVariantsAndBuilderDoesNotMutateThem() {
        for (BattlePromptScenario scenario : BattlePromptScenario.values()) {
            BattleRoyaleGameState expected = BattlePromptExperimentMain.state(scenario, 77L);
            for (BattlePromptFactorialVariant ignored : BattlePromptFactorialVariant.values()) {
                BattleRoyaleGameState actual = BattlePromptExperimentMain.state(scenario, 77L);
                assertEquals(expected, actual);
            }
        }
    }

    @Test
    void traceRecordsFactorFlagsAndNeverContainsKey() {
        ModelGateway fake = new ModelGateway() {
            public String generate(ModelRuntimeConfig c,String p){return "{}";}
            public <T>T generateStructured(ModelRuntimeConfig c,String p,Class<T> type){
                return type.cast(new BattleAiDecision("HEAL",null,null,null,null,"医疗",90));
            }
        };
        BattleAiGameRunner runner = new BattleAiGameRunner(fake,new BattleAgentContextBuilder());
        BattleRoyaleGameState state = BattlePromptExperimentMain.state(BattlePromptScenario.HEAL,88L);
        BattlePlayerState actor=state.findPlayer(1L); BattleAgentContext c=runner.createContext(state,actor);
        BattlePromptFactorialPrompt prompt=builder.build(BattlePromptFactorialVariant.V11,c);
        String secret="secret-factorial-key";
        ModelRuntimeConfig config=new ModelRuntimeConfig(ModelProvider.QWEN,"test",null,secret,0.0,100, Duration.ofSeconds(1),0);
        BattleDecisionPipelineTrace trace=runner.runDiagnosticTurn(state,actor,config,"HEAL",88L,0,prompt.text(),Map.of(
                "promptVariant","V11","longTermGoalPresent",false,"regionAffordancePresent",false));
        assertEquals(false,trace.context().get("longTermGoalPresent"));
        assertEquals(false,trace.context().get("regionAffordancePresent"));
        assertFalse(String.valueOf(trace).contains(secret));
        assertFalse(prompt.text().contains(secret));
    }

    private String line(String text,String prefix){return Arrays.stream(text.split("\\R")).filter(x->x.startsWith(prefix)).findFirst().orElseThrow();}
}
