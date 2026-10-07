package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.agent.BattleAgentContext;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.social.BattleRelationshipState;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 本地与模型回退策略共用的有限信息社会决策；模型成功时不使用。 */
public class BattleHeuristicSocialCandidateSelector {
    public BattleSocialDecisionCandidate select(BattleSocialDecisionCandidateSet set,
                                                BattleAgentContext context, BattleRoyaleAction tactical) {
        var self = context.observation().self();
        List<BattleSocialDecisionCandidate> options = set.candidates().stream()
                .filter(c -> compatible(c, context, tactical)).toList();
        if (self.lifeStatus() == BattlePlayerLifeStatus.DOWNED) return none(set);
        for (String type : List.of("FULFILL_PROMISE", "ACCEPT_PROMISE", "ACCEPT_ALLIANCE", "ACCEPT_TRADE",
                "ACCEPT_INFORMATION_TRADE", "CONTRIBUTE_OBJECTIVE")) {
            var selected = options.stream().filter(c -> c.decision() != null && type.equals(c.decision().type()))
                    .max(Comparator.comparingInt(c -> c.decision().objectiveContribution() == null ? 0
                            : c.decision().objectiveContribution().amount())).orElse(null);
            if (selected != null) return selected;
        }
        if (context.social().rumors().isEmpty() && !context.observation().visiblePlayers().isEmpty()
                && context.observation().publicWorld().roundNo() % 3 == 0) {
            var rumor = options.stream().filter(c -> c.decision() != null
                    && "SHARE_RUMOR".equals(c.decision().type())).findFirst().orElse(null);
            if (rumor != null) return rumor;
        }
        // 轮换主动议题，避免所有玩家永远只提出同一种邀请。
        List<String> topics = List.of("PROPOSE_ALLIANCE", "PROPOSE_TRADE", "PROPOSE_PROMISE",
                "PROPOSE_INFORMATION_TRADE");
        int offset = Math.floorMod(context.observation().publicWorld().roundNo() - 1, topics.size());
        for (int i = 0; i < topics.size(); i++) {
            String type = topics.get((offset + i) % topics.size());
            var selected = options.stream().filter(c -> c.decision() != null && type.equals(c.decision().type()))
                    .findFirst().orElse(null);
            if (selected != null) return selected;
        }
        if (!context.observation().visiblePlayers().isEmpty()
                && context.observation().publicWorld().roundNo() % 3 == 0) {
            var rumor = options.stream().filter(c -> c.decision() != null
                    && "SHARE_RUMOR".equals(c.decision().type())).findFirst().orElse(null);
            if (rumor != null) return rumor;
        }
        return none(set);
    }

    private boolean compatible(BattleSocialDecisionCandidate candidate, BattleAgentContext context,
                               BattleRoyaleAction tactical) {
        var d = candidate.decision();
        if (!BattleSocialCandidateCompatibility.allows(candidate, context, tactical)) return false;
        if (d == null) return true;
        var self = context.observation().self();
        String destination = tactical.type() == BattleActionType.MOVE ? tactical.targetRegion() : self.region();
        if (Objects.equals(d.targetPlayerId(), tactical.targetPlayerId())
                && (tactical.type() == BattleActionType.ATTACK || tactical.type() == BattleActionType.EXECUTE)) return false;
        if (d.targetPlayerId() != null) {
            BattleRelationshipState relationship = context.relationships().get(d.targetPlayerId());
            if (relationship != null && (relationship.getTrust() < 40 || relationship.getResentment() > 60)) return false;
        }
        if ("FULFILL_PROMISE".equals(d.type()) || "ACCEPT_TRADE".equals(d.type())
                || "ACCEPT_INFORMATION_TRADE".equals(d.type())) {
            if (context.observation().visiblePlayers().stream().noneMatch(p -> Objects.equals(p.playerId(), d.targetPlayerId())
                    && Objects.equals(destination, p.currentObservedRegion())
                    && p.lifeStatus() == BattlePlayerLifeStatus.ALIVE)) return false;
        }
        if (d.objectiveContribution() != null && context.social().cooperativeObjectives().stream()
                .noneMatch(o -> Objects.equals(o.getObjectiveId(), d.objectiveContribution().objectiveId())
                        && Objects.equals(o.getRegion(), destination))) return false;
        if (d.trade() != null && context.social().tradeProposals().stream().anyMatch(t ->
                Objects.equals(t.getProposerPlayerId(), self.playerId())
                        && Objects.equals(t.getTargetPlayerId(), d.targetPlayerId())
                        && t.getStatus().name().equals("PROPOSED"))) return false;
        if (d.informationTrade() != null && context.social().informationTradeProposals().stream().anyMatch(t ->
                Objects.equals(t.getSellerPlayerId(), self.playerId())
                        && Objects.equals(t.getBuyerPlayerId(), d.targetPlayerId())
                        && t.getStatus().name().equals("PROPOSED"))) return false;
        return true;
    }

    private BattleSocialDecisionCandidate none(BattleSocialDecisionCandidateSet set) {
        return set.candidates().stream().filter(c -> c.decision() == null).findFirst().orElseThrow();
    }
}
