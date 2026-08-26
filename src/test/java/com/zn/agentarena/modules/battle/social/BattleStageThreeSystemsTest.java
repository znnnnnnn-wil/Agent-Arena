package com.zn.agentarena.modules.battle.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.goal.*;
import com.zn.agentarena.modules.battle.information.*;
import com.zn.agentarena.modules.battle.memory.*;
import com.zn.agentarena.modules.battle.objective.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("大逃杀第三阶段社会模拟")
class BattleStageThreeSystemsTest {
    private BattleSocialStateInitializer initializer;
    private BattleMemorySystem memory;
    private BattleHiddenGoalSystem goals;
    private BattleCooperativeObjectiveSystem objectives;
    private BattleRumorSystem rumors;
    private BattleInformationTradeSystem informationTrades;

    @BeforeEach void setUp() {
        initializer = new BattleSocialStateInitializer();
        memory = new BattleMemorySystem(initializer);
        goals = new BattleHiddenGoalSystem(memory);
        objectives = new BattleCooperativeObjectiveSystem(memory);
        rumors = new BattleRumorSystem(memory);
        informationTrades = new BattleInformationTradeSystem(rumors, memory);
    }

    @Test void hiddenGoalIsVisibleOnlyToOwnerAndPublicEventIsRedacted() {
        BattleRoyaleGameState state = state(player(1L,"C"), player(2L,"C"));
        state.getHiddenGoals().add(BattleHiddenGoal.builder().goalId("g1").ownerPlayerId(1L)
                .type(BattleHiddenGoalType.RESOURCE_HOARDER).targetValue(8).status(BattleHiddenGoalStatus.ACTIVE).build());
        var events = goals.evaluate(state,1L,"SEARCH");
        assertEquals(1, goals.visibleTo(state,1L).size());
        assertTrue(goals.visibleTo(state,2L).stream().noneMatch(goal -> "g1".equals(goal.getGoalId())));
        assertEquals(BattleHiddenGoalStatus.COMPLETED,state.getHiddenGoals().get(0).getStatus());
        assertFalse(events.get(0).publicPayload().containsKey("goalType"));
    }

    @Test void deterministicGoalsAreAssignedOnce() {
        BattleRoyaleGameState state = state(player(1L,"A"),player(2L,"B"));
        goals.ensureAssigned(state); var first = state.getHiddenGoals().stream().map(BattleHiddenGoal::getType).toList();
        goals.ensureAssigned(state);
        assertEquals(2,state.getHiddenGoals().size()); assertEquals(first,state.getHiddenGoals().stream().map(BattleHiddenGoal::getType).toList());
    }

    @Test void cooperativeContributionConsumesOnlyValidatedAmountAndCompletes() {
        BattleRoyaleGameState state=state(player(1L,"C"),player(2L,"C"));
        objectives.ensureObjective(state);
        int food=state.findPlayer(1L).getFood(), water=state.findPlayer(1L).getWater();
        objectives.contribute(state,1L,new BattleObjectiveContributionDecision(null,"FOOD",2));
        objectives.contribute(state,1L,new BattleObjectiveContributionDecision(null,"WATER",2));
        var events=objectives.contribute(state,2L,new BattleObjectiveContributionDecision(null,"WATER",1));
        assertEquals(food-2,state.findPlayer(1L).getFood()); assertEquals(water-1,state.findPlayer(1L).getWater());
        assertEquals(BattleCooperativeObjectiveStatus.COMPLETED,state.getCooperativeObjectives().get(0).getStatus());
        assertTrue(events.stream().anyMatch(e->"BATTLE_OBJECTIVE_COMPLETED".equals(e.type()))); assertEquals(16,state.findPlayer(1L).getArmor());
        assertEquals(12,state.findPlayer(2L).getArmor());
    }

    @Test void invalidCooperativeContributionHasNoSideEffects() {
        BattleRoyaleGameState state=state(player(1L,"A"),player(2L,"C")); objectives.ensureObjective(state); int before=state.findPlayer(1L).getFood();
        assertTrue(objectives.contribute(state,1L,new BattleObjectiveContributionDecision(null,"FOOD",2)).isEmpty());
        assertEquals(before,state.findPlayer(1L).getFood()); assertTrue(state.getCooperativeObjectives().get(0).getContributed().isEmpty());
    }

    @Test void rumorPropagationRequiresKnowledgeAndDecaysWithoutChangingWorldFacts() {
        BattleRoyaleGameState state=state(player(1L,"A"),player(2L,"A"),player(3L,"A")); String safe=state.getSafeRegion();
        var created=rumors.share(state,1L,new BattleRumorDecision(null,3L,"LOCATION","3号在E区",80),List.of(2L));
        BattleRumor source=state.getRumors().get(0);
        assertTrue(rumors.share(state,3L,new BattleRumorDecision(source.getRumorId(),null,null,null,null),List.of(2L)).isEmpty());
        rumors.share(state,2L,new BattleRumorDecision(source.getRumorId(),null,null,null,null),List.of(3L));
        assertEquals(65,state.getRumors().get(1).getConfidence()); assertEquals(1,state.getRumors().get(1).getGeneration());
        assertEquals(safe,state.getSafeRegion()); assertFalse(created.get(0).publicPayload().containsKey("claim"));
    }

    @Test void informationTradeAtomicallyMovesPaymentAndKnowledge() {
        BattleRoyaleGameState state=state(player(1L,"A"),player(2L,"A"));
        rumors.share(state,1L,new BattleRumorDecision(null,null,"THREAT","A区很危险",70),List.of(1L)); String rumorId=state.getRumors().get(0).getRumorId();
        informationTrades.propose(state,1L,2L,new BattleInformationTradeDecision(rumorId,"FOOD",2)); var proposal=state.getInformationTradeProposals().get(0);
        int sellerBefore=state.findPlayer(1L).getFood(), buyerBefore=state.findPlayer(2L).getFood();
        var events=informationTrades.accept(state,2L,1L,proposal.getTradeId());
        assertEquals(sellerBefore+2,state.findPlayer(1L).getFood()); assertEquals(buyerBefore-2,state.findPlayer(2L).getFood());
        assertTrue(state.getRumors().get(0).getKnownByPlayerIds().contains(2L));
        assertFalse(events.get(0).publicPayload().containsKey("price")); assertFalse(events.get(0).publicPayload().containsKey("rumorId"));
    }

    @Test void changedInformationTradeConditionsFailWithoutPartialMutation() {
        BattleRoyaleGameState state=state(player(1L,"A"),player(2L,"A"));
        rumors.share(state,1L,new BattleRumorDecision(null,null,"THREAT","A区很危险",70),List.of(1L)); String rumorId=state.getRumors().get(0).getRumorId();
        informationTrades.propose(state,1L,2L,new BattleInformationTradeDecision(rumorId,"FOOD",5)); var p=state.getInformationTradeProposals().get(0);
        state.findPlayer(2L).setFood(0); int sellerBefore=state.findPlayer(1L).getFood();
        informationTrades.accept(state,2L,1L,p.getTradeId());
        assertEquals(BattleInformationTradeStatus.FAILED,p.getStatus()); assertEquals(sellerBefore,state.findPlayer(1L).getFood()); assertFalse(state.getRumors().get(0).getKnownByPlayerIds().contains(2L));
    }

    @Test void newStateRoundTripsAndLegacyMissingCollectionsAreInitialized() throws Exception {
        ObjectMapper mapper=new ObjectMapper(); BattleRoyaleGameState state=state(player(1L,"C"),player(2L,"C")); goals.ensureAssigned(state); objectives.ensureObjective(state);
        BattleRoyaleGameState restored=mapper.readValue(mapper.writeValueAsString(state),BattleRoyaleGameState.class); initializer.ensure(restored);
        assertEquals(state.getHiddenGoals().size(),restored.getHiddenGoals().size()); assertEquals(state.getCooperativeObjectives().size(),restored.getCooperativeObjectives().size());
        BattleRoyaleGameState legacy=mapper.readValue("{\"gameId\":9,\"players\":[]}",BattleRoyaleGameState.class); initializer.ensure(legacy);
        assertNotNull(legacy.getHiddenGoals()); assertNotNull(legacy.getCooperativeObjectives()); assertNotNull(legacy.getRumors()); assertNotNull(legacy.getInformationTradeProposals());
    }

    private BattleRoyaleGameState state(BattlePlayerState... players){var s=BattleRoyaleGameState.builder().gameId(88L).roundNo(1).maxRounds(20).randomSeed(42L).safeRegion("A").safeRegions(List.of("A","B","C","D","E")).players(new ArrayList<>(List.of(players))).build();initializer.ensure(s);return s;}
    private BattlePlayerState player(Long id,String region){return BattlePlayerState.builder().playerId(id).displayName(id+"号").region(region).health(100).food(5).water(5).alive(true).lifeStatus(BattlePlayerLifeStatus.ALIVE).build();}
}
