package com.zn.agentarena.modules.battle.perception;

import java.util.List;
import java.util.Map;

/** 所有参赛智能体均可获知的规则和公开世界信息。 */
public record BattlePublicWorldObservation(
        int roundNo,
        int maxRounds,
        List<String> safeRegions,
        int ringStage,
        List<String> ringOrder,
        Integer nextShrinkRound,
        int currentZoneDamage,
        List<String> regions,
        Map<String, List<String>> topology,
        String supplyDropRegion,
        Long supplyDropClaimPlayerId,
        int supplyDropClaimActionsRemaining,
        int currentRegionSearchCount,
        List<Long> roundTurnOrder,
        int turnIndex,
        int remainingPlayers,
        List<String> gameRules
) {
}
