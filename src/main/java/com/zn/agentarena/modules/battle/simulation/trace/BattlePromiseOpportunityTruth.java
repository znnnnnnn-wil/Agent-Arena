package com.zn.agentarena.modules.battle.simulation.trace;

public record BattlePromiseOpportunityTruth(String promiseId, Long promiseePlayerId, int deadlineRound,
                                            boolean promiserActive, boolean promiseeActive,
                                            boolean hasResource, boolean sameRegion,
                                            boolean legalFulfillmentOpportunity) {
}
