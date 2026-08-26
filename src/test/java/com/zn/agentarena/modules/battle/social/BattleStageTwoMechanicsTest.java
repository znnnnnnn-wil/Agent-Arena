package com.zn.agentarena.modules.battle.social;

import com.zn.agentarena.modules.battle.agent.BattleAgentContextBuilder;
import com.zn.agentarena.modules.battle.agent.BattleAiDecision;
import com.zn.agentarena.modules.battle.agent.BattleAiGameRunner;
import com.zn.agentarena.modules.battle.alliance.BattleAlliance;
import com.zn.agentarena.modules.battle.alliance.BattleAllianceStatus;
import com.zn.agentarena.modules.battle.alliance.BattleAllianceSystem;
import com.zn.agentarena.modules.battle.communication.BattleCommunicationDecision;
import com.zn.agentarena.modules.battle.communication.BattleCommunicationSystem;
import com.zn.agentarena.modules.battle.domain.BattleActionType;
import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleAction;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.decision.BattleCandidateDecision;
import com.zn.agentarena.modules.battle.decision.BattleDecisionCandidateGenerator;
import com.zn.agentarena.modules.battle.decision.BattleSocialDecisionCandidateGenerator;
import com.zn.agentarena.modules.battle.engine.BattleRoyaleGameEngine;
import com.zn.agentarena.modules.battle.memory.BattleMemorySystem;
import com.zn.agentarena.modules.battle.memory.BattleMemoryType;
import com.zn.agentarena.modules.battle.promise.BattlePromiseDecision;
import com.zn.agentarena.modules.battle.promise.BattlePromiseStatus;
import com.zn.agentarena.modules.battle.promise.BattlePromiseSystem;
import com.zn.agentarena.modules.battle.trade.BattleTradeDecision;
import com.zn.agentarena.modules.battle.trade.BattleTradeItemDecision;
import com.zn.agentarena.modules.battle.trade.BattleTradeStatus;
import com.zn.agentarena.modules.battle.trade.BattleTradeSystem;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("大逃杀第二阶段社会规则测试")
class BattleStageTwoMechanicsTest {
    private BattleSocialStateInitializer initializer;
    private BattleMemorySystem memory;
    private BattleRelationshipSystem relationships;
    private BattleAllianceSystem alliances;
    private BattlePromiseSystem promises;
    private BattleTradeSystem trades;
    private BattleCommunicationSystem communication;
    private BattleBetrayalDetector betrayalDetector;

    @BeforeEach
    void setUp() {
        initializer = new BattleSocialStateInitializer();
        memory = new BattleMemorySystem(initializer);
        relationships = new BattleRelationshipSystem(initializer);
        alliances = new BattleAllianceSystem(initializer, memory, relationships);
        promises = new BattlePromiseSystem(initializer, memory, relationships);
        trades = new BattleTradeSystem(initializer, memory, relationships);
        communication = new BattleCommunicationSystem(initializer);
        betrayalDetector = new BattleBetrayalDetector(alliances, memory, relationships);
    }

    @Test
    void allianceProposalIsNotActiveBeforeAcceptance() {
        BattleRoyaleGameState state = state(player(1L, "A"), player(2L, "A"), player(3L, "E"));

        alliances.propose(state, 1L, 2L, List.of(1L, 2L));

        assertEquals(BattleAllianceStatus.PROPOSED, state.getAlliances().get(0).getStatus());
        assertNull(alliances.activeBetween(state, 1L, 2L));
    }

    @Test
    void acceptedAllianceIsVisibleOnlyToInformedPlayers() {
        BattleRoyaleGameState state = state(player(1L, "A"), player(2L, "A"), player(3L, "E"));
        BattleAlliance alliance = formAlliance(state, 1L, 2L);

        assertEquals(BattleAllianceStatus.ACTIVE, alliance.getStatus());
        assertEquals(1, alliances.visibleTo(state, 1L).size());
        assertEquals(1, alliances.visibleTo(state, 2L).size());
        assertTrue(alliances.visibleTo(state, 3L).isEmpty());
    }

    @Test
    void alliedAttackRemainsLegalAndBetrayalDoesNotReachRemoteWitness() {
        BattleRoyaleGameState state = state(player(1L, "A"), player(2L, "A"), player(3L, "E"));
        BattleAlliance alliance = formAlliance(state, 1L, 2L);
        BattleRoyaleAction attack = new BattleRoyaleAction(1L, BattleActionType.ATTACK, 2L, null);

        assertTrue(new BattleRoyaleGameEngine().validate(state, attack).valid());
        BattleBetrayalResult result = betrayalDetector.detect(state, attack, "A", 30);

        assertTrue(result.betrayed());
        assertEquals(BattleAllianceStatus.ENDED, alliance.getStatus());
        assertEquals("BETRAYAL", alliance.getEndReason());
        assertTrue(memory.memoriesFor(state, 2L).stream()
                .anyMatch(item -> item.getType() == BattleMemoryType.ALLY_BETRAYED_ME));
        assertTrue(memory.memoriesFor(state, 3L).stream()
                .noneMatch(item -> item.getType() == BattleMemoryType.ALLIANCE_BETRAYAL_WITNESSED));
    }

    @Test
    void acceptedPromiseDoesNotTransferResourcesAutomatically() {
        BattleRoyaleGameState state = state(player(1L, "A"), player(2L, "A"));
        state.findPlayer(1L).setMedkits(2);
        var promise = proposeAndAcceptPromise(state);

        assertEquals(BattlePromiseStatus.ACCEPTED, promise.getStatus());
        assertEquals(2, state.findPlayer(1L).getMedkits());
        assertEquals(0, state.findPlayer(2L).getMedkits());
    }

    @Test
    void promiseIsFulfilledOnlyByExplicitTransfer() {
        BattleRoyaleGameState state = state(player(1L, "A"), player(2L, "A"));
        state.findPlayer(1L).setMedkits(2);
        var promise = proposeAndAcceptPromise(state);
        List<BattleRelationshipChange> changes = new ArrayList<>();

        var events = promises.fulfill(state, 1L, 2L, promise.getPromiseId(), changes);

        assertEquals(BattlePromiseStatus.FULFILLED, promise.getStatus());
        assertEquals(1, state.findPlayer(1L).getMedkits());
        assertEquals(1, state.findPlayer(2L).getMedkits());
        assertEquals("BATTLE_PROMISE_FULFILLED", events.get(0).type());
        assertEquals("PROMISE_FULFILLED", changes.get(0).reason());
    }

    @Test
    void acceptedPromiseBreaksAtDeadlineAndChangesRelationship() {
        BattleRoyaleGameState state = state(player(1L, "A"), player(2L, "A"));
        state.findPlayer(1L).setMedkits(2);
        var promise = proposeAndAcceptPromise(state);
        state.setRoundNo(promise.getDeadlineRound());
        List<BattleRelationshipChange> changes = new ArrayList<>();

        var events = promises.evaluateDeadlines(state, changes);

        assertEquals(BattlePromiseStatus.BROKEN, promise.getStatus());
        assertEquals("BATTLE_PROMISE_BROKEN", events.get(0).type());
        assertEquals("PROMISE_BROKEN", changes.get(0).reason());
        assertTrue(state.getSocialStates().get(2L).getRelationships().get(1L).getTrust() < 50);
    }

    @Test
    void tradeAcceptsWithAtomicTwoWayExchange() {
        BattleRoyaleGameState state = tradeState();
        var proposal = proposeTrade(state);
        List<BattleRelationshipChange> changes = new ArrayList<>();

        var events = trades.accept(state, 2L, 1L, proposal.getTradeId(), changes);

        assertEquals(BattleTradeStatus.COMPLETED, proposal.getStatus());
        assertEquals(2, state.findPlayer(1L).getFood());
        assertEquals(1, state.findPlayer(1L).getMedkits());
        assertEquals(3, state.findPlayer(2L).getFood());
        assertEquals(0, state.findPlayer(2L).getMedkits());
        assertEquals("BATTLE_TRADE_COMPLETED", events.get(0).type());
    }

    @Test
    void tradeFailureLeavesBothInventoriesUnchanged() {
        BattleRoyaleGameState state = tradeState();
        var proposal = proposeTrade(state);
        state.findPlayer(1L).setFood(1);
        int proposerMedkits = state.findPlayer(1L).getMedkits();
        int targetFood = state.findPlayer(2L).getFood();
        int targetMedkits = state.findPlayer(2L).getMedkits();

        var events = trades.accept(state, 2L, 1L, proposal.getTradeId(), new ArrayList<>());

        assertEquals(BattleTradeStatus.FAILED, proposal.getStatus());
        assertEquals(1, state.findPlayer(1L).getFood());
        assertEquals(proposerMedkits, state.findPlayer(1L).getMedkits());
        assertEquals(targetFood, state.findPlayer(2L).getFood());
        assertEquals(targetMedkits, state.findPlayer(2L).getMedkits());
        assertEquals("BATTLE_TRADE_FAILED", events.get(0).type());
    }

    @Test
    void lethalDamageCreatesDownedStateInsteadOfImmediateElimination() {
        BattleRoyaleGameState state = state(player(1L, "A"), player(2L, "A"), player(3L, "E"));
        state.findPlayer(2L).setHealth(10);

        var result = new BattleRoyaleGameEngine().execute(state,
                new BattleRoyaleAction(1L, BattleActionType.ATTACK, 2L, null));

        assertTrue(state.findPlayer(2L).isDowned());
        assertFalse(state.findPlayer(2L).isEliminated());
        assertEquals(0, state.findPlayer(2L).getHealth());
        assertTrue(result.events().stream().anyMatch(event -> "BATTLE_PLAYER_DOWNED".equals(event.type())));
    }

    @Test
    void reviveConsumesMedkitAndUpdatesMemoryAndRelationship() {
        BattleRoyaleGameState state = state(player(1L, "A"), player(2L, "A"), player(3L, "E"));
        state.findPlayer(1L).setMedkits(1);
        state.findPlayer(2L).markDowned(1, 2, 3L);
        ModelGateway gateway = decisionGateway(new BattleCandidateDecision("T06", null, null, "救援", 90));

        var result = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder())
                .runNextTurn(state, state.findPlayer(1L), runtimeConfig());

        assertTrue(state.findPlayer(2L).isActive());
        assertEquals(30, state.findPlayer(2L).getHealth());
        assertEquals(0, state.findPlayer(1L).getMedkits());
        assertTrue(memoryType(state, 2L, BattleMemoryType.PLAYER_REVIVED_ME));
        assertTrue(state.getSocialStates().get(2L).getRelationships().get(1L).getGratitude() >= 40);
        assertTrue(result.events().stream().anyMatch(event -> "BATTLE_PLAYER_REVIVED".equals(event.type())));
    }

    @Test
    void downedPlayerBleedsOutAtConfiguredRound() {
        BattleRoyaleGameState state = state(player(1L, "A"), player(2L, "A"), player(3L, "E"));
        state.findPlayer(2L).markDowned(1, 2, 1L);
        state.setRoundNo(2);
        state.setActionsInRound(2);

        var result = new BattleRoyaleGameEngine().execute(state,
                new BattleRoyaleAction(1L, BattleActionType.SKIP, null, null));

        assertEquals(3, state.getRoundNo());
        assertTrue(state.findPlayer(2L).isEliminated());
        assertTrue(result.events().stream().anyMatch(event -> "BATTLE_PLAYER_ELIMINATED".equals(event.type())
                && "BLEED_OUT".equals(event.payload().get("reason"))));
    }

    @Test
    void executingDownedAllyTriggersBetrayal() {
        BattleRoyaleGameState state = state(player(1L, "A"), player(2L, "A"), player(3L, "E"));
        BattleAlliance alliance = formAlliance(state, 1L, 2L);
        state.findPlayer(2L).markDowned(1, 2, 3L);
        BattleAiGameRunner probe = new BattleAiGameRunner(null, new BattleAgentContextBuilder());
        var context = probe.createContext(state, state.findPlayer(1L));
        String executeId = new BattleDecisionCandidateGenerator().generate(state, state.findPlayer(1L), context)
                .candidates().stream().filter(candidate -> "EXECUTE_P2".equals(candidate.semanticName()))
                .findFirst().orElseThrow().candidateId();
        ModelGateway gateway = decisionGateway(new BattleCandidateDecision(executeId, null, null, "处决", 90));

        var result = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder())
                .runNextTurn(state, state.findPlayer(1L), runtimeConfig());

        assertTrue(state.findPlayer(2L).isEliminated());
        assertEquals(BattleAllianceStatus.ENDED, alliance.getStatus());
        assertEquals("BETRAYAL", alliance.getEndReason());
        assertTrue(result.events().stream().anyMatch(event -> "BATTLE_ALLIANCE_BETRAYED".equals(event.type())));
    }

    @Test
    void privatePromiseAndTradeEventsRedactTermsFromPublicPayload() {
        BattleRoyaleGameState state = tradeState();
        var promiseEvent = promises.propose(state, 1L, 2L,
                new BattlePromiseDecision("GIVE_RESOURCE", "MEDKIT", 1, 4), List.of(1L, 2L)).get(0);
        var tradeEvent = trades.propose(state, 1L, 2L, tradeDecision(), List.of(1L, 2L)).get(0);

        assertTrue(promiseEvent.payload().containsKey("resourceType"));
        assertFalse(promiseEvent.publicPayload().containsKey("resourceType"));
        assertFalse(promiseEvent.publicPayload().containsKey("amount"));
        assertFalse(promiseEvent.publicPayload().containsKey("deadlineRound"));
        assertTrue(tradeEvent.payload().containsKey("offer"));
        assertFalse(tradeEvent.publicPayload().containsKey("offer"));
        assertFalse(tradeEvent.publicPayload().containsKey("request"));
    }

    @Test
    void localMessageAudienceIsFixedAtSendTime() {
        BattleRoyaleGameState state = state(player(1L, "A"), player(2L, "A"), player(3L, "B"));

        var delivered = communication.deliver(state, 1L,
                new BattleCommunicationDecision("LOCAL", null, "需要支援"));
        state.findPlayer(2L).setRegion("C");
        state.findPlayer(3L).setRegion("A");

        assertEquals("BATTLE_LOCAL_MESSAGE", delivered.event().type());
        assertEquals(List.of(1L, 2L), delivered.message().getAudiencePlayerIds());
        assertEquals(1, communication.messagesVisibleTo(state, 2L).size());
        assertTrue(communication.messagesVisibleTo(state, 3L).isEmpty());
    }

    @Test
    void downedPlayerCanStillCommunicate() {
        BattleRoyaleGameState state = state(player(1L, "A"), player(2L, "A"), player(3L, "E"));
        state.findPlayer(1L).markDowned(1, 2, 2L);

        var delivered = communication.deliver(state, 1L,
                new BattleCommunicationDecision("PRIVATE", 2L, "救我"));

        assertTrue(delivered.delivered());
        assertEquals("BATTLE_PRIVATE_MESSAGE", delivered.event().type());
    }

    @Test
    void runnerExecutesTacticalSocialAndCommunicationOutputsTogether() {
        BattleRoyaleGameState state = state(player(1L, "A"), player(2L, "A"), player(3L, "C"));
        BattleAiGameRunner probe = new BattleAiGameRunner(null, new BattleAgentContextBuilder());
        var context = probe.createContext(state, state.findPlayer(1L));
        String socialId = new BattleSocialDecisionCandidateGenerator().generate(state, state.findPlayer(1L), context)
                .candidates().stream().filter(candidate -> "PROPOSE_ALLIANCE_P2".equals(candidate.semanticName()))
                .findFirst().orElseThrow().candidateId();
        BattleCandidateDecision decision = new BattleCandidateDecision("T03", socialId, null,
                new BattleCommunicationDecision("PRIVATE", 2L, "临时合作"), "搜索并结盟", 80);

        var result = new BattleAiGameRunner(decisionGateway(decision), new BattleAgentContextBuilder())
                .runNextTurn(state, state.findPlayer(1L), runtimeConfig());

        assertTrue(result.events().stream().anyMatch(event -> "BATTLE_ACTION_EXECUTED".equals(event.type())));
        assertTrue(result.events().stream().anyMatch(event -> "BATTLE_PRIVATE_MESSAGE".equals(event.type())));
        assertTrue(result.events().stream().anyMatch(event -> "BATTLE_ALLIANCE_PROPOSED".equals(event.type())));
        assertEquals(BattleAllianceStatus.PROPOSED, state.getAlliances().get(0).getStatus());
    }

    private BattleAlliance formAlliance(BattleRoyaleGameState state, Long proposerId, Long targetId) {
        alliances.propose(state, proposerId, targetId, List.of(proposerId, targetId));
        BattleAlliance alliance = state.getAlliances().get(state.getAlliances().size() - 1);
        alliances.accept(state, targetId, proposerId, alliance.getAllianceId(),
                List.of(proposerId, targetId), new ArrayList<>());
        return alliance;
    }

    private com.zn.agentarena.modules.battle.promise.BattlePromise proposeAndAcceptPromise(
            BattleRoyaleGameState state) {
        promises.propose(state, 1L, 2L,
                new BattlePromiseDecision("GIVE_RESOURCE", "MEDKIT", 1, 4), List.of(1L, 2L));
        var promise = state.getPromises().get(0);
        promises.accept(state, 2L, 1L, promise.getPromiseId(), List.of(1L, 2L));
        return promise;
    }

    private com.zn.agentarena.modules.battle.trade.BattleTradeProposal proposeTrade(BattleRoyaleGameState state) {
        trades.propose(state, 1L, 2L, tradeDecision(), List.of(1L, 2L));
        return state.getTradeProposals().get(0);
    }

    private BattleTradeDecision tradeDecision() {
        return new BattleTradeDecision(List.of(new BattleTradeItemDecision("FOOD", 2)),
                List.of(new BattleTradeItemDecision("MEDKIT", 1)));
    }

    private BattleRoyaleGameState tradeState() {
        BattleRoyaleGameState state = state(player(1L, "A"), player(2L, "A"), player(3L, "E"));
        state.findPlayer(1L).setFood(4);
        state.findPlayer(2L).setFood(1);
        state.findPlayer(2L).setMedkits(1);
        return state;
    }

    private boolean memoryType(BattleRoyaleGameState state, Long playerId, BattleMemoryType type) {
        return state.getSocialStates().get(playerId).getMemory().getEntries().stream()
                .anyMatch(item -> item.getType() == type);
    }

    private ModelGateway decisionGateway(BattleCandidateDecision decision) {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(BattleCandidateDecision.class))).thenReturn(decision);
        return gateway;
    }

    private ModelRuntimeConfig runtimeConfig() {
        return new ModelRuntimeConfig(ModelProvider.OPENAI, "test", null,
                "test-key", 0.2, 256, Duration.ofSeconds(5), 0);
    }

    private BattleRoyaleGameState state(BattlePlayerState... players) {
        BattleRoyaleGameState state = BattleRoyaleGameState.builder().gameId(77L).roundNo(1).maxRounds(20)
                .randomSeed(42L).safeRegions(List.of("A", "B", "C", "D", "E"))
                .players(new ArrayList<>(List.of(players))).build();
        initializer.ensure(state);
        return state;
    }

    private BattlePlayerState player(Long id, String region) {
        return BattlePlayerState.builder().playerId(id).displayName(id + "号")
                .region(region).health(100).food(5).water(5).alive(true).build();
    }
}
