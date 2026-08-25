package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.agent.BattleAgentContext;
import com.zn.agentarena.modules.battle.domain.BattleActionType;
import com.zn.agentarena.modules.battle.domain.BattlePlayerLifeStatus;
import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.perception.BattleObservationSystem;
import com.zn.agentarena.modules.battle.social.BattleSocialContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BattleDecisionCandidateGeneratorTest {
    private final BattleDecisionCandidateGenerator generator = new BattleDecisionCandidateGenerator();

    @Test
    void generatesAllConcreteObservableLegalActionsDeterministically() {
        Fixture fixture = fixture();
        fixture.actor.setHealth(20);
        fixture.actor.setMedkits(2);
        fixture.actor.setArmed(true);
        fixture.actor.setWeaponName("weapon");
        fixture.actor.setAmmo(5);
        fixture.target.markDowned(1, 3, null);
        fixture.state.setSupplyDropRegion("A");

        BattleDecisionCandidateSet first = generate(fixture);
        BattleDecisionCandidateSet second = generate(fixture);

        assertEquals(first, second);
        assertEquals(first.candidates().size(), new HashSet<>(first.candidates().stream()
                .map(BattleDecisionCandidate::candidateId).toList()).size());
        assertEquals(List.of("B", "C"), first.candidates().stream()
                .filter(candidate -> candidate.actionType() == BattleActionType.MOVE)
                .map(BattleDecisionCandidate::targetRegion).toList());
        assertSemantic(first, "SEARCH_CURRENT_REGION", BattleActionType.SEARCH);
        assertSemantic(first, "CAPTURE_SUPPLY_DROP", BattleActionType.CAPTURE);
        assertSemantic(first, "REST_SELF", BattleActionType.REST);
        assertSemantic(first, "HIDE_SELF", BattleActionType.HIDE);
        assertSemantic(first, "HEAL_SELF", BattleActionType.HEAL);
        assertSemantic(first, "GUARD_SELF", BattleActionType.GUARD);
        assertSemantic(first, "REVIVE_P2", BattleActionType.REVIVE);
        assertSemantic(first, "EXECUTE_P2", BattleActionType.EXECUTE);
        assertSemantic(first, "SKIP", BattleActionType.SKIP);
        BattleDecisionCandidate heal = candidate(first, "HEAL_SELF");
        assertNull(heal.targetPlayerId());
        assertTrue(heal.effectSummary().contains("治疗自己"));
        assertTrue(heal.costSummary().contains("MEDKIT x1"));
        assertTrue(heal.costSummary().contains("FOOD x1 + WATER x1"));
    }

    @Test
    void generatesOneAttackPerVisibleAliveTarget() {
        Fixture fixture = fixture();
        BattlePlayerState third = player(3L, "A");
        fixture.state.getPlayers().add(third);

        List<Long> targets = generate(fixture).candidates().stream()
                .filter(candidate -> candidate.actionType() == BattleActionType.ATTACK)
                .map(BattleDecisionCandidate::targetPlayerId).toList();

        assertEquals(List.of(2L, 3L), targets);
    }

    @Test
    void hiddenAndUnknownPlayersNeverAppearInCandidatesOrDescriptions() {
        Fixture fixture = fixture();
        fixture.target.setHidden(true);
        BattlePlayerState unknown = player(3L, "E");
        fixture.state.getPlayers().add(unknown);

        BattleDecisionCandidateSet set = generate(fixture);
        String rendered = set.candidates().toString();

        assertTrue(set.candidates().stream().noneMatch(candidate -> candidate.targetPlayerId() != null));
        assertFalse(rendered.contains("P2"));
        assertFalse(rendered.contains("P3"));
        assertFalse(rendered.contains("hidden"));
    }

    @Test
    void noMedkitPreventsHealAndReviveButNotExecute() {
        Fixture fixture = fixture();
        fixture.actor.setHealth(20);
        fixture.actor.setMedkits(0);
        fixture.target.markDowned(1, 3, null);

        BattleDecisionCandidateSet set = generate(fixture);

        assertTrue(set.candidates().stream().noneMatch(candidate -> candidate.actionType() == BattleActionType.HEAL));
        assertTrue(set.candidates().stream().noneMatch(candidate -> candidate.actionType() == BattleActionType.REVIVE));
        assertSemantic(set, "EXECUTE_P2", BattleActionType.EXECUTE);
    }

    @Test
    void fullHealthDoesNotOfferWastefulHeal() {
        Fixture fixture = fixture();
        fixture.actor.setHealth(100);
        fixture.actor.setMedkits(1);
        assertTrue(generate(fixture).candidates().stream()
                .noneMatch(candidate -> candidate.actionType() == BattleActionType.HEAL));
    }

    @Test
    void downedActorReceivesOnlySkip() {
        Fixture fixture = fixture();
        fixture.actor.markDowned(1, 3, 2L);
        BattleDecisionCandidateSet set = generate(fixture);
        assertEquals(1, set.candidates().size());
        assertEquals(BattleActionType.SKIP, set.candidates().get(0).actionType());
    }

    private BattleDecisionCandidateSet generate(Fixture fixture) {
        return generator.generate(fixture.state, fixture.actor, context(fixture.state, fixture.actor));
    }

    private BattleAgentContext context(BattleRoyaleGameState state, BattlePlayerState actor) {
        var observation = new BattleObservationSystem().observe(state, actor.getPlayerId());
        return new BattleAgentContext(null, observation, List.of(), Map.of(), List.of(),
                new BattleSocialContext(List.of(), List.of(), List.of()), observation.publicWorld().gameRules());
    }

    private Fixture fixture() {
        BattlePlayerState actor = player(1L, "A");
        BattlePlayerState target = player(2L, "A");
        BattleRoyaleGameState state = BattleRoyaleGameState.builder().gameId(71L).sequenceNo(9).roundNo(1)
                .maxRounds(20).randomSeed(71L).regions(new ArrayList<>(List.of("A", "B", "C", "D", "E")))
                .safeRegions(new ArrayList<>(List.of("A", "B", "C", "D", "E")))
                .safeRegion("A").players(new ArrayList<>(List.of(actor, target))).build();
        return new Fixture(state, actor, target);
    }

    private BattlePlayerState player(Long id, String region) {
        return BattlePlayerState.builder().playerId(id).displayName("P" + id).region(region).health(100)
                .food(5).water(5).alive(true).lifeStatus(BattlePlayerLifeStatus.ALIVE).build();
    }

    private BattleDecisionCandidate candidate(BattleDecisionCandidateSet set, String semantic) {
        return set.candidates().stream().filter(candidate -> semantic.equals(candidate.semanticName()))
                .findFirst().orElseThrow();
    }

    private void assertSemantic(BattleDecisionCandidateSet set, String semantic, BattleActionType type) {
        assertEquals(type, candidate(set, semantic).actionType());
    }

    private record Fixture(BattleRoyaleGameState state, BattlePlayerState actor, BattlePlayerState target) { }
}
