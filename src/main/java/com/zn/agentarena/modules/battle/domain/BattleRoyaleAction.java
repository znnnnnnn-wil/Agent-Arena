package com.zn.agentarena.modules.battle.domain;

import com.zn.agentarena.modules.game.engine.GameAction;

/**
 * 大逃杀玩家行动。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public record BattleRoyaleAction(
        Long actorPlayerId,
        BattleActionType type,
        Long targetPlayerId,
        String targetRegion
) implements GameAction {
    @Override
    public String actionType() {
        return type.name();
    }
}
