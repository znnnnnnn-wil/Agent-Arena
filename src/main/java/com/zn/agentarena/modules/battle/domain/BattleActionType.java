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
    /** 占领当前区域的空投，需要守住一个行动窗口。 */
    CAPTURE,
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
    /** 消耗医疗包救起同区域倒地玩家。 */
    REVIVE,
    /** 处决同区域倒地玩家。 */
    EXECUTE,
    /** 跳过本次行动。 */
    SKIP
}
