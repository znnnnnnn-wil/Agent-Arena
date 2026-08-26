package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.domain.BattleActionType;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleAction;

/** 当前玩家当前回合可选择的一个完整、不可改写的战术动作。 */
public record BattleDecisionCandidate(
        String candidateId,
        BattleActionType actionType,
        Long actorPlayerId,
        Long targetPlayerId,
        String targetRegion,
        String semanticName,
        String effectSummary,
        String costSummary,
        String knownConsequence
) {
    public BattleRoyaleAction toAction() {
        return new BattleRoyaleAction(actorPlayerId, actionType, targetPlayerId, targetRegion);
    }
}
