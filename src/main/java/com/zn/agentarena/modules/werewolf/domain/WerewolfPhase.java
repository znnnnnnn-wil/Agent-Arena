package com.zn.agentarena.modules.werewolf.domain;

/**
 * 狼人杀游戏阶段。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public enum WerewolfPhase {
    /** 夜晚狼人行动阶段。 */
    NIGHT_WOLF,
    /** 夜晚守卫保护阶段。 */
    NIGHT_GUARD,
    /** 夜晚预言家查验阶段。 */
    NIGHT_SEER,
    /** 夜晚女巫行动阶段。 */
    NIGHT_WITCH,
    /** 白天讨论阶段。 */
    DAY_DISCUSSION,
    /** 白天投票阶段。 */
    DAY_VOTE,
    /** 对局结束阶段。 */
    FINISHED
}
