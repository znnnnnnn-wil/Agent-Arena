package com.zn.agentarena.modules.werewolf.domain;

import com.zn.agentarena.modules.game.engine.GameAction;

/**
 * 狼人杀玩家行动。
 *
 * @param actorPlayerId 行动玩家ID
 * @param type 行动类型
 * @param targetPlayerId 目标玩家ID
 * @param message 公开发言
 * @author zhangxin
 * @date 2026/07/12
 */
public record WerewolfAction(
        Long actorPlayerId,
        WerewolfActionType type,
        Long targetPlayerId,
        String message
) implements GameAction {

    @Override
    public String actionType() {
        return type.name();
    }
}
