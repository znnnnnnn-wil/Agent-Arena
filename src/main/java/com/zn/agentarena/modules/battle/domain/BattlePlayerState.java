package com.zn.agentarena.modules.battle.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zn.agentarena.modules.agent.domain.AgentPersonaSnapshot;
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
    /** 玩家ID */
    private Long playerId;
    /** 玩家显示名称 */
    private String displayName;
    /** 玩家人格快照 */
    private AgentPersonaSnapshot persona;
    /** 当前区域 */
    private String region;
    /** 生命值 */
    private int health;
    /** 食物资源 */
    private int food;
    /** 饮水资源 */
    private int water;
    /** 是否持有武器 */
    private boolean armed;
    /** 武器名称 */
    private String weaponName;
    /** 武器等级 */
    private int weaponLevel;
    /** 弹药数量 */
    private int ammo;
    /** 护甲值 */
    private int armor;
    /** 防守的临时减伤，下一次受击或自己下次行动时失效。 */
    private boolean guarded;
    /** 医疗包数量 */
    private int medkits;
    /** 未获得武器的搜索次数 */
    private int searchesWithoutWeapon;
    /** 最近执行的动作 */
    private String lastAction;
    /** 连续重复动作次数 */
    private int repeatedActions;
    /** 淘汰数量 */
    private int kills;
    /** 累计计分有效伤害；过量伤害和超过目标终身100点的伤害不累计。 */
    private int totalDamage;
    /** 本局已分配给攻击者的计分伤害，救援或治疗不重置，以防循环刷分。 */
    private int scoredDamageReceived;
    /** 是否处于隐藏状态 */
    private boolean hidden;
    /** 是否因长期避战而暴露，暴露时承受额外伤害。 */
    private boolean exposed;
    /** 最近一次攻击自己的玩家ID，用于反击与仇恨决策。 */
    private Long lastAttackerPlayerId;
    /** 是否存活 */
    private boolean alive;
    /** 显式生命阶段；旧快照缺失时由 alive 字段兼容推导。 */
    private BattlePlayerLifeStatus lifeStatus;
    /** 进入倒地状态的轮次。 */
    private Integer downedAtRound;
    /** 到达该轮次仍未获救时自然淘汰。 */
    private Integer bleedOutRound;
    /** 导致本次倒地的玩家；环境伤害时为空。 */
    private Long downedByPlayerId;

    @JsonIgnore
    public BattlePlayerLifeStatus effectiveLifeStatus() {
        return lifeStatus == null ? (alive ? BattlePlayerLifeStatus.ALIVE : BattlePlayerLifeStatus.ELIMINATED)
                : lifeStatus;
    }

    @JsonIgnore
    public boolean isActive() {
        return effectiveLifeStatus() == BattlePlayerLifeStatus.ALIVE;
    }

    @JsonIgnore
    public boolean isDowned() {
        return effectiveLifeStatus() == BattlePlayerLifeStatus.DOWNED;
    }

    @JsonIgnore
    public boolean isEliminated() {
        return effectiveLifeStatus() == BattlePlayerLifeStatus.ELIMINATED;
    }

    public void markAlive(int restoredHealth) {
        setLifeStatus(BattlePlayerLifeStatus.ALIVE);
        setAlive(true);
        setHealth(Math.max(1, restoredHealth));
        setDownedAtRound(null);
        setBleedOutRound(null);
        setDownedByPlayerId(null);
    }

    public void markDowned(int roundNo, int bleedOutAfterRounds, Long attackerPlayerId) {
        setLifeStatus(BattlePlayerLifeStatus.DOWNED);
        setAlive(false);
        setHealth(0);
        setHidden(false);
        setDownedAtRound(roundNo);
        setBleedOutRound(roundNo + Math.max(1, bleedOutAfterRounds));
        setDownedByPlayerId(attackerPlayerId);
    }

    public void markEliminated() {
        setLifeStatus(BattlePlayerLifeStatus.ELIMINATED);
        setAlive(false);
        setHealth(Math.min(0, getHealth()));
        setDownedAtRound(null);
        setBleedOutRound(null);
    }
}
