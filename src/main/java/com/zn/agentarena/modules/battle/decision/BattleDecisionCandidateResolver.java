package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.domain.BattleActionType;
import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleAction;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.engine.BattleRoyaleGameEngine;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 把模型选择的临时 ID 解析成不可改写的引擎动作，并按当前服务器状态复验。 */
@Component
public class BattleDecisionCandidateResolver {
    private final BattleRoyaleGameEngine gameEngine = new BattleRoyaleGameEngine();

    public BattleCandidateResolution resolve(BattleDecisionCandidateSet candidateSet,
                                              String selectedCandidateId,
                                              BattleRoyaleGameState currentState,
                                              BattlePlayerState currentActor) {
        if (currentState == null || currentActor == null) {
            throw new IllegalArgumentException("解析候选需要当前游戏状态和玩家");
        }
        BattleRoyaleAction skip = new BattleRoyaleAction(currentActor.getPlayerId(), BattleActionType.SKIP, null, null);
        if (candidateSet == null
                || !Objects.equals(candidateSet.gameId(), currentState.getGameId())
                || candidateSet.round() != currentState.getRoundNo()
                || candidateSet.sequenceNo() != currentState.getSequenceNo()
                || !Objects.equals(candidateSet.actorPlayerId(), currentActor.getPlayerId())) {
            return new BattleCandidateResolution(BattleCandidateResolutionCode.CANDIDATE_STALE,
                    null, skip, false, "候选集不再属于当前玩家或当前状态");
        }
        BattleDecisionCandidate selected = candidateSet.candidates().stream()
                .filter(candidate -> candidate.candidateId().equals(selectedCandidateId))
                .findFirst().orElse(null);
        if (selected == null) {
            return new BattleCandidateResolution(BattleCandidateResolutionCode.INVALID_CANDIDATE_SELECTION,
                    null, skip, false, "模型返回的候选 ID 不存在");
        }
        var validation = gameEngine.validate(currentState, selected.toAction());
        if (!validation.valid()) {
            return new BattleCandidateResolution(BattleCandidateResolutionCode.CANDIDATE_STALE,
                    selected, skip, false, "所选候选在执行前已失效");
        }
        return new BattleCandidateResolution(BattleCandidateResolutionCode.SELECTED,
                selected, selected.toAction(), true, "候选合法并保持原始动作与目标");
    }
}
