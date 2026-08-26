package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.agent.BattleAgentContext;
import com.zn.agentarena.modules.battle.agent.BattleAgentContextBuilder;
import com.zn.agentarena.modules.battle.agent.BattleAiGameRunner;
import com.zn.agentarena.modules.battle.domain.BattleActionType;
import com.zn.agentarena.modules.battle.domain.BattlePlayerLifeStatus;
import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.perception.BattleObservationSystem;
import com.zn.agentarena.modules.battle.social.BattleSocialContext;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BattleDecisionCandidateResolverTest {
    private final BattleDecisionCandidateGenerator generator = new BattleDecisionCandidateGenerator();
    private final BattleDecisionCandidateResolver resolver = new BattleDecisionCandidateResolver();

    @Test
    void resolvesHealAttackAndMoveWithoutAllowingParameterOverrides() {
        Fixture fixture = fixture();
        fixture.actor.setHealth(20);
        fixture.actor.setMedkits(2);
        BattleDecisionCandidateSet set = generate(fixture);

        var heal = resolve(set, semantic(set, "HEAL_SELF").candidateId(), fixture);
        var attack = resolve(set, semantic(set, "ATTACK_P2").candidateId(), fixture);
        var move = resolve(set, semantic(set, "MOVE_B").candidateId(), fixture);

        assertEquals(BattleActionType.HEAL, heal.action().type());
        assertNull(heal.action().targetPlayerId());
        assertEquals(2L, attack.action().targetPlayerId());
        assertEquals("B", move.action().targetRegion());
        assertTrue(heal.valid() && attack.valid() && move.valid());
    }

    @Test
    void invalidIdAlwaysBecomesSkipAndReasonCannotInfluenceResolver() {
        Fixture fixture = fixture();
        BattleDecisionCandidateSet set = generate(fixture);

        var result = resolve(set, "T999", fixture);

        assertEquals(BattleCandidateResolutionCode.INVALID_CANDIDATE_SELECTION, result.code());
        assertEquals(BattleActionType.SKIP, result.action().type());
        assertNull(result.action().targetPlayerId());
    }

    @Test
    void staleCandidateBecomesSkipWithoutSecondSelection() {
        Fixture fixture = fixture();
        BattleDecisionCandidateSet set = generate(fixture);
        fixture.state.setSequenceNo(fixture.state.getSequenceNo() + 1);

        var result = resolve(set, set.candidates().get(0).candidateId(), fixture);

        assertEquals(BattleCandidateResolutionCode.CANDIDATE_STALE, result.code());
        assertEquals(BattleActionType.SKIP, result.action().type());
    }

    @Test
    void candidateCannotCrossTurnOrPlayer() {
        Fixture fixture = fixture();
        BattleDecisionCandidateSet set = generate(fixture);
        fixture.state.setRoundNo(2);
        assertEquals(BattleCandidateResolutionCode.CANDIDATE_STALE,
                resolver.resolve(set, set.candidates().get(0).candidateId(), fixture.state, fixture.actor).code());

        fixture.state.setRoundNo(1);
        assertEquals(BattleCandidateResolutionCode.CANDIDATE_STALE,
                resolver.resolve(set, set.candidates().get(0).candidateId(), fixture.state, fixture.target).code());
    }

    @Test
    void productionPromptUsesCandidateContractAndMakesHealSelfExplicit() {
        Fixture fixture = fixture();
        fixture.actor.setHealth(20);
        fixture.actor.setMedkits(2);
        BattleAgentContext context = context(fixture.state, fixture.actor);
        BattleDecisionCandidateSet set = generator.generate(fixture.state, fixture.actor, context);

        String prompt = new BattleAgentContextBuilder().build(context, set);

        assertTrue(prompt.contains("CURRENT TACTICAL OPTIONS"));
        assertTrue(prompt.contains("HEAL_SELF"));
        assertTrue(prompt.contains("target: SELF"));
        assertTrue(prompt.contains("Choose exactly one tacticalCandidateId"));
        assertTrue(prompt.contains("tacticalCandidateId 必须非空"));
        assertTrue(prompt.contains("\"tacticalCandidateId\":\"T01\""));
        assertTrue(prompt.contains("\"socialCandidateId\":\"S01\""));
        assertFalse(prompt.contains("\"action\":\"SEARCH\""));
        assertFalse(prompt.contains("\"targetRegion\":"));
    }

    @Test
    void realModelPathPreservesSelectedMoveAndKeepsCandidateDataInternal() {
        Fixture fixture = fixture();
        ModelGateway gateway = new ModelGateway() {
            @Override public String generate(ModelRuntimeConfig config, String prompt) { return "{}"; }
            @Override public <T> T generateStructured(ModelRuntimeConfig config, String prompt, Class<T> type) {
                return type.cast(new BattleCandidateDecision("T01", null, null,
                        "选择候选，不修改其区域", 90));
            }
        };
        ModelRuntimeConfig config = new ModelRuntimeConfig(ModelProvider.QWEN, "test", null, "secret",
                0.0, 100, Duration.ofSeconds(1), 0);

        var result = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder())
                .runNextTurn(fixture.state, fixture.actor, config);

        assertEquals("MOVE", result.events().get(0).payload().get("action"));
        assertEquals("B", result.events().get(0).payload().get("targetRegion"));
        assertEquals("T01", result.events().get(0).payload().get("selectedCandidateId"));
        assertFalse(result.events().get(0).publicPayload().containsKey("selectedCandidateId"));
        assertFalse(result.events().get(0).publicPayload().containsKey("candidateResolution"));
    }

    private BattleCandidateResolution resolve(BattleDecisionCandidateSet set, String id, Fixture fixture) {
        return resolver.resolve(set, id, fixture.state, fixture.actor);
    }

    private BattleDecisionCandidateSet generate(Fixture fixture) {
        return generator.generate(fixture.state, fixture.actor, context(fixture.state, fixture.actor));
    }

    private BattleAgentContext context(BattleRoyaleGameState state, BattlePlayerState actor) {
        var observation = new BattleObservationSystem().observe(state, actor.getPlayerId());
        return new BattleAgentContext(null, observation, List.of(), Map.of(), List.of(),
                new BattleSocialContext(List.of(), List.of(), List.of()), observation.publicWorld().gameRules());
    }

    private BattleDecisionCandidate semantic(BattleDecisionCandidateSet set, String semanticName) {
        return set.candidates().stream().filter(candidate -> semanticName.equals(candidate.semanticName()))
                .findFirst().orElseThrow();
    }

    private Fixture fixture() {
        BattlePlayerState actor = player(1L, "A");
        actor.setArmed(true);
        actor.setWeaponName("weapon");
        actor.setAmmo(5);
        BattlePlayerState target = player(2L, "A");
        BattleRoyaleGameState state = BattleRoyaleGameState.builder().gameId(72L).sequenceNo(0).roundNo(1)
                .maxRounds(20).randomSeed(72L).regions(new ArrayList<>(List.of("A", "B", "C", "D", "E")))
                .safeRegions(new ArrayList<>(List.of("A", "B", "C", "D", "E"))).safeRegion("A")
                .players(new ArrayList<>(List.of(actor, target))).build();
        return new Fixture(state, actor, target);
    }

    private BattlePlayerState player(Long id, String region) {
        return BattlePlayerState.builder().playerId(id).displayName("P" + id).region(region).health(100)
                .food(5).water(5).alive(true).lifeStatus(BattlePlayerLifeStatus.ALIVE).build();
    }

    private record Fixture(BattleRoyaleGameState state, BattlePlayerState actor, BattlePlayerState target) { }
}
