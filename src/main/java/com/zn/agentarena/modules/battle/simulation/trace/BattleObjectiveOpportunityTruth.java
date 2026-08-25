package com.zn.agentarena.modules.battle.simulation.trace;

public record BattleObjectiveOpportunityTruth(String objectiveId, String region, int deadlineRound,
                                              boolean knownInContext, boolean inRegion,
                                              boolean hasNeededResource, boolean canContribute,
                                              int contributed, int required) {
}
