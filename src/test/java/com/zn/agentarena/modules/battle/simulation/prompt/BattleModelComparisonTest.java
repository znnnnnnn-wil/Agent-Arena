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

class BattleModelComparisonTest {
    private BattleObjectiveAffordanceBuilder builder;private BattleActionAffordanceEngine engine;
    @BeforeEach void setup(){builder=new BattleObjectiveAffordanceBuilder();engine=new BattleActionAffordanceEngine();}

    @Test void bothModelsReceiveByteIdenticalPromptAndHash(){for(BattlePromptScenario s:BattlePromptScenario.values()){Built a=built(s,33L),b=built(s,33L);assertEquals(a.prompt.text(),b.prompt.text());assertEquals(BattleModelComparisonMain.sha256(a.prompt.text()),BattleModelComparisonMain.sha256(b.prompt.text()));}}
    @Test void fixtureIsIdenticalForBothModelGroups(){for(BattlePromptScenario s:BattlePromptScenario.values())assertEquals(BattlePromptExperimentMain.state(s,44L),BattlePromptExperimentMain.state(s,44L));}
    @Test void promptIsExactExistingD3PlusA3(){Built b=built(BattlePromptScenario.HEAL,45L);BattleObjectiveAffordancePrompt expected=new BattleObjectiveAffordanceBuilder().build(BattleObjectiveAffordanceVariant.I3_D3_A3,b.context,b.affordances);assertEquals(expected,b.prompt);}
    @Test void productionActionOrderIsUnchanged(){Built b=built(BattlePromptScenario.ATTACK,46L);String p=b.prompt.text();assertTrue(p.contains("action 只能是 MOVE、SEARCH、CAPTURE、REST、ATTACK、HIDE、HEAL、GUARD、REVIVE、EXECUTE、SKIP"));}
    @Test void runtimeParametersDifferOnlyByModelName(){String secret="not-a-real-key";ModelRuntimeConfig c=BattleModelComparisonMain.config(BattleModelComparisonGroup.M0_CONTROL,secret),s=BattleModelComparisonMain.config(BattleModelComparisonGroup.M1_STRONG,secret);assertEquals(c.provider(),s.provider());assertEquals(c.baseUrl(),s.baseUrl());assertEquals(c.apiKey(),s.apiKey());assertEquals(c.temperature(),s.temperature());assertEquals(c.maxTokens(),s.maxTokens());assertEquals(c.timeout(),s.timeout());assertEquals(c.maxRetries(),s.maxRetries());assertNotEquals(c.modelName(),s.modelName());assertEquals("qwen-turbo",c.modelName());assertEquals("qwen-max",s.modelName());}
    @Test void modelLabelsAreExactAndDistinct(){assertEquals("qwen-turbo",BattleModelComparisonGroup.M0_CONTROL.modelName());assertEquals("qwen-max",BattleModelComparisonGroup.M1_STRONG.modelName());assertNotEquals(BattleModelComparisonGroup.M0_CONTROL.modelName(),BattleModelComparisonGroup.M1_STRONG.modelName());}

    @Test void parsedSanitizedExecutedRemainSeparate(){ModelGateway fake=fake("SEARCH");Built b=built(BattlePromptScenario.ATTACK,47L);BattleAiGameRunner runner=new BattleAiGameRunner(fake,new BattleAgentContextBuilder());BattleDecisionPipelineTrace t=runner.runDiagnosticTurn(b.state,b.actor,BattleModelComparisonMain.config(BattleModelComparisonGroup.M0_CONTROL,"secret"),"ATTACK",47L,0,b.prompt.text(),Map.of("modelGroup","M0_CONTROL"));assertEquals("SEARCH",t.parsedAction());assertEquals("ATTACK",t.sanitizedAction());assertEquals("ATTACK",t.executedAction());}
    @Test void keyNeverEntersPromptOrTrace(){String secret="secret-model-comparison-credential";Built b=built(BattlePromptScenario.HEAL,48L);BattleAiGameRunner runner=new BattleAiGameRunner(fake("HEAL"),new BattleAgentContextBuilder());BattleDecisionPipelineTrace t=runner.runDiagnosticTurn(b.state,b.actor,BattleModelComparisonMain.config(BattleModelComparisonGroup.M0_CONTROL,secret),"HEAL",48L,0,b.prompt.text(),Map.of("modelGroup","M0_CONTROL","promptHash",BattleModelComparisonMain.sha256(b.prompt.text())));assertFalse(b.prompt.text().contains(secret));assertFalse(String.valueOf(t).contains(secret));}
    @Test void strongGroupCannotSilentlyFallbackToTurbo(){Built b=built(BattlePromptScenario.HEAL,49L);BattleAiGameRunner runner=new BattleAiGameRunner(fake("HEAL"),new BattleAgentContextBuilder());BattleDecisionPipelineTrace t=runner.runDiagnosticTurn(b.state,b.actor,BattleModelComparisonMain.config(BattleModelComparisonGroup.M0_CONTROL,"secret"),"HEAL",49L,0,b.prompt.text(),Map.of());assertThrows(IllegalStateException.class,()->BattleModelComparisonSampleValidator.requireRealSample(t,BattleModelComparisonGroup.M1_STRONG));}
    @Test void fakeOrMockGatewayCannotBeCountedAsStrongSample(){Built b=built(BattlePromptScenario.REVIVE,50L);BattleAiGameRunner runner=new BattleAiGameRunner(fake("REVIVE"),new BattleAgentContextBuilder());BattleDecisionPipelineTrace t=runner.runDiagnosticTurn(b.state,b.actor,BattleModelComparisonMain.config(BattleModelComparisonGroup.M1_STRONG,"secret"),"REVIVE",50L,0,b.prompt.text(),Map.of());assertNull(t.rawOutput());assertThrows(IllegalStateException.class,()->BattleModelComparisonSampleValidator.requireRealSample(t,BattleModelComparisonGroup.M1_STRONG));}

    private Built built(BattlePromptScenario s,long seed){BattleRoyaleGameState state=BattlePromptExperimentMain.state(s,seed);BattlePlayerState actor=state.findPlayer(1L);BattleAgentContext context=new BattleAiGameRunner(null,new BattleAgentContextBuilder()).createContext(state,actor);List<BattleActionAffordance>a=engine.compute(state,actor,context);return new Built(state,actor,context,a,builder.build(BattleObjectiveAffordanceVariant.I3_D3_A3,context,a));}
    private ModelGateway fake(String action){return new ModelGateway(){public String generate(ModelRuntimeConfig c,String p){return"{}";}public<T>T generateStructured(ModelRuntimeConfig c,String p,Class<T>t){return t.cast(new BattleAiDecision(action,null,null,null,null,"test",90));}};}
    private record Built(BattleRoyaleGameState state,BattlePlayerState actor,BattleAgentContext context,List<BattleActionAffordance>affordances,BattleObjectiveAffordancePrompt prompt){}
}
