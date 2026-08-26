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

class BattleActionAffordanceExperimentTest {
    private BattleActionAffordanceEngine engine;
    private BattleActionAffordanceBuilder builder;

    @BeforeEach void setUp(){engine=new BattleActionAffordanceEngine();builder=new BattleActionAffordanceBuilder();}

    @Test void targetAffordancesAreDynamicallyGeneratedFromEveryFixture(){
        for(BattlePromptScenario scenario:BattlePromptScenario.values()){
            Fixture f=fixture(scenario,100L+scenario.ordinal());
            List<String> names=engine.compute(f.state,f.actor,f.context).stream().map(BattleActionAffordance::action).toList();
            assertTrue(names.contains(scenario.expectedRelevantAction()),scenario.name()+names);
        }
    }

    @Test void healPreconditionComesFromRealMedkitValidationNotScenarioName(){
        Fixture f=fixture(BattlePromptScenario.HEAL,200L);
        assertTrue(names(f).contains("HEAL"));
        f.actor.setMedkits(0);
        assertFalse(engine.compute(f.state,f.actor,f.context).stream().map(BattleActionAffordance::action).toList().contains("HEAL"));
    }

    @Test void attackCandidatesRespectObservationBoundaryAndEngineValidation(){
        Fixture f=fixture(BattlePromptScenario.ATTACK,201L);
        BattleActionAffordance attack=find(f,"ATTACK");
        assertEquals(List.of(2L),attack.targetPlayerIds());
        f.state.findPlayer(2L).setHidden(true);
        BattleAgentContext hiddenContext=new BattleAiGameRunner(null,new BattleAgentContextBuilder()).createContext(f.state,f.actor);
        assertFalse(engine.compute(f.state,f.actor,hiddenContext).stream().anyMatch(a->a.action().equals("ATTACK")));
    }

    @Test void reviveRequiresDownedSameRegionTargetAndMedkit(){
        Fixture f=fixture(BattlePromptScenario.REVIVE,202L);
        assertEquals(List.of(2L),find(f,"REVIVE").targetPlayerIds());
        f.actor.setMedkits(0);
        assertFalse(engine.compute(f.state,f.actor,f.context).stream().anyMatch(a->a.action().equals("REVIVE")));
        assertTrue(engine.compute(f.state,f.actor,f.context).stream().anyMatch(a->a.action().equals("EXECUTE")));
    }

    @Test void moveTargetsComeFromTopologyAndValidate(){
        Fixture f=fixture(BattlePromptScenario.SEARCH,203L);
        assertEquals(Set.of("B","C"),new HashSet<>(find(f,"MOVE").targetRegions()));
    }

    @Test void searchAffordanceUsesCurrentRegionStateWithoutExpectedAnswerBranch(){
        Fixture f=fixture(BattlePromptScenario.SEARCH,204L);
        BattleActionAffordance search=find(f,"SEARCH");
        assertEquals("A",search.relevantState().get("currentRegion"));
        assertEquals(0,search.relevantState().get("currentRegionSearchCount"));
        f.state.getRegionSearchCounts().put("A",4);
        BattleActionAffordance changed=engine.compute(f.state,f.actor,f.context).stream().filter(a->a.action().equals("SEARCH")).findFirst().orElseThrow();
        assertEquals(4,changed.relevantState().get("currentRegionSearchCount"));
    }

    @Test void affordanceComputationDoesNotMutateGameStateFixture(){
        Fixture f=fixture(BattlePromptScenario.REVIVE,210L);
        String before=f.state.toString();
        engine.compute(f.state,f.actor,f.context);
        assertEquals(before,f.state.toString());
    }

    @Test void a0IsByteIdenticalAndHasNoComputedAffordanceSection(){
        Fixture f=fixture(BattlePromptScenario.HEAL,205L);
        BattleActionAffordancePrompt p=builder.build(BattleActionAffordanceVariant.A0_CURRENT,f.context,null);
        assertEquals(builder.productionPrompt(f.context),p.text());
        assertNull(p.insertedSection()); assertEquals(List.of(),p.changedSections());
    }

    @Test void allExperimentalPromptsOnlyInsertTheDeclaredSection(){
        Fixture f=fixture(BattlePromptScenario.HEAL,206L);List<BattleActionAffordance>a=engine.compute(f.state,f.actor,f.context);
        String production=builder.productionPrompt(f.context);
        for(BattleActionAffordanceVariant v:BattleActionAffordanceVariant.values()){
            if(v==BattleActionAffordanceVariant.A0_CURRENT)continue;
            BattleActionAffordancePrompt p=builder.build(v,f.context,a);
            assertEquals(production.replace(BattleActionAffordanceBuilder.INSERTION_MARKER,p.insertedSection()+BattleActionAffordanceBuilder.INSERTION_MARKER),p.text());
            assertEquals(List.of("ACTION_AFFORDANCE"),p.changedSections());
        }
    }

    @Test void fixedProductionSectionsRemainIdenticalAcrossVariants(){
        Fixture f=fixture(BattlePromptScenario.REVIVE,207L);List<BattleActionAffordance>a=engine.compute(f.state,f.actor,f.context);
        String production=builder.productionPrompt(f.context);
        for(String prefix:List.of("你正在参加一局多智能体大逃杀","<untrusted-persona-data>","【历史记忆","【正式联盟","【游戏规则】","区域资源只是概率倾向","格式:","action 只能是 ")){
            String expected=line(production,prefix);
            for(BattleActionAffordanceVariant v:BattleActionAffordanceVariant.values())assertEquals(expected,line(builder.build(v,f.context,v==BattleActionAffordanceVariant.A0_CURRENT?null:a).text(),prefix));
        }
    }

    @Test void variantSectionsHaveNoRecommendationOrPriorityLanguage(){
        Fixture f=fixture(BattlePromptScenario.ATTACK,208L);List<BattleActionAffordance>a=engine.compute(f.state,f.actor,f.context);
        for(BattleActionAffordanceVariant v:BattleActionAffordanceVariant.values()){
            if(v==BattleActionAffordanceVariant.A0_CURRENT)continue;
            String section=builder.build(v,f.context,a).insertedSection();
            assertFalse(section.contains("应该"));assertFalse(section.contains("最好选择"));assertFalse(section.contains("优先执行"));
        }
        String a1=builder.build(BattleActionAffordanceVariant.A1_LEGAL_ACTION_NAMES,f.context,a).insertedSection();
        assertFalse(a1.contains("可执行条件："));assertFalse(a1.contains("targetPlayerIds"));
    }

    @Test void recognitionMetricsSeparateStateMappingAndSelection(){
        assertTrue(BattleActionAffordanceMain.contextRecognized(BattlePromptScenario.HEAL,"当前HP很低"));
        assertFalse(BattleActionAffordanceMain.affordanceRecognized(BattlePromptScenario.HEAL,"当前HP很低"));
        assertTrue(BattleActionAffordanceMain.affordanceRecognized(BattlePromptScenario.HEAL,"HP很低且有医疗包，可以治疗恢复"));
        assertTrue(BattleActionAffordanceMain.affordanceRecognized(BattlePromptScenario.ATTACK,"P2是当前可攻击目标"));
        assertTrue(BattleActionAffordanceMain.affordanceRecognized(BattlePromptScenario.SEARCH,"搜索当前A区的物资"));
        assertTrue(BattleActionAffordanceMain.affordanceRecognized(BattlePromptScenario.REVIVE,"P2在A区倒地，可以救援"));
        assertTrue(BattleActionAffordanceMain.affordanceRecognized(BattlePromptScenario.REVIVE,"P2 is downed in the same region. Reviving him is executable."));
        assertTrue(BattleActionAffordanceMain.affordanceRecognized(BattlePromptScenario.REVIVE,"P2 is Downed in the same region. Reviving him is executable."));
    }

    @Test void traceKeepsParsedSanitizedExecutedSeparateAndRecordsAffordances(){
        ModelGateway fake=new ModelGateway(){public String generate(ModelRuntimeConfig c,String p){return"{}";}public<T>T generateStructured(ModelRuntimeConfig c,String p,Class<T>t){return t.cast(new BattleAiDecision("SEARCH",null,null,null,null,"搜索A区资源",90));}};
        BattleRoyaleGameState state=BattlePromptExperimentMain.state(BattlePromptScenario.ATTACK,209L);BattlePlayerState actor=state.findPlayer(1L);
        BattleAiGameRunner runner=new BattleAiGameRunner(fake,new BattleAgentContextBuilder());BattleAgentContext context=runner.createContext(state,actor);
        List<BattleActionAffordance>a=engine.compute(state,actor,context);BattleActionAffordancePrompt p=builder.build(BattleActionAffordanceVariant.A3_DYNAMIC_AFFORDANCE,context,a);
        String secret="secret-affordance-credential";ModelRuntimeConfig config=new ModelRuntimeConfig(ModelProvider.QWEN,"test",null,secret,0.0,100,Duration.ofSeconds(1),0);
        BattleDecisionPipelineTrace trace=runner.runDiagnosticTurn(state,actor,config,"ATTACK",209L,0,p.text(),Map.of("actionAffordanceVariant","A3_DYNAMIC_AFFORDANCE","computedAffordances",a));
        assertEquals("SEARCH",trace.parsedAction());assertEquals("ATTACK",trace.sanitizedAction());assertEquals("ATTACK",trace.executedAction());assertTrue(trace.sanitizedChanged());
        assertEquals("A3_DYNAMIC_AFFORDANCE",trace.context().get("actionAffordanceVariant"));assertFalse(String.valueOf(trace).contains(secret));assertFalse(p.text().contains(secret));
    }

    private List<String> names(Fixture f){return engine.compute(f.state,f.actor,f.context).stream().map(BattleActionAffordance::action).toList();}
    private BattleActionAffordance find(Fixture f,String action){return engine.compute(f.state,f.actor,f.context).stream().filter(a->a.action().equals(action)).findFirst().orElseThrow();}
    private Fixture fixture(BattlePromptScenario s,long seed){BattleRoyaleGameState state=BattlePromptExperimentMain.state(s,seed);BattlePlayerState actor=state.findPlayer(1L);BattleAgentContext context=new BattleAiGameRunner(null,new BattleAgentContextBuilder()).createContext(state,actor);return new Fixture(state,actor,context);}
    private String line(String text,String prefix){return Arrays.stream(text.split("\\R")).filter(x->x.startsWith(prefix)).findFirst().orElseThrow();}
    private record Fixture(BattleRoyaleGameState state,BattlePlayerState actor,BattleAgentContext context){}
}
