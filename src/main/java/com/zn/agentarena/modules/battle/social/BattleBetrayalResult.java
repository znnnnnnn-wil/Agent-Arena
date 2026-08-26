package com.zn.agentarena.modules.battle.social;

import com.zn.agentarena.modules.game.engine.EngineEvent;

import java.util.List;

/** 从真实攻击或处决推导出的联盟背叛后果。 */
public record BattleBetrayalResult(
        boolean betrayed,
        List<Long> witnesses,
        List<EngineEvent> events,
        List<BattleRelationshipChange> relationshipChanges
) {
    public static BattleBetrayalResult none() {
        return new BattleBetrayalResult(false, List.of(), List.of(), List.of());
    }
}
