package com.zn.agentarena.modules.battle.social;

import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.perception.BattleAgentObservation;
import com.zn.agentarena.modules.battle.perception.BattleHealthStatus;
import com.zn.agentarena.modules.battle.perception.ObservedBattlePlayer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 依据某个智能体亲历或看到的事实维护有方向的主观关系。 */
@Component
@RequiredArgsConstructor
public class BattleRelationshipSystem {
    private static final int ATTACK_TRUST_DELTA = -30;
    private static final int ATTACK_RESENTMENT_DELTA = 35;
    private static final int ATTACK_SUSPICION_DELTA = 20;
    private static final int HIGH_DAMAGE_THRESHOLD = 35;
    private static final int HIGH_DAMAGE_FEAR_DELTA = 25;
    private static final int NORMAL_DAMAGE_FEAR_DELTA = 10;
    private static final int GIFT_TRUST_DELTA = 5;
    private static final int GIFT_GRATITUDE_DELTA = 10;
    private static final int ALLIANCE_TRUST_DELTA = 5;
    private static final int PROMISE_FULFILLED_TRUST_DELTA = 15;
    private static final int PROMISE_FULFILLED_GRATITUDE_DELTA = 10;
    private static final int PROMISE_BROKEN_TRUST_DELTA = -35;
    private static final int PROMISE_BROKEN_RESENTMENT_DELTA = 25;
    private static final int PROMISE_BROKEN_SUSPICION_DELTA = 30;
    private static final int BETRAYAL_TRUST_DELTA = -50;
    private static final int BETRAYAL_RESENTMENT_DELTA = 60;
    private static final int BETRAYAL_SUSPICION_DELTA = 35;
    private static final int WITNESS_BETRAYAL_TRUST_DELTA = -10;
    private static final int WITNESS_BETRAYAL_SUSPICION_DELTA = 15;
    private static final int REVIVE_TRUST_DELTA = 25;
    private static final int REVIVE_GRATITUDE_DELTA = 40;

    private final BattleSocialStateInitializer initializer;

    public void updatePerceivedStrength(BattleRoyaleGameState state, BattleAgentObservation observation) {
        for (ObservedBattlePlayer visible : observation.visiblePlayers()) {
            BattleRelationshipState relationship = relationship(state, observation.self().playerId(), visible.playerId());
            int strength = 25;
            if (visible.obviousWeapon()) strength += 40;
            if (visible.healthStatus() == BattleHealthStatus.HEALTHY) strength += 20;
            else if (visible.healthStatus() == BattleHealthStatus.INJURED) strength += 10;
            if (visible.exposed()) strength -= 10;
            relationship.setPerceivedStrength(clamp(strength));
        }
    }

    public BattleRelationshipChange attacked(BattleRoyaleGameState state, Long victimId,
                                              Long attackerId, int damage) {
        BattleRelationshipState relationship = relationship(state, victimId, attackerId);
        Map<String, Integer> before = values(relationship);
        relationship.setTrust(clamp(relationship.getTrust() + ATTACK_TRUST_DELTA));
        relationship.setResentment(clamp(relationship.getResentment() + ATTACK_RESENTMENT_DELTA));
        relationship.setSuspicion(clamp(relationship.getSuspicion() + ATTACK_SUSPICION_DELTA));
        relationship.setFear(clamp(relationship.getFear()
                + (damage >= HIGH_DAMAGE_THRESHOLD ? HIGH_DAMAGE_FEAR_DELTA : NORMAL_DAMAGE_FEAR_DELTA)));
        return new BattleRelationshipChange(victimId, attackerId, "ATTACKED", before, values(relationship));
    }

    public BattleRelationshipChange resourceReceived(BattleRoyaleGameState state, Long recipientId, Long giverId) {
        BattleRelationshipState relationship = relationship(state, recipientId, giverId);
        Map<String, Integer> before = values(relationship);
        relationship.setTrust(clamp(relationship.getTrust() + GIFT_TRUST_DELTA));
        relationship.setGratitude(clamp(relationship.getGratitude() + GIFT_GRATITUDE_DELTA));
        return new BattleRelationshipChange(recipientId, giverId, "RESOURCE_RECEIVED", before, values(relationship));
    }

    public BattleRelationshipChange allianceFormed(BattleRoyaleGameState state, Long observerId, Long allyId) {
        return change(state, observerId, allyId, "ALLIANCE_FORMED",
                ALLIANCE_TRUST_DELTA, 0, 0, 0, 0);
    }

    public BattleRelationshipChange promiseFulfilled(BattleRoyaleGameState state, Long promiseeId, Long promiserId) {
        return change(state, promiseeId, promiserId, "PROMISE_FULFILLED",
                PROMISE_FULFILLED_TRUST_DELTA, 0, PROMISE_FULFILLED_GRATITUDE_DELTA, 0, 0);
    }

    public BattleRelationshipChange promiseBroken(BattleRoyaleGameState state, Long promiseeId, Long promiserId) {
        return change(state, promiseeId, promiserId, "PROMISE_BROKEN",
                PROMISE_BROKEN_TRUST_DELTA, 0, 0,
                PROMISE_BROKEN_RESENTMENT_DELTA, PROMISE_BROKEN_SUSPICION_DELTA);
    }

    public BattleRelationshipChange betrayed(BattleRoyaleGameState state, Long victimId,
                                              Long betrayerId, int damage) {
        BattleRelationshipChange result = change(state, victimId, betrayerId, "ALLIANCE_BETRAYED",
                BETRAYAL_TRUST_DELTA, 0, 0, BETRAYAL_RESENTMENT_DELTA, BETRAYAL_SUSPICION_DELTA);
        BattleRelationshipState relationship = relationship(state, victimId, betrayerId);
        Map<String, Integer> beforeFear = result.before();
        relationship.setFear(clamp(relationship.getFear()
                + (damage >= HIGH_DAMAGE_THRESHOLD ? HIGH_DAMAGE_FEAR_DELTA : NORMAL_DAMAGE_FEAR_DELTA)));
        return new BattleRelationshipChange(victimId, betrayerId, "ALLIANCE_BETRAYED",
                beforeFear, values(relationship));
    }

    public BattleRelationshipChange witnessedBetrayal(BattleRoyaleGameState state, Long witnessId, Long betrayerId) {
        return change(state, witnessId, betrayerId, "WITNESSED_ALLIANCE_BETRAYAL",
                WITNESS_BETRAYAL_TRUST_DELTA, 0, 0, 0, WITNESS_BETRAYAL_SUSPICION_DELTA);
    }

    public BattleRelationshipChange revived(BattleRoyaleGameState state, Long revivedId, Long rescuerId) {
        return change(state, revivedId, rescuerId, "PLAYER_REVIVED",
                REVIVE_TRUST_DELTA, 0, REVIVE_GRATITUDE_DELTA, 0, 0);
    }

    public Map<Long, BattleRelationshipState> relationshipsFor(BattleRoyaleGameState state, Long observerId) {
        return Map.copyOf(initializer.socialState(state, observerId).getRelationships());
    }

    public BattleRelationshipState relationship(BattleRoyaleGameState state, Long observerId, Long subjectId) {
        return initializer.socialState(state, observerId).getRelationships().computeIfAbsent(subjectId,
                ignored -> BattleRelationshipState.builder().build());
    }

    private BattleRelationshipChange change(BattleRoyaleGameState state, Long observerId, Long subjectId,
                                            String reason, int trustDelta, int fearDelta, int gratitudeDelta,
                                            int resentmentDelta, int suspicionDelta) {
        BattleRelationshipState relationship = relationship(state, observerId, subjectId);
        Map<String, Integer> before = values(relationship);
        relationship.setTrust(clamp(relationship.getTrust() + trustDelta));
        relationship.setFear(clamp(relationship.getFear() + fearDelta));
        relationship.setGratitude(clamp(relationship.getGratitude() + gratitudeDelta));
        relationship.setResentment(clamp(relationship.getResentment() + resentmentDelta));
        relationship.setSuspicion(clamp(relationship.getSuspicion() + suspicionDelta));
        return new BattleRelationshipChange(observerId, subjectId, reason, before, values(relationship));
    }

    private Map<String, Integer> values(BattleRelationshipState state) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("trust", state.getTrust());
        result.put("fear", state.getFear());
        result.put("gratitude", state.getGratitude());
        result.put("resentment", state.getResentment());
        result.put("suspicion", state.getSuspicion());
        result.put("perceivedStrength", state.getPerceivedStrength());
        return result;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
