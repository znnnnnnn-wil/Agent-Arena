package com.zn.agentarena.modules.turing.agent;

import com.zn.agentarena.modules.turing.domain.TuringHuntActionType;

/** 下一次单步推进需要执行的动作。 */
public record TuringHuntTurnRequirement(
        TuringHuntActionType actionType,
        Long actorPlayerId,
        boolean humanActionRequired
) {
}
