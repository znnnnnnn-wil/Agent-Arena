package com.zn.agentarena.modules.battle.decision;

import java.util.List;

/** 候选仅绑定生成它的游戏、玩家、回合和状态序号，不进入持久化状态。 */
public record BattleDecisionCandidateSet(
        Long gameId,
        int round,
        long sequenceNo,
        Long actorPlayerId,
        List<BattleDecisionCandidate> candidates
) {
    public BattleDecisionCandidateSet {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
