package com.zn.agentarena.modules.battle.perception;

import com.zn.agentarena.modules.battle.domain.BattlePlayerLifeStatus;

/** 当前确实进入智能体视野的其他玩家；不包含精确生命和装备细节。 */
public record ObservedBattlePlayer(
        Long playerId,
        String name,
        String currentObservedRegion,
        BattleHealthStatus healthStatus,
        boolean obviousWeapon,
        boolean exposed,
        BattlePlayerLifeStatus lifeStatus,
        Integer bleedOutRound
) {
}
