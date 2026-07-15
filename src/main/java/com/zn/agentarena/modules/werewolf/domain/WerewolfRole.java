package com.zn.agentarena.modules.werewolf.domain;

/**
 * 狼人杀角色。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public enum WerewolfRole {
    /** 狼人阵营角色。 */
    WEREWOLF,
    /** 预言家角色。 */
    SEER,
    /** 女巫角色。 */
    WITCH,
    /** 守卫角色。 */
    GUARD,
    /** 平民角色。 */
    VILLAGER;

    /**
     * 判断当前角色是否属于狼人阵营。
     *
     * @return 属于狼人阵营时返回 {@code true}
     */
    public boolean isWolfCamp() {
        return this == WEREWOLF;
    }
}
