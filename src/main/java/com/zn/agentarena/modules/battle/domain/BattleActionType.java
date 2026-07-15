package com.zn.agentarena.modules.battle.domain;

/**
 * 大逃杀行动类型。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public enum BattleActionType {
    /** 移动至指定区域。 */
    MOVE,
    /** 搜索当前区域。 */
    SEARCH,
    /** 原地休整。 */
    REST,
    /** 与其他玩家交易。 */
    TRADE,
    /** 攻击目标玩家。 */
    ATTACK,
    /** 隐藏自身状态。 */
    HIDE,
    /** 使用医疗包恢复生命。 */
    HEAL,
    /** 加固防具，降低后续攻击伤害。 */
    GUARD,
    /** 跳过本次行动。 */
    SKIP
}
