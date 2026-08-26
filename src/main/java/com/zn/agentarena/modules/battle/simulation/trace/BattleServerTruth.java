package com.zn.agentarena.modules.battle.simulation.trace;

import java.util.List;

/** Server-only comparison data. Never merge this structure into agentKnownState or prompts. */
public record BattleServerTruth(
        List<BattlePromiseOpportunityTruth> promiseOpportunities,
        List<BattleObjectiveOpportunityTruth> objectiveOpportunities,
        BattleMedicOpportunityTruth medicOpportunity,
        BattleInformationBrokerOpportunityTruth informationBrokerOpportunity,
        int alivePlayers,
        boolean resourceTight,
        boolean supplyDropConflict,
        boolean lateGame
) {
}
