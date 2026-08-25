package com.zn.agentarena.modules.battle.simulation.trace;

public record BattleTraceDecision(String action, Long targetPlayerId, String targetRegion,
                                  String socialAction, Long socialTargetPlayerId, String socialReferenceId,
                                  String communicationChannel, Long communicationTargetPlayerId,
                                  String reasonSummary, int confidence, String source) {
}
