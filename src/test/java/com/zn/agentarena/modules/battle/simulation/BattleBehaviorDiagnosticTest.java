package com.zn.agentarena.modules.battle.simulation;

import com.zn.agentarena.modules.agent.domain.AgentPersonaSnapshot;
import com.zn.agentarena.modules.battle.agent.*;
import com.zn.agentarena.modules.battle.alliance.*;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.information.*;
import com.zn.agentarena.modules.battle.objective.*;
import com.zn.agentarena.modules.battle.promise.*;
import com.zn.agentarena.modules.battle.simulation.trace.*;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleBehaviorDiagnosticTest {

    @Test void promiseOpportunityRequiresResourceRegionAndActiveParticipants() {
        BattleRoyaleGameState state=state();BattlePlayerState actor=state.findPlayer(1L),other=state.findPlayer(2L);
        actor.setFood(2);other.setRegion(actor.getRegion());state.getPromises().add(BattlePromise.builder().promiseId("p")
                .promiserPlayerId(1L).promiseePlayerId(2L).resourceType(BattleResourceType.FOOD).amount(1)
                .status(BattlePromiseStatus.ACCEPTED).acceptedRound(1).deadlineRound(4).knownByPlayerIds(new ArrayList<>(List.of(1L,2L))).build());
        BattleDecisionTraceDraft draft=factory().capture(context(state,actor),state,actor,7);
        assertTrue(draft.serverTruth().promiseOpportunities().get(0).legalFulfillmentOpportunity());
        other.setRegion("B");draft=factory().capture(context(state,actor),state,actor,7);
        assertFalse(draft.serverTruth().promiseOpportunities().get(0).legalFulfillmentOpportunity());
    }

    @Test void cooperativeFunnelDoesNotCountUnknownObjectiveAsKnown() {
        BattleRoyaleGameState s=state();BattleCooperativeObjective o=BattleCooperativeObjective.builder().objectiveId("o")
                .region("C").required(new EnumMap<>(Map.of(BattleResourceType.FOOD,2)))
                .status(BattleCooperativeObjectiveStatus.EXPIRED).deadlineRound(6).build();s.getCooperativeObjectives().add(o);
        BattleAgentKnownState known=known(List.of());BattleServerTruth truth=new BattleServerTruth(List.of(),
                List.of(new BattleObjectiveOpportunityTruth("o","C",6,false,false,true,false,0,2)),
                new BattleMedicOpportunityTruth(List.of(),false,List.of()),new BattleInformationBrokerOpportunityTruth(List.of(),List.of(),false,false),6,false,false,false);
        BattleAgentDecisionTrace trace=trace(1L,known,truth,new BattleTraceDecision("SEARCH",null,null,null,null,null,null,null,"",50,"TEST"));
        BattleSimulationResult result=result(s,List.of(trace));
        assertEquals(0,new BattleBehaviorDiagnosticMetrics(List.of(result)).objectiveFunnel().knownAgents());
    }

    @Test void fieldMedicOnlyCountsActuallyVisibleDownedPlayers() {
        BattleRoyaleGameState s=state();BattlePlayerState actor=s.findPlayer(1L),downed=s.findPlayer(2L);actor.setMedkits(1);downed.markDowned(1,2,3L);downed.setRegion("B");
        BattleDecisionTraceDraft draft=factory().capture(context(s,actor),s,actor,7);
        assertTrue(draft.serverTruth().medicOpportunity().legalReviveTargets().isEmpty());
        downed.setRegion(actor.getRegion());draft=factory().capture(context(s,actor),s,actor,7);
        assertEquals(List.of(2L),draft.serverTruth().medicOpportunity().legalReviveTargets());
    }

    @Test void informationBrokerOnlyCountsRumorsKnownByActor() {
        BattleRoyaleGameState s=state();BattleRumor rumor=BattleRumor.builder().rumorId("r").originatorPlayerId(2L)
                .speakerPlayerId(2L).claimType(BattleRumorClaimType.RESOURCE_CACHE).claim("claim").confidence(50)
                .knownByPlayerIds(new ArrayList<>(List.of(2L))).build();s.getRumors().add(rumor);
        BattleDecisionTraceDraft draft=factory().capture(context(s,s.findPlayer(1L)),s,s.findPlayer(1L),7);
        assertFalse(draft.serverTruth().informationBrokerOpportunity().hasInformation());
        rumor.getKnownByPlayerIds().add(1L);draft=factory().capture(context(s,s.findPlayer(1L)),s,s.findPlayer(1L),7);
        assertTrue(draft.serverTruth().informationBrokerOpportunity().hasInformation());
    }

    @Test void decisionTraceAgentKnownStateDoesNotLeakPrivateAlliance() {
        BattleRoyaleGameState s=state();s.getAlliances().add(BattleAlliance.builder().allianceId("secret")
                .proposerPlayerId(2L).memberAPlayerId(2L).memberBPlayerId(3L).status(BattleAllianceStatus.ACTIVE)
                .knownByPlayerIds(new ArrayList<>(List.of(2L,3L))).build());
        BattleDecisionTraceDraft draft=factory().capture(context(s,s.findPlayer(1L)),s,s.findPlayer(1L),7);
        assertFalse(draft.knownStateWithoutRelationships().activeAlliances().contains("secret"));
        assertTrue(s.getAlliances().stream().anyMatch(a->"secret".equals(a.getAllianceId())));
    }

    @Test void rumorRemainsExplicitlyMarkedAsClaimInTrace() {
        BattleRoyaleGameState s=state();s.getRumors().add(BattleRumor.builder().rumorId("r").originatorPlayerId(1L)
                .speakerPlayerId(1L).claimType(BattleRumorClaimType.RESOURCE_CACHE).claim("claim").confidence(55)
                .knownByPlayerIds(new ArrayList<>(List.of(1L))).build());
        var draft=factory().capture(context(s,s.findPlayer(1L)),s,s.findPlayer(1L),7);
        assertTrue(draft.knownStateWithoutRelationships().rumors().get(0).startsWith("CLAIM:"));
    }

    @Test void traceKnownAndServerTruthArePhysicallySeparateStructures() throws Exception {
        assertNotEquals(BattleAgentKnownState.class,BattleServerTruth.class);
        assertThrows(NoSuchMethodException.class,()->BattleAgentKnownState.class.getMethod("alivePlayers"));
        assertNotNull(BattleServerTruth.class.getMethod("alivePlayers"));
    }

    @Test void multiSeedSimulationIsReproducibleIncludingTraces() {
        BattleSimulationConfig c=new BattleSimulationConfig(BattleSimulationMode.FAST_SIMULATION,3,8,20,910_000,true);
        List<BattleSimulationResult> a=new BattleSimulationRunner().run(c),b=new BattleSimulationRunner().run(c);
        assertEquals(a.stream().map(BattleSimulationResult::winnerPlayerId).toList(),b.stream().map(BattleSimulationResult::winnerPlayerId).toList());
        assertEquals(a.stream().map(x->x.decisionTraces().stream().map(t->t.decision().action()).toList()).toList(),b.stream().map(x->x.decisionTraces().stream().map(t->t.decision().action()).toList()).toList());
    }

    @Test void personaRatesUseOpportunityDenominatorsAndWilsonIntervals() {
        BattleSimulationConfig c=new BattleSimulationConfig(BattleSimulationMode.FAST_SIMULATION,6,8,20,920_000,true);
        var rows=new BattleBehaviorDiagnosticMetrics(new BattleSimulationRunner().run(c)).personaRows();
        assertEquals(3,rows.size());
        rows.forEach(x->{assertTrue(x.ciLow()<=x.winRate()&&x.winRate()<=x.ciHigh());assertTrue(x.revivesPerOpportunity()>=0&&x.revivesPerOpportunity()<=1);assertTrue(x.promiseFulfillmentPerAccepted()>=0&&x.promiseFulfillmentPerAccepted()<=1);});
    }

    @Test void llmSampleCannotCallApiWithoutExplicitGatewayAndConfig() {
        BattleSimulationConfig c=new BattleSimulationConfig(BattleSimulationMode.LLM_SAMPLE,1,8,20,1,true);
        assertThrows(IllegalArgumentException.class,()->new BattleSimulationRunner().run(c));
    }

    @Test void diagnosticFieldsStayOutOfPublicDecisionPayload() {
        BattleRoyaleGameState s=state();BattlePlayerState actor=s.findPlayer(1L);BattleAiGameRunner runner=new BattleAiGameRunner(null,new BattleAgentContextBuilder());
        var result=runner.runPreparedTurn(s,actor,new BattleRoyaleAction(1L,BattleActionType.SEARCH,null,null),null,null);
        EngineEvent event=result.events().stream().filter(e->"AI_DECISION".equals(e.type())).findFirst().orElseThrow();
        assertTrue(event.payload().containsKey("socialAction"));assertFalse(event.publicPayload().containsKey("socialAction"));assertFalse(event.publicPayload().containsKey("communicationChannel"));
    }

    @Test void completedInformationTradeCreditsSellerGoalNotBuyerGoal() {
        BattleRoyaleGameState s=state();BattlePlayerState seller=s.findPlayer(1L),buyer=s.findPlayer(2L);
        seller.setRegion("A");buyer.setRegion("A");buyer.setFood(2);
        context(s,seller); // initialize hidden goals and social state
        s.getHiddenGoals().stream().filter(g->Objects.equals(g.getOwnerPlayerId(),1L)).forEach(g->{g.setType(com.zn.agentarena.modules.battle.goal.BattleHiddenGoalType.INFORMATION_BROKER);g.setStatus(com.zn.agentarena.modules.battle.goal.BattleHiddenGoalStatus.ACTIVE);g.setProgress(0);});
        s.getHiddenGoals().stream().filter(g->Objects.equals(g.getOwnerPlayerId(),2L)).forEach(g->{g.setType(com.zn.agentarena.modules.battle.goal.BattleHiddenGoalType.INFORMATION_BROKER);g.setStatus(com.zn.agentarena.modules.battle.goal.BattleHiddenGoalStatus.ACTIVE);g.setProgress(0);});
        BattleRumor rumor=BattleRumor.builder().rumorId("r").originatorPlayerId(1L).speakerPlayerId(1L)
                .claimType(BattleRumorClaimType.RESOURCE_CACHE).claim("claim").confidence(50)
                .knownByPlayerIds(new ArrayList<>(List.of(1L))).build();s.getRumors().add(rumor);
        new BattleAiGameRunner(null,new BattleAgentContextBuilder()).runPreparedTurn(s,seller,
                new BattleRoyaleAction(1L,BattleActionType.SKIP,null,null),
                new com.zn.agentarena.modules.battle.social.BattleSocialDecision("PROPOSE_INFORMATION_TRADE",2L,null,null,null,null,new BattleInformationTradeDecision("r","FOOD",1),null),null);
        String tradeId=s.getInformationTradeProposals().get(0).getTradeId();
        new BattleAiGameRunner(null,new BattleAgentContextBuilder()).runPreparedTurn(s,buyer,
                new BattleRoyaleAction(2L,BattleActionType.SKIP,null,null),
                new com.zn.agentarena.modules.battle.social.BattleSocialDecision("ACCEPT_INFORMATION_TRADE",1L,tradeId,null,null,null,null,null),null);
        assertEquals(com.zn.agentarena.modules.battle.goal.BattleHiddenGoalStatus.COMPLETED,s.getHiddenGoals().stream().filter(g->Objects.equals(g.getOwnerPlayerId(),1L)).findFirst().orElseThrow().getStatus());
        assertEquals(com.zn.agentarena.modules.battle.goal.BattleHiddenGoalStatus.ACTIVE,s.getHiddenGoals().stream().filter(g->Objects.equals(g.getOwnerPlayerId(),2L)).findFirst().orElseThrow().getStatus());
    }

    private BattleDecisionTraceFactory factory(){return new BattleDecisionTraceFactory();}
    private BattleAgentContext context(BattleRoyaleGameState s,BattlePlayerState p){return new BattleAiGameRunner(null,new BattleAgentContextBuilder()).createContext(s,p);}
    private BattleRoyaleGameState state(){List<BattlePlayerState> ps=new ArrayList<>();for(long i=1;i<=6;i++)ps.add(BattlePlayerState.builder().playerId(i).displayName("P"+i).persona(AgentPersonaSnapshot.builder().profileId(i).code("T").name("T").riskPreference(i%3==0?"CAUTIOUS":i%2==0?"BALANCED":"BOLD").traits(List.of()).behaviorGuidance("").builtIn(true).build()).region(i<=3?"A":"B").health(100).food(7).water(7).alive(true).lifeStatus(BattlePlayerLifeStatus.ALIVE).build());return BattleRoyaleGameState.builder().gameId(7L).randomSeed(7L).roundNo(1).maxRounds(20).regions(new ArrayList<>(List.of("A","B","C","D","E"))).safeRegion("A").safeRegions(new ArrayList<>(List.of("A","B","C","D","E"))).players(ps).build();}
    private BattleAgentKnownState known(List<String> objectives){return new BattleAgentKnownState(List.of(),List.of(),Map.of("food",1),objectives,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),0,List.of(),List.of(),Map.of());}
    private BattleAgentDecisionTrace trace(Long actor,BattleAgentKnownState known,BattleServerTruth truth,BattleTraceDecision decision){return new BattleAgentDecisionTrace(7L,7,1,actor,"BOLD",BattlePlayerLifeStatus.ALIVE,"A",known,truth,decision);}
    private BattleSimulationResult result(BattleRoyaleGameState s,List<BattleAgentDecisionTrace> traces){return new BattleSimulationResult(7,null,6,"MAX_ROUNDS",false,null,s,List.of(),Map.of(),Map.of(),traces);}
}
