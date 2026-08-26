package com.zn.agentarena.modules.battle.simulation.trace;

import com.zn.agentarena.modules.battle.domain.BattlePlayerLifeStatus;

/** Simulation/debug trace. It is deliberately not a game event and is never exposed by public APIs. */
public record BattleAgentDecisionTrace(
        Long simulationId,
        long seed,
        int round,
        Long actorPlayerId,
        String persona,
        BattlePlayerLifeStatus lifeStatus,
        String region,
        BattleAgentKnownState agentKnownState,
        BattleServerTruth serverTruth,
        BattleTraceDecision decision
) {
}
