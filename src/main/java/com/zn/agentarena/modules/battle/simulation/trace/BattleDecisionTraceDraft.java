package com.zn.agentarena.modules.battle.simulation.trace;

import com.zn.agentarena.modules.battle.domain.BattlePlayerLifeStatus;
import java.util.Map;

/** Internal pre-decision snapshot used to prevent post-turn mutation from changing diagnostics. */
public record BattleDecisionTraceDraft(Long simulationId, long seed, int round, Long actorPlayerId,
                                       String persona, BattlePlayerLifeStatus lifeStatus, String region,
                                       BattleAgentKnownState knownStateWithoutRelationships,
                                       Map<Long, BattleTraceRelationship> relationshipCandidates,
                                       BattleServerTruth serverTruth) {
}
