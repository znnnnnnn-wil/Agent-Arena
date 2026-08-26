package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.domain.BattleRoyaleAction;

/** 候选 ID 解析及最终合法性复验结果。 */
public record BattleCandidateResolution(
        BattleCandidateResolutionCode code,
        BattleDecisionCandidate selectedCandidate,
        BattleRoyaleAction action,
        boolean valid,
        String validationReason
) {
    public boolean selected() {
        return code == BattleCandidateResolutionCode.SELECTED;
    }
}
