package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.agent.BattleAgentContext;
import com.zn.agentarena.modules.battle.domain.BattleActionType;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/** LOCAL fallback 的确定性策略；真实模型路径不会调用本策略。 */
@Component
public class BattleHeuristicCandidateSelector {
    public BattleDecisionCandidate select(BattleDecisionCandidateSet set, BattleAgentContext context) {
        if (set == null || set.candidates().isEmpty()) return null;
        var self = context.observation().self();
        if (self.lifeStatus().name().equals("DOWNED")) return first(set, BattleActionType.SKIP);
        BattleDecisionCandidate selected = first(set, BattleActionType.REVIVE);
        if (selected != null) return selected;
        if (self.health() < 45 && (selected = first(set, BattleActionType.HEAL)) != null) return selected;
        if (!context.observation().publicWorld().safeRegions().contains(self.region())) {
            selected = set.candidates().stream().filter(c -> c.actionType() == BattleActionType.MOVE)
                    .filter(c -> context.observation().publicWorld().safeRegions().contains(c.targetRegion()))
                    .min(Comparator.comparing(BattleDecisionCandidate::candidateId)).orElse(null);
            if (selected != null) return selected;
        }
        if ((selected = first(set, BattleActionType.ATTACK)) != null) return selected;
        if ((selected = first(set, BattleActionType.CAPTURE)) != null) return selected;
        if (self.armor() < 20 && self.armed() && (selected = first(set, BattleActionType.GUARD)) != null) return selected;
        if ((selected = first(set, BattleActionType.SEARCH)) != null) return selected;
        return first(set, BattleActionType.SKIP);
    }

    private BattleDecisionCandidate first(BattleDecisionCandidateSet set, BattleActionType type) {
        return set.candidates().stream().filter(candidate -> candidate.actionType() == type)
                .min(Comparator.comparing(BattleDecisionCandidate::candidateId)).orElse(null);
    }
}
