package com.zn.agentarena.modules.battle.domain;

import com.zn.agentarena.modules.game.engine.GameAction;

/**
 * 大逃杀玩家行动。
 *
 * @author zhangxin
 * @date 2026/07/12
  *
  * @param actorPlayerId actorPlayerId 字段
  * @param type type 字段
  * @param targetPlayerId targetPlayerId 字段
  * @param targetRegion targetRegion 字段
  */
public record BattleRoyaleAction(
        Long actorPlayerId,
        BattleActionType type,
        Long targetPlayerId,
        String targetRegion
) implements GameAction {
    /**
     * 返回用于事件记录的动作类型名称。
     *
     * @return 动作类型名称
     */
    @Override
    public String actionType() {
        return type.name();
    }
}
