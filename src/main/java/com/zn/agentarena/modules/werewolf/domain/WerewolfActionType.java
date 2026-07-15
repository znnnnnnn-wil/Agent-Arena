package com.zn.agentarena.modules.werewolf.domain;

/**
 * 狼人杀行动类型。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public enum WerewolfActionType {
    /** 狼人夜晚击杀目标。 */
    WOLF_KILL,
    /** 守卫保护一名玩家。 */
    GUARD_PROTECT,
    /** 预言家查验目标身份。 */
    SEER_CHECK,
    /** 女巫使用解药救人。 */
    WITCH_SAVE,
    /** 女巫使用毒药淘汰目标。 */
    WITCH_POISON,
    /** 白天公开发言。 */
    SPEAK,
    /** 白天投票放逐目标。 */
    VOTE,
    /** 跳过本次行动。 */
    SKIP
}
