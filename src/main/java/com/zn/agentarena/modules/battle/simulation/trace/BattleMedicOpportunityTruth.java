package com.zn.agentarena.modules.battle.simulation.trace;

import java.util.List;

public record BattleMedicOpportunityTruth(List<Long> visibleDownedPlayerIds, boolean hasMedkit,
                                          List<Long> legalReviveTargets) {
}
