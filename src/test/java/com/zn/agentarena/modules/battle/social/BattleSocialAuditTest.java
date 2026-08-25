package com.zn.agentarena.modules.battle.social;

import com.zn.agentarena.modules.battle.agent.*;
import com.zn.agentarena.modules.battle.alliance.*;
import com.zn.agentarena.modules.battle.communication.*;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.goal.*;
import com.zn.agentarena.modules.battle.information.*;
import com.zn.agentarena.modules.battle.memory.*;
import com.zn.agentarena.modules.battle.objective.*;
import com.zn.agentarena.modules.battle.promise.*;
import com.zn.agentarena.modules.battle.simulation.*;
import com.zn.agentarena.modules.battle.trade.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("大逃杀社会系统全量审计回归")
class BattleSocialAuditTest {
    private BattleSocialStateInitializer initializer; private BattleMemorySystem memory;
    private BattleRelationshipSystem relationships; private BattleAllianceSystem alliances;
    private BattlePromiseSystem promises; private BattleTradeSystem trades; private BattleRumorSystem rumors;
    private BattleInformationTradeSystem info; private BattleSocialActionSystem social; private BattleAiGameRunner runner;

    @BeforeEach void setup(){initializer=new BattleSocialStateInitializer();memory=new BattleMemorySystem(initializer);relationships=new BattleRelationshipSystem(initializer);alliances=new BattleAllianceSystem(initializer,memory,relationships);promises=new BattlePromiseSystem(initializer,memory,relationships);trades=new BattleTradeSystem(initializer,memory,relationships);rumors=new BattleRumorSystem(memory);info=new BattleInformationTradeSystem(rumors,memory);social=new BattleSocialActionSystem(alliances,promises,trades,new BattleCooperativeObjectiveSystem(memory),info,rumors);runner=new BattleAiGameRunner(null,new BattleAgentContextBuilder());}

    @Test void unrelatedPublicMessageDoesNotExpandPrivateAllianceKnowledge(){var s=state(player(1,"A"),player(2,"E"),player(3,"A"));var decision=new BattleSocialDecision("PROPOSE_ALLIANCE",2L,null,null,null);runner.runPreparedTurn(s,s.findPlayer(1L),action(1,BattleActionType.SEARCH,null,null),decision,new BattleCommunicationDecision("PUBLIC",null,"公开但与联盟无关"));assertEquals(Set.of(1L,2L),new HashSet<>(s.getAlliances().get(0).getKnownByPlayerIds()));assertTrue(alliances.visibleTo(s,3L).isEmpty());}

    @Test void downedMayCommunicateButCannotExecuteSocialAction(){var s=state(player(1,"A"),player(2,"A"));s.findPlayer(1L).markDowned(1,2,2L);var result=runner.runPreparedTurn(s,s.findPlayer(1L),action(1,BattleActionType.SKIP,null,null),new BattleSocialDecision("PROPOSE_ALLIANCE",2L,null,null,null),new BattleCommunicationDecision("PRIVATE",2L,"救我"));assertTrue(s.getAlliances().isEmpty());assertTrue(result.events().stream().anyMatch(e->"BATTLE_PRIVATE_MESSAGE".equals(e.type())));}

    @Test void rumorFactAndTwoRelaysKeepDifferentCertaintyAndSource(){var s=state(player(1,"A"),player(2,"A"),player(3,"A"));memory.rememberFact(s,1L,BattleMemoryType.I_ATTACKED_PLAYER,2L,3L,"亲眼事实",90,"A");rumors.share(s,1L,new BattleRumorDecision(null,3L,"THREAT","B攻击了C",80),List.of(2L));var first=s.getRumors().get(0);rumors.share(s,2L,new BattleRumorDecision(first.getRumorId(),null,null,null,null),List.of(3L));var second=s.getRumors().get(1);assertEquals(first.getRumorId(),second.getSourceRumorId());assertEquals(1,second.getGeneration());assertEquals(65,second.getConfidence());assertEquals(100,lastCertainty(s,1L,BattleMemoryType.I_ATTACKED_PLAYER));assertEquals(80,lastCertainty(s,2L,BattleMemoryType.RUMOR_HEARD));assertEquals(65,lastCertainty(s,3L,BattleMemoryType.RUMOR_HEARD));}

    @Test void fabricatedRumorIsAllowedButDoesNotMutateWorld(){var s=state(player(1,"A"),player(2,"A"));String before=s.getSupplyDropRegion();assertFalse(rumors.share(s,1L,new BattleRumorDecision(null,null,"RESOURCE_CACHE","E区有三个医疗包",70),List.of(2L)).isEmpty());assertEquals(before,s.getSupplyDropRegion());assertTrue(memory.memoriesFor(s,2L).stream().noneMatch(m->m.getType()==BattleMemoryType.RESOURCE_RECEIVED));}

    @Test void exactRumorReplayInSameRoundIsIdempotent(){var s=state(player(1,"A"),player(2,"A"));var d=new BattleRumorDecision(null,null,"THREAT","重复传闻",60);assertFalse(rumors.share(s,1L,d,List.of(2L)).isEmpty());assertTrue(rumors.share(s,1L,d,List.of(2L)).isEmpty());assertEquals(1,s.getRumors().size());}

    @Test void promiseIsCancelledOnceWhenParticipantEliminated(){var s=state(player(1,"A"),player(2,"A"));promises.propose(s,1L,2L,new BattlePromiseDecision("GIVE_RESOURCE","FOOD",1,4),List.of());var p=s.getPromises().get(0);promises.accept(s,2L,1L,p.getPromiseId(),List.of());s.findPlayer(1L).markEliminated();List<BattleRelationshipChange> changes=new ArrayList<>();assertEquals(1,promises.evaluateDeadlines(s,changes).size());assertEquals(BattlePromiseStatus.CANCELLED,p.getStatus());assertTrue(promises.evaluateDeadlines(s,changes).isEmpty());assertTrue(changes.isEmpty());}

    @Test void fulfilledPromiseNeverBreaksAndOrdinaryTradeDoesNotAutoFulfill(){var s=state(player(1,"A"),player(2,"A"));s.findPlayer(1L).setFood(4);s.findPlayer(2L).setWater(4);promises.propose(s,1L,2L,new BattlePromiseDecision("GIVE_RESOURCE","FOOD",1,4),List.of());var p=s.getPromises().get(0);promises.accept(s,2L,1L,p.getPromiseId(),List.of());trades.propose(s,1L,2L,new BattleTradeDecision(List.of(new BattleTradeItemDecision("FOOD",1)),List.of(new BattleTradeItemDecision("WATER",1))),List.of());var t=s.getTradeProposals().get(0);trades.accept(s,2L,1L,t.getTradeId(),new ArrayList<>());assertEquals(BattlePromiseStatus.ACCEPTED,p.getStatus());promises.fulfill(s,1L,2L,p.getPromiseId(),new ArrayList<>());s.setRoundNo(4);assertTrue(promises.evaluateDeadlines(s,new ArrayList<>()).isEmpty());assertEquals(BattlePromiseStatus.FULFILLED,p.getStatus());}

    @Test void zeroDamageDoesNotCreateBetrayal(){var s=state(player(1,"A"),player(2,"A"));formAlliance(s);var detector=new BattleBetrayalDetector(alliances,memory,relationships);assertFalse(detector.detect(s,action(1,BattleActionType.ATTACK,2L,null),"A",0).betrayed());assertEquals(BattleAllianceStatus.ACTIVE,s.getAlliances().get(0).getStatus());}

    @Test void leaveAndAttackSameDecisionIsAttackFirstAndThereforeBetrayal(){var s=state(player(1,"A"),player(2,"A"));var a=formAlliance(s);var socialDecision=new BattleSocialDecision("LEAVE_ALLIANCE",2L,a.getAllianceId(),null,null);var result=runner.runPreparedTurn(s,s.findPlayer(1L),action(1,BattleActionType.ATTACK,2L,null),socialDecision,null);assertTrue(result.events().stream().anyMatch(e->"BATTLE_ALLIANCE_BETRAYED".equals(e.type())));assertTrue(result.events().stream().noneMatch(e->"BATTLE_ALLIANCE_LEFT".equals(e.type())));}

    @Test void attackAndAcceptAllianceSameDecisionFormsAllianceAfterNonBetrayalAttack(){var s=state(player(1,"A"),player(2,"A"));alliances.propose(s,2L,1L,List.of());var a=s.getAlliances().get(0);var result=runner.runPreparedTurn(s,s.findPlayer(1L),action(1,BattleActionType.ATTACK,2L,null),new BattleSocialDecision("ACCEPT_ALLIANCE",2L,a.getAllianceId(),null,null),null);assertTrue(result.events().stream().noneMatch(e->"BATTLE_ALLIANCE_BETRAYED".equals(e.type())));assertEquals(BattleAllianceStatus.ACTIVE,a.getStatus());}

    @Test void moveThenAcceptTradeUsesPostMoveRegion(){var s=state(player(1,"A"),player(2,"B"));s.findPlayer(1L).setWater(3);s.findPlayer(2L).setFood(3);trades.propose(s,2L,1L,new BattleTradeDecision(List.of(new BattleTradeItemDecision("FOOD",1)),List.of(new BattleTradeItemDecision("WATER",1))),List.of());var t=s.getTradeProposals().get(0);runner.runPreparedTurn(s,s.findPlayer(1L),action(1,BattleActionType.MOVE,null,"B"),new BattleSocialDecision("ACCEPT_TRADE",2L,t.getTradeId(),null,null),null);assertEquals(BattleTradeStatus.COMPLETED,t.getStatus());}

    @Test void invariantCheckerDetectsLegacyLifeFlagConflictAndInitializerRepairsIt(){var s=state(player(1,"A"),player(2,"B"));s.findPlayer(1L).setLifeStatus(BattlePlayerLifeStatus.ELIMINATED);s.findPlayer(1L).setAlive(true);assertFalse(new BattleStateInvariantChecker().violations(s).isEmpty());initializer.ensure(s);assertFalse(s.findPlayer(1L).isAlive());assertTrue(new BattleStateInvariantChecker().violations(s).isEmpty());}

    @Test void fixedSeedSimulationIsReproducibleAndLongRunKeepsInvariants(){var c=new BattleSimulationConfig(BattleSimulationMode.FAST_SIMULATION,20,8,20,5000,true);var first=new BattleSimulationRunner().run(c);var second=new BattleSimulationRunner().run(c);assertEquals(first.stream().map(BattleSimulationResult::winnerPlayerId).toList(),second.stream().map(BattleSimulationResult::winnerPlayerId).toList());assertEquals(first.stream().map(BattleSimulationResult::rounds).toList(),second.stream().map(BattleSimulationResult::rounds).toList());assertTrue(first.stream().noneMatch(BattleSimulationResult::abnormal));}

    private int lastCertainty(BattleRoyaleGameState s,Long id,BattleMemoryType type){return memory.memoriesFor(s,id).stream().filter(m->m.getType()==type).reduce((a,b)->b).orElseThrow().getCertainty();}
    private BattleAlliance formAlliance(BattleRoyaleGameState s){alliances.propose(s,1L,2L,List.of());var a=s.getAlliances().get(0);alliances.accept(s,2L,1L,a.getAllianceId(),List.of(),new ArrayList<>());return a;}
    private BattleRoyaleAction action(long id,BattleActionType type,Long target,String region){return new BattleRoyaleAction(id,type,target,region);}
    private BattleRoyaleGameState state(BattlePlayerState...p){var s=BattleRoyaleGameState.builder().gameId(7L).roundNo(1).maxRounds(20).randomSeed(9).regions(new ArrayList<>(List.of("A","B","C","D","E"))).safeRegions(new ArrayList<>(List.of("A","B","C","D","E"))).players(new ArrayList<>(List.of(p))).build();initializer.ensure(s);return s;}
    private BattlePlayerState player(long id,String region){return BattlePlayerState.builder().playerId(id).displayName("P"+id).region(region).health(100).food(5).water(5).alive(true).lifeStatus(BattlePlayerLifeStatus.ALIVE).build();}
}
