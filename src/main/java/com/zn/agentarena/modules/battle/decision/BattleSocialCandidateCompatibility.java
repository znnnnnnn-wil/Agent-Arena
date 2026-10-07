package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.agent.BattleAgentContext;
import com.zn.agentarena.modules.battle.domain.*;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/** 仅依据已知信息检查战术后的距离和资源，不把随机搜索收益计入预算。 */
public final class BattleSocialCandidateCompatibility {
    private BattleSocialCandidateCompatibility() { }

    public static boolean allows(BattleSocialDecisionCandidate candidate, BattleAgentContext context,
                                 BattleRoyaleAction tactical) {
        var d = candidate.decision();
        if (d == null) return true;
        var self = context.observation().self();
        if (self.lifeStatus() != BattlePlayerLifeStatus.ALIVE) return false;
        String region = tactical.type() == BattleActionType.MOVE ? tactical.targetRegion() : self.region();
        if (List.of("FULFILL_PROMISE", "ACCEPT_TRADE", "ACCEPT_INFORMATION_TRADE").contains(d.type())
                && context.observation().visiblePlayers().stream().anyMatch(p ->
                Objects.equals(p.playerId(), d.targetPlayerId()) && !Objects.equals(region, p.currentObservedRegion()))) return false;
        EnumMap<BattleResourceType, Integer> available = new EnumMap<>(BattleResourceType.class);
        available.put(BattleResourceType.FOOD, Math.max(0, self.food() - BattleRoyaleRules.METABOLISM_COST));
        available.put(BattleResourceType.WATER, Math.max(0, self.water() - BattleRoyaleRules.METABOLISM_COST));
        available.put(BattleResourceType.MEDKIT, self.medkits()
                - (tactical.type() == BattleActionType.HEAL || tactical.type() == BattleActionType.REVIVE ? 1 : 0));
        available.put(BattleResourceType.AMMO, self.ammo()
                - (tactical.type() == BattleActionType.ATTACK && self.armed() && self.ammo() > 0 ? 1 : 0));
        var contribution = d.objectiveContribution();
        if (contribution != null) {
            if (context.social().cooperativeObjectives().stream().noneMatch(o ->
                    Objects.equals(o.getObjectiveId(), contribution.objectiveId()) && Objects.equals(o.getRegion(), region))) return false;
            return available.getOrDefault(BattleResourceType.valueOf(contribution.resourceType()), 0) >= contribution.amount();
        }
        if ("FULFILL_PROMISE".equals(d.type())) return context.social().promises().stream()
                .filter(p -> Objects.equals(p.getPromiseId(), d.referenceId()))
                .anyMatch(p -> available.getOrDefault(p.getResourceType(), 0) >= p.getAmount());
        if ("ACCEPT_TRADE".equals(d.type())) return context.social().tradeProposals().stream()
                .filter(t -> Objects.equals(t.getTradeId(), d.referenceId())).anyMatch(t -> canPay(available, t.getRequest()));
        if ("ACCEPT_INFORMATION_TRADE".equals(d.type())) return context.social().informationTradeProposals().stream()
                .filter(t -> Objects.equals(t.getTradeId(), d.referenceId())).anyMatch(t -> canPay(available, List.of(t.getPrice())));
        if (d.trade() != null) return d.trade().offer().stream().allMatch(item ->
                available.getOrDefault(BattleResourceType.valueOf(item.resourceType()), 0) >= item.amount());
        return true;
    }

    private static boolean canPay(EnumMap<BattleResourceType, Integer> available, List<BattleResourceAmount> price) {
        return price.stream().allMatch(item -> available.getOrDefault(item.resourceType(), 0) >= item.amount());
    }
}
