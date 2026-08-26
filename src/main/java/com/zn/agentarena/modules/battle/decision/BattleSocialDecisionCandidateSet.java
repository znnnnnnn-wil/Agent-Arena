package com.zn.agentarena.modules.battle.decision;

import java.util.List;

/** 与当前玩家、回合及状态序号绑定的临时社会候选集。 */
public record BattleSocialDecisionCandidateSet(
        Long gameId,
        int round,
        long sequenceNo,
        Long actorPlayerId,
        List<BattleSocialDecisionCandidate> candidates
) { }
