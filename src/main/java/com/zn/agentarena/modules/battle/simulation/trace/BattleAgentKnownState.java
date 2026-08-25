package com.zn.agentarena.modules.battle.simulation.trace;

import java.util.List;
import java.util.Map;

/** A compact, immutable projection of only what the acting agent was allowed to know. */
public record BattleAgentKnownState(
        List<Long> visiblePlayerIds,
        List<Long> visibleDownedPlayerIds,
        Map<String, Integer> selfResources,
        List<String> publicObjectives,
        List<String> socialProposals,
        List<String> activeAlliances,
        List<String> acceptedPromises,
        List<String> pendingPromises,
        List<String> pendingTrades,
        List<String> informationTradeOpportunities,
        List<String> rumors,
        List<String> hiddenGoals,
        int injectedMemoryCount,
        List<String> importantMemoryTypes,
        List<String> relevantMemorySummaries,
        Map<Long, BattleTraceRelationship> involvedRelationships
) {
}
