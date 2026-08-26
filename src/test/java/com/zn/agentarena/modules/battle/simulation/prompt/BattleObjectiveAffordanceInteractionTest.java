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

class BattleObjectiveAffordanceInteractionTest {
    private BattleObjectiveAffordanceBuilder builder;private BattleActionAffordanceEngine engine;private BattleRoyaleGameState state;private BattlePlayerState actor;private BattleAgentContext context;private List<BattleActionAffordance> computed;
    @BeforeEach void setup(){builder=new BattleObjectiveAffordanceBuilder();engine=new BattleActionAffordanceEngine();state=BattlePromptExperimentMain.state(BattlePromptScenario.HEAL,1_500_000L);actor=state.findPlayer(1L);context=new BattleAiGameRunner(null,new BattleAgentContextBuilder()).createContext(state,actor);computed=engine.compute(state,actor,context);}

    @Test void i0IsExactProduction(){assertEquals(builder.productionPrompt(context),builder.build(BattleObjectiveAffordanceVariant.I0_CURRENT_NONE,context,null).text());}
    @Test void i1IsExactPreviouslyTestedD3(){String expected=new BattleDecisionObjectiveBuilder().build(BattleDecisionObjectiveVariant.D3_OPPORTUNITY_FIRST_NEUTRAL,context).text();assertEquals(expected,builder.build(BattleObjectiveAffordanceVariant.I1_D3_NONE,context,null).text());assertTrue(expected.startsWith(BattleDecisionObjectiveBuilder.OPPORTUNITY_OBJECTIVE));}
    @Test void i2IsExactPreviouslyTestedA3(){String expected=new BattleActionAffordanceBuilder().build(BattleActionAffordanceVariant.A3_DYNAMIC_AFFORDANCE,context,computed).text();assertEquals(expected,builder.build(BattleObjectiveAffordanceVariant.I2_CURRENT_A3,context,computed).text());}
    @Test void i3IsOnlyExactD3PlusExactA3(){String i2=builder.build(BattleObjectiveAffordanceVariant.I2_CURRENT_A3,context,computed).text();String expected=i2.replace(BattleDecisionObjectiveBuilder.PRODUCTION_OBJECTIVE,BattleDecisionObjectiveBuilder.OPPORTUNITY_OBJECTIVE);BattleObjectiveAffordancePrompt actual=builder.build(BattleObjectiveAffordanceVariant.I3_D3_A3,context,computed);assertEquals(expected,actual.text());assertEquals(List.of("DECISION_OBJECTIVE","ACTION_AFFORDANCE"),actual.changedSections());}

    @Test void a3SectionWordingAndPlacementAreUnchanged(){String prior=new BattleActionAffordanceBuilder().build(BattleActionAffordanceVariant.A3_DYNAMIC_AFFORDANCE,context,computed).insertedSection();for(var v:List.of(BattleObjectiveAffordanceVariant.I2_CURRENT_A3,BattleObjectiveAffordanceVariant.I3_D3_A3)){BattleObjectiveAffordancePrompt p=builder.build(v,context,computed);assertEquals(prior,p.insertedAffordanceSection());assertEquals(p.text().indexOf(prior),p.text().indexOf(BattleActionAffordanceBuilder.INSERTION_MARKER)-prior.length());}}

    @Test void allNonFactorSectionsAreByteIdentical(){String production=builder.productionPrompt(context);for(var v:BattleObjectiveAffordanceVariant.values()){BattleObjectiveAffordancePrompt p=builder.build(v,context,v.a3Affordance()?computed:null);String normalized=p.text();if(v.a3Affordance())normalized=normalized.replace(p.insertedAffordanceSection(),"");if(v.d3Objective())normalized=normalized.replace(BattleDecisionObjectiveBuilder.OPPORTUNITY_OBJECTIVE,BattleDecisionObjectiveBuilder.PRODUCTION_OBJECTIVE);assertEquals(production,normalized,v.name());}}

    @Test void fixturesAndAffordanceComputationRemainUnchanged(){for(BattlePromptScenario s:BattlePromptScenario.values()){BattleRoyaleGameState x=BattlePromptExperimentMain.state(s,77L),y=BattlePromptExperimentMain.state(s,77L);BattlePlayerState a=x.findPlayer(1L);BattleAgentContext c=new BattleAiGameRunner(null,new BattleAgentContextBuilder()).createContext(x,a);String before=x.toString();engine.compute(x,a,c);assertEquals(before,x.toString());assertEquals(y,BattlePromptExperimentMain.state(s,77L));}}

    @Test void traceCanReconstructI3AndNeverContainsCredential(){ModelGateway fake=new ModelGateway(){public String generate(ModelRuntimeConfig c,String p){return"{}";}public<T>T generateStructured(ModelRuntimeConfig c,String p,Class<T>t){return t.cast(new BattleAiDecision("HEAL",null,null,null,null,"HP低且有医疗包，可治疗恢复",90));}};BattleAiGameRunner runner=new BattleAiGameRunner(fake,new BattleAgentContextBuilder());BattleObjectiveAffordancePrompt p=builder.build(BattleObjectiveAffordanceVariant.I3_D3_A3,context,computed);String secret="secret-interaction-credential";ModelRuntimeConfig config=new ModelRuntimeConfig(ModelProvider.QWEN,"test",null,secret,0.0,100,Duration.ofSeconds(1),0);BattleDecisionPipelineTrace trace=runner.runDiagnosticTurn(state,actor,config,"HEAL",1L,0,p.text(),Map.of("promptVariant","I3_D3_A3","objectiveVariant",p.objectiveVariant(),"affordanceVariant",p.affordanceVariant(),"computedAffordances",computed));assertEquals("I3_D3_A3",trace.context().get("promptVariant"));assertEquals("D3_OPPORTUNITY_FIRST_NEUTRAL",trace.context().get("objectiveVariant"));assertEquals("A3_DYNAMIC_AFFORDANCE",trace.context().get("affordanceVariant"));assertEquals("HEAL",trace.parsedAction());assertEquals("HEAL",trace.executedAction());assertFalse(String.valueOf(trace).contains(secret));assertFalse(p.text().contains(secret));}
}
