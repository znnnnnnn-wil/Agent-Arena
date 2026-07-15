package com.zn.agentarena.modules.game.engine;

/**
 * 游戏行动标记接口。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public interface GameAction {

    /**
     * 获取行动玩家ID。
     *
     * @return 行动玩家ID
     */
    Long actorPlayerId();

    /**
     * 获取行动类型。
     *
     * @return 行动类型
     */
    String actionType();
}
