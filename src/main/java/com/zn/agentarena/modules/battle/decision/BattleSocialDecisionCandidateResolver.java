package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 把模型选择的社会候选 ID 解析为服务器生成的强类型载荷。 */
@Component
public class BattleSocialDecisionCandidateResolver {
    public BattleSocialCandidateResolution resolve(BattleSocialDecisionCandidateSet set, String candidateId,
                                                    BattleRoyaleGameState state, BattlePlayerState actor) {
        if (set == null || state == null || actor == null
                || !Objects.equals(set.gameId(), state.getGameId())
                || set.round() != state.getRoundNo() || set.sequenceNo() != state.getSequenceNo()
                || !Objects.equals(set.actorPlayerId(), actor.getPlayerId())) {
            return new BattleSocialCandidateResolution(false, null, null, "SOCIAL_CANDIDATE_STALE");
        }
        boolean legacyNone = candidateId == null || candidateId.isBlank() || "NONE".equalsIgnoreCase(candidateId);
        BattleSocialDecisionCandidate selected = set.candidates().stream()
                .filter(candidate -> legacyNone
                        ? "NO_SOCIAL_ACTION".equals(candidate.semanticName())
                        : candidate.candidateId().equals(candidateId))
                .findFirst().orElse(null);
        return selected == null
                ? new BattleSocialCandidateResolution(false, null, null, "INVALID_SOCIAL_CANDIDATE")
                : new BattleSocialCandidateResolution(true, selected.decision(),
                        selected.coupledCommunication(), "SELECTED");
    }
}
