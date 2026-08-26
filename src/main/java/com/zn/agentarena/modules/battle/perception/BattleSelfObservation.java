package com.zn.agentarena.modules.battle.perception;

import com.zn.agentarena.modules.battle.domain.BattlePlayerLifeStatus;

/** 当前智能体可以完整获知的自身状态。 */
public record BattleSelfObservation(
        Long playerId,
        String name,
        String region,
        int health,
        int food,
        int water,
        boolean armed,
        String weaponName,
        int weaponLevel,
        int ammo,
        int armor,
        int medkits,
        boolean hidden,
        boolean exposed,
        int kills,
        String lastAction,
        int repeatedActions,
        BattlePlayerLifeStatus lifeStatus,
        Integer bleedOutRound
) {
}
