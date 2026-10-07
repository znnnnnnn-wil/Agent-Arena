package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.agent.*;
import com.zn.agentarena.modules.battle.alliance.*;
import com.zn.agentarena.modules.battle.communication.BattleCommunicationDecision;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.engine.BattleRoyaleGameEngine;
import com.zn.agentarena.modules.battle.goal.*;
import com.zn.agentarena.modules.battle.information.*;
import com.zn.agentarena.modules.battle.memory.*;
import com.zn.agentarena.modules.battle.objective.*;
import com.zn.agentarena.modules.battle.promise.*;
import com.zn.agentarena.modules.battle.social.BattleSocialDecision;
import com.zn.agentarena.modules.battle.social.BattleSocialStateInitializer;
import com.zn.agentarena.modules.model.domain.*;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BattleRuleImprovementsTest {
    @Test
    void finalSocialRewardDeterminesWinnerAndFinishIsLastEvent() {
        var state = state(player(1L, "A"), player(2L, "C"));
        state.setMaxRounds(2);
        state.setRoundTurnOrder(new ArrayList<>(List.of(1L, 2L)));
        state.setActionsInRound(1);
        state.findPlayer(1L).setArmor(4);
        state.findPlayer(1L).setFood(4);
        state.findPlayer(1L).setWater(4);
        state.getHiddenGoals().add(BattleHiddenGoal.builder().goalId("medic").ownerPlayerId(2L)
                .type(BattleHiddenGoalType.FIELD_MEDIC).targetValue(1).status(BattleHiddenGoalStatus.ACTIVE).build());
        state.getCooperativeObjectives().add(BattleCooperativeObjective.builder().objectiveId("test")
                .region("C").required(new EnumMap<>(Map.of(BattleResourceType.FOOD, 1)))
                .minimumContributors(1).deadlineRound(10).status(BattleCooperativeObjectiveStatus.ACTIVE).build());
        var result = runner().runPreparedTurn(state, state.findPlayer(2L), action(2L, BattleActionType.SKIP),
                new BattleSocialDecision("CONTRIBUTE_OBJECTIVE", null, null, null, null,
                        new BattleObjectiveContributionDecision("test", "FOOD", 1), null, null), null);
        assertEquals(2L, state.getWinnerPlayerId());
        assertEquals("BATTLE_FINISHED", result.events().get(result.events().size() - 1).type());
        assertEquals(BattleRoyaleRules.survivalScore(state.findPlayer(2L)),
                result.events().get(result.events().size() - 1).payload().get("score"));
    }

    @Test
    void lastPlayerCanFulfillBeforeDeadlineAndMessageUsesActionRound() {
        var state = state(player(1L, "A"), player(2L, "A"));
        state.setRoundTurnOrder(new ArrayList<>(List.of(1L, 2L)));
        state.setActionsInRound(1);
        var promise = BattlePromise.builder().promiseId("p").promiserPlayerId(2L).promiseePlayerId(1L)
                .promiseType(BattlePromiseType.GIVE_RESOURCE).resourceType(BattleResourceType.FOOD).amount(1)
                .deadlineRound(2).status(BattlePromiseStatus.ACCEPTED).build();
        state.getPromises().add(promise);
        var result = runner().runPreparedTurn(state, state.findPlayer(2L), action(2L, BattleActionType.SKIP),
                new BattleSocialDecision("FULFILL_PROMISE", 1L, "p", null, null),
                new BattleCommunicationDecision("PRIVATE", 1L, "现在履约"));
        assertEquals(BattlePromiseStatus.FULFILLED, promise.getStatus());
        assertEquals(1, promise.getResolvedRound());
        assertEquals(1, state.getCommunicationHistory().get(0).getRound());
        assertEquals(2, state.getRoundNo());
        assertEquals(1, result.events().get(0).payload().get("roundNo"));
        assertFalse(result.events().stream().anyMatch(e -> e.type().equals("BATTLE_PROMISE_BROKEN")));
    }

    @Test
    void localPlayersCanProposeAndAcceptAnAllianceWithMessages() {
        var state = state(player(1L, "C"), player(2L, "C"));
        var runner = runner();
        var first = runner.runNextTurn(state, state.findPlayer(1L), null);
        // 当前有可贡献资源时先推进公共目标；下一次在无协作目标的场景中验证结盟链。
        assertTrue(first.events().stream().anyMatch(e -> e.type().equals("BATTLE_OBJECTIVE_CONTRIBUTED")));
        state = state(player(1L, "A"), player(2L, "A"));
        first = runner.runNextTurn(state, state.findPlayer(1L), null);
        assertTrue(first.events().stream().anyMatch(e -> e.type().equals("BATTLE_ALLIANCE_PROPOSED")));
        var second = runner.runNextTurn(state, state.findPlayer(2L), null);
        assertTrue(second.events().stream().anyMatch(e -> e.type().equals("BATTLE_ALLIANCE_FORMED")));
        assertEquals(2, state.getCommunicationHistory().size());
    }

    @Test
    void modelExecutionFailureDoesNotReplayAnAlreadyExecutedAction() {
        var state = state(player(1L, "A"), player(2L, "E"));
        state.setCommunicationHistory(List.of()); // 强制投递阶段抛错，模拟战术之后的故障。
        var gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), anyString(), eq(BattleCandidateDecision.class)))
                .thenReturn(new BattleCandidateDecision("T03", "NONE", null,
                        new BattleCommunicationDecision("PUBLIC", null, "test"), "test", 80));
        assertThrows(UnsupportedOperationException.class, () ->
                new BattleAiGameRunner(gateway, new BattleAgentContextBuilder()).runNextTurn(state, state.findPlayer(1L), config()));
        assertEquals(1, state.getRandomActionNo());
        assertEquals(1, state.getActionsInRound());
    }

    @Test
    void cappedOptionsRetainContributionRumorAndNoAction() {
        var players = new ArrayList<BattlePlayerState>();
        for (long id = 1; id <= 10; id++) players.add(player(id, "C"));
        var state = state(players.toArray(BattlePlayerState[]::new));
        for (int i = 0; i < 20; i++) state.getAlliances().add(BattleAlliance.builder()
                .allianceId("a" + i).proposerPlayerId(2L).memberAPlayerId(2L).memberBPlayerId(1L)
                .expiresRound(3).status(BattleAllianceStatus.PROPOSED).build());
        var context = runner().createContext(state, state.findPlayer(1L));
        var options = new BattleSocialDecisionCandidateGenerator().generate(state, state.findPlayer(1L), context);
        assertEquals(32, options.candidates().size());
        assertTrue(options.candidates().stream().anyMatch(c -> c.semanticName().startsWith("CONTRIBUTE_")));
        assertTrue(options.candidates().stream().anyMatch(c -> c.semanticName().equals("CREATE_LOCAL_RESOURCE_RUMOR")));
        assertTrue(options.candidates().stream().anyMatch(c -> c.semanticName().equals("NO_SOCIAL_ACTION")));
        assertTrue(options.candidates().stream().anyMatch(c -> c.decision() != null
                && c.decision().objectiveContribution() != null && c.decision().objectiveContribution().amount() > 1));
    }

    @Test
    void incompatibleModelPairIsRecordedWithoutReplacingTacticalChoice() {
        var state = state(player(1L, "C"), player(2L, "E"));
        var gateway = mock(ModelGateway.class);
        var runner = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder());
        var context = runner.createContext(state, state.findPlayer(1L));
        var tactical = new BattleDecisionCandidateGenerator().generate(state, state.findPlayer(1L), context);
        var social = new BattleSocialDecisionCandidateGenerator().generate(state, state.findPlayer(1L), context);
        var move = tactical.candidates().stream().filter(c -> c.semanticName().equals("MOVE_A")).findFirst().orElseThrow();
        var contribution = social.candidates().stream().filter(c -> c.semanticName().startsWith("CONTRIBUTE_")).findFirst().orElseThrow();
        when(gateway.generateStructured(any(), anyString(), eq(BattleCandidateDecision.class)))
                .thenReturn(new BattleCandidateDecision(move.candidateId(), contribution.candidateId(), null, null, "test", 80));
        var result = runner.runNextTurn(state, state.findPlayer(1L), config());
        assertEquals("A", state.findPlayer(1L).getRegion());
        assertEquals("INCOMPATIBLE_TACTICAL_SOCIAL_PAIR", result.events().get(0).payload().get("socialCandidateResolution"));
        assertFalse(result.events().get(0).publicPayload().containsKey("socialCandidateResolution"));
        assertTrue(state.getCooperativeObjectives().get(0).getContributed().isEmpty());
    }

    @Test
    void localStrategyTakesFirstStepTowardDistantSafeRegion() {
        var state = state(player(1L, "A"), player(2L, "E"));
        state.setSafeRegions(List.of("E"));
        var runner = runner();
        var context = runner.createContext(state, state.findPlayer(1L));
        var selected = new BattleHeuristicCandidateSelector().select(
                new BattleDecisionCandidateGenerator().generate(state, state.findPlayer(1L), context), context);
        assertEquals(BattleActionType.MOVE, selected.actionType());
        assertEquals("C", selected.targetRegion());
    }

    @Test
    void armorGainNeverReducesExistingHigherArmor() {
        var state = state(player(1L, "A"), player(2L, "E"));
        state.findPlayer(1L).setArmor(70);
        new BattleRoyaleGameEngine().execute(state, action(1L, BattleActionType.GUARD));
        assertEquals(70, state.findPlayer(1L).getArmor());
        assertEquals(70, BattleRoyaleRules.addArmor(70, 10, 60));
    }

    @Test
    void opponentCannotResetCaptureProgressAndHidingBreaksClaim() {
        var state = state(player(1L, "A"), player(2L, "A"));
        state.setSupplyDropRegion("A");
        var engine = new BattleRoyaleGameEngine();
        engine.execute(state, action(1L, BattleActionType.CAPTURE));
        int progress = state.getSupplyDropClaimActionsRemaining();
        assertFalse(engine.validate(state, action(2L, BattleActionType.CAPTURE)).valid());
        assertEquals(progress, state.getSupplyDropClaimActionsRemaining());
        engine.execute(state, action(2L, BattleActionType.GUARD));
        var result = engine.execute(state, action(1L, BattleActionType.HIDE));
        assertNull(state.getSupplyDropClaimPlayerId());
        assertEquals("A", state.getSupplyDropRegion());
        assertTrue(result.events().stream().anyMatch(e -> e.type().equals("BATTLE_SUPPLY_CLAIM_BROKEN")));
        assertFalse(result.events().stream().anyMatch(e -> e.type().equals("BATTLE_SUPPLY_CAPTURED")));
    }

    @Test
    void informationCannotBeSoldTwiceOrChargedAfterBuyerLearnsIt() {
        var state = state(player(1L, "A"), player(2L, "A"));
        var memory = new BattleMemorySystem(new BattleSocialStateInitializer());
        var rumorSystem = new BattleRumorSystem(memory);
        var trades = new BattleInformationTradeSystem(rumorSystem, memory);
        rumorSystem.share(state, 1L, new BattleRumorDecision(null, null, "THREAT", "可能有危险", 70), List.of(1L));
        var rumor = state.getRumors().get(0);
        var offer = new BattleInformationTradeDecision(rumor.getRumorId(), "FOOD", 1);
        assertFalse(trades.propose(state, 1L, 2L, offer).isEmpty());
        assertTrue(trades.propose(state, 1L, 2L, offer).isEmpty());
        var proposal = state.getInformationTradeProposals().get(0);
        rumor.getKnownByPlayerIds().add(2L);
        trades.accept(state, 2L, 1L, proposal.getTradeId());
        assertEquals("BUYER_ALREADY_KNOWS_INFORMATION", proposal.getFailureReason());
        assertEquals(5, state.findPlayer(2L).getFood());
        assertTrue(trades.propose(state, 1L, 2L, offer).isEmpty());
        rumor.getKnownByPlayerIds().remove(2L);
        trades.propose(state, 1L, 2L, offer);
        trades.accept(state, 2L, 1L, state.getInformationTradeProposals().get(1).getTradeId());
        assertTrue(memory.memoriesFor(state, 2L).stream().anyMatch(m ->
                m.getType() == BattleMemoryType.RUMOR_HEARD && m.getCertainty() == 70));
        assertFalse(memory.memoriesFor(state, 2L).stream().anyMatch(m ->
                m.getType() == BattleMemoryType.INFORMATION_TRADE_COMPLETED && m.getSummary().contains(rumor.getClaim())));
    }

    @Test
    void existingPromiseDoesNotRemoveResourceTradeOptions() {
        var state = state(player(1L, "A"), player(2L, "A"));
        state.getPromises().add(BattlePromise.builder().promiseId("p").promiserPlayerId(1L).promiseePlayerId(2L)
                .promiseType(BattlePromiseType.GIVE_RESOURCE).resourceType(BattleResourceType.FOOD).amount(1)
                .deadlineRound(8).status(BattlePromiseStatus.ACCEPTED).build());
        var context = runner().createContext(state, state.findPlayer(1L));
        var candidates = new BattleSocialDecisionCandidateGenerator().generate(state, state.findPlayer(1L), context);
        assertTrue(candidates.candidates().stream().anyMatch(c -> c.semanticName().equals("PROPOSE_TRADE_P2")));
    }

    @Test
    void localStrategyPreservesAllianceUntilEndgameContest() {
        var state = state(player(1L, "A"), player(2L, "A"));
        state.findPlayer(1L).setArmed(true);
        state.findPlayer(1L).setAmmo(5);
        state.findPlayer(1L).setWeaponLevel(1);
        state.getAlliances().add(BattleAlliance.builder().allianceId("a").memberAPlayerId(1L).memberBPlayerId(2L)
                .activatedRound(1).status(BattleAllianceStatus.ACTIVE).build());
        var runner = runner();
        var context = runner.createContext(state, state.findPlayer(1L));
        var candidates = new BattleDecisionCandidateGenerator().generate(state, state.findPlayer(1L), context);
        assertNotEquals(BattleActionType.ATTACK, new BattleHeuristicCandidateSelector().select(candidates, context).actionType());
        state.setRingStage(5);
        context = runner.createContext(state, state.findPlayer(1L));
        candidates = new BattleDecisionCandidateGenerator().generate(state, state.findPlayer(1L), context);
        assertEquals(BattleActionType.ATTACK, new BattleHeuristicCandidateSelector().select(candidates, context).actionType());
        var result = runner.runNextTurn(state, state.findPlayer(1L), null);
        assertTrue(result.events().stream().anyMatch(e -> e.type().equals("BATTLE_ALLIANCE_BETRAYED")));
    }

    @Test
    void medicalPromiseReservesOnlyResourcesActuallyConsumedByTacticalAction() {
        var state = state(player(1L, "A"), player(2L, "A"));
        state.findPlayer(1L).setMedkits(1);
        state.findPlayer(1L).setHealth(80);
        state.getPromises().add(BattlePromise.builder().promiseId("p").promiserPlayerId(1L).promiseePlayerId(2L)
                .promiseType(BattlePromiseType.GIVE_RESOURCE).resourceType(BattleResourceType.MEDKIT).amount(1)
                .deadlineRound(8).status(BattlePromiseStatus.ACCEPTED).build());
        var context = runner().createContext(state, state.findPlayer(1L));
        var candidates = new BattleSocialDecisionCandidateGenerator().generate(state, state.findPlayer(1L), context);
        var fulfill = candidates.candidates().stream().filter(c -> c.semanticName().equals("FULFILL_PROMISE_p"))
                .findFirst().orElseThrow();
        assertTrue(BattleSocialCandidateCompatibility.allows(fulfill, context, action(1L, BattleActionType.GUARD)));
        assertFalse(BattleSocialCandidateCompatibility.allows(fulfill, context, action(1L, BattleActionType.HEAL)));
    }

    @Test
    void modelFailureKeepsLocalSocialDecisionAndCommunication() {
        var state = state(player(1L, "A"), player(2L, "A"));
        var gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), anyString(), eq(BattleCandidateDecision.class)))
                .thenThrow(new IllegalStateException("timeout"));
        var result = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder())
                .runNextTurn(state, state.findPlayer(1L), config());
        assertEquals("MODEL_FALLBACK", result.events().get(0).payload().get("source"));
        assertTrue(result.events().stream().anyMatch(e -> e.type().equals("BATTLE_ALLIANCE_PROPOSED")));
        assertTrue(result.events().stream().anyMatch(e -> e.type().equals("BATTLE_PRIVATE_MESSAGE")));
    }

    @Test
    void knownInformationHasRejectOptionButNoPurchaseOption() {
        var state = state(player(1L, "A"), player(2L, "A"));
        var rumor = BattleRumor.builder().rumorId("known").originatorPlayerId(2L).speakerPlayerId(2L)
                .claimType(BattleRumorClaimType.RESOURCE_CACHE).claim("合成情报").confidence(70)
                .knownByPlayerIds(new ArrayList<>(List.of(2L))).build();
        state.getRumors().add(rumor);
        state.getInformationTradeProposals().add(BattleInformationTradeProposal.builder().tradeId("sale")
                .sellerPlayerId(2L).buyerPlayerId(1L).rumorId("known")
                .price(new BattleResourceAmount(BattleResourceType.FOOD, 1)).createdRound(1).expiresRound(4)
                .status(BattleInformationTradeStatus.PROPOSED).knownByPlayerIds(new ArrayList<>(List.of(1L, 2L))).build());
        var generator = new BattleSocialDecisionCandidateGenerator();
        var actor = state.findPlayer(1L);
        var context = runner().createContext(state, actor);
        assertTrue(generator.generate(state, actor, context).candidates().stream()
                .anyMatch(c -> c.semanticName().equals("ACCEPT_INFORMATION_TRADE_sale")));
        rumor.getKnownByPlayerIds().add(1L);
        context = runner().createContext(state, actor);
        var options = generator.generate(state, actor, context).candidates();
        assertFalse(options.stream().anyMatch(c -> c.semanticName().equals("ACCEPT_INFORMATION_TRADE_sale")));
        assertTrue(options.stream().anyMatch(c -> c.semanticName().equals("REJECT_INFORMATION_TRADE_sale")));
    }

    private BattleAiGameRunner runner() { return new BattleAiGameRunner(null, new BattleAgentContextBuilder()); }
    private BattleRoyaleAction action(Long id, BattleActionType type) { return new BattleRoyaleAction(id, type, null, null); }
    private ModelRuntimeConfig config() {
        return new ModelRuntimeConfig(ModelProvider.QWEN, "test", null, "test", 0.0, 512, Duration.ofSeconds(1), 0);
    }
    private BattlePlayerState player(Long id, String region) {
        return BattlePlayerState.builder().playerId(id).displayName("P" + id).region(region)
                .health(100).food(5).water(5).alive(true).build();
    }
    private BattleRoyaleGameState state(BattlePlayerState... players) {
        return BattleRoyaleGameState.builder().gameId(9L).roundNo(1).maxRounds(20).randomSeed(42L)
                .players(new ArrayList<>(List.of(players))).build();
    }
}
