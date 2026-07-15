package com.zn.agentarena.modules.battle.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 大逃杀玩家状态。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattlePlayerState {
    private Long playerId;
    private String displayName;
    private String region;
    private int health;
    private int food;
    private int water;
    private boolean armed;
    private String weaponName;
    private int weaponLevel;
    private int ammo;
    private int armor;
    private int medkits;
    private int searchesWithoutWeapon;
    private String lastAction;
    private int repeatedActions;
    private int kills;
    private int totalDamage;
    private boolean hidden;
    private boolean alive;
}
