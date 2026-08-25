package com.zn.agentarena.modules.battle.simulation.trace;

import java.util.List;

public record BattleInformationBrokerOpportunityTruth(List<String> knownRumorIds,
                                                       List<Long> visiblePotentialBuyers,
                                                       boolean hasInformation,
                                                       boolean hasBuyerEncounter) {
}
