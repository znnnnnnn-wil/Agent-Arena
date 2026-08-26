package com.zn.agentarena.modules.battle.social;

import java.util.Map;

/** 一次由确定性规则产生的主观关系变化。 */
public record BattleRelationshipChange(
        Long observerPlayerId,
        Long subjectPlayerId,
        String reason,
        Map<String, Integer> before,
        Map<String, Integer> after
) {
}
