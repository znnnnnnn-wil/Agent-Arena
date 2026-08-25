package com.zn.agentarena.modules.battle.social;

import com.zn.agentarena.modules.game.engine.EngineEvent;

import java.util.List;

/** 社会动作或周期评估产生的权威事件与关系变化。 */
public record BattleSocialActionResult(
        List<EngineEvent> events,
        List<BattleRelationshipChange> relationshipChanges
) {
    public static BattleSocialActionResult empty() {
        return new BattleSocialActionResult(List.of(), List.of());
    }
}
