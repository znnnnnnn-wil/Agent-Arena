package com.zn.agentarena.modules.battle.communication;

import com.zn.agentarena.modules.game.engine.EngineEvent;

/** 一次可选通信的投递结果；非法通信返回空结果且不影响主行动。 */
public record BattleCommunicationResult(BattleMessage message, EngineEvent event) {
    public static BattleCommunicationResult ignored() {
        return new BattleCommunicationResult(null, null);
    }

    public boolean delivered() {
        return message != null;
    }
}
