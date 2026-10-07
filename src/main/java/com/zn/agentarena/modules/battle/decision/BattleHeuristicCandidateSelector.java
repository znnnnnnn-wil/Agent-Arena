package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.agent.BattleAgentContext;
import com.zn.agentarena.modules.battle.domain.BattleActionType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;

/** LOCAL fallback 的确定性策略；真实模型路径不会调用本策略。 */
@Component
public class BattleHeuristicCandidateSelector {
    public BattleDecisionCandidate select(BattleDecisionCandidateSet set, BattleAgentContext context) {
        if (set == null || set.candidates().isEmpty()) return null;
        var self = context.observation().self();
        if (self.lifeStatus().name().equals("DOWNED")) return first(set, BattleActionType.SKIP);
        BattleDecisionCandidate selected;
        if (!context.observation().publicWorld().safeRegions().contains(self.region())) {
            selected = moveToward(set, context, context.observation().publicWorld().safeRegions());
            if (selected != null) return selected;
        }
        if (self.health() < 45 && (selected = first(set, BattleActionType.HEAL)) != null) return selected;
        selected = set.candidates().stream().filter(c -> c.actionType() == BattleActionType.REVIVE)
                .filter(c -> ally(context, c.targetPlayerId()) || context.social().hiddenGoals().stream()
                        .anyMatch(g -> g.getStatus().name().equals("ACTIVE") && g.getType().name().equals("FIELD_MEDIC")))
                .findFirst().orElse(null);
        if (selected != null) return selected;
        if ((self.armed() && self.ammo() > 0) || (context.observation().publicWorld().ringStage() >= 4
                && context.observation().publicWorld().remainingPlayers() <= 2)) {
            selected = set.candidates().stream().filter(c -> c.actionType() == BattleActionType.ATTACK)
                    .filter(c -> !ally(context, c.targetPlayerId()) || shouldContestAlly(context))
                    .findFirst().orElse(null);
            if (selected != null) return selected;
        }
        selected = set.candidates().stream().filter(c -> c.actionType() == BattleActionType.EXECUTE)
                .filter(c -> !ally(context, c.targetPlayerId())).findFirst().orElse(null);
        if (selected != null) return selected;
        if ((selected = first(set, BattleActionType.CAPTURE)) != null) return selected;
        if (Objects.equals(context.observation().publicWorld().supplyDropClaimPlayerId(), self.playerId())) {
            if ((selected = first(set, BattleActionType.GUARD)) != null) return selected;
            if ((selected = first(set, BattleActionType.REST)) != null) return selected;
            return first(set, BattleActionType.SKIP);
        }
        if (self.food() > 2 && self.water() > 2) {
            var objective = context.social().cooperativeObjectives().stream()
                    .filter(o -> o.getStatus().name().equals("ACTIVE") && !Objects.equals(o.getRegion(), self.region()))
                    .filter(o -> context.observation().publicWorld().safeRegions().contains(o.getRegion()))
                    .filter(o -> o.getDeadlineRound() > context.observation().publicWorld().roundNo() + 2)
                    .findFirst().orElse(null);
            if (objective != null && (selected = moveToward(set, context, java.util.List.of(objective.getRegion()))) != null) return selected;
        }
        boolean nearbyThreat = context.observation().visiblePlayers().stream()
                .anyMatch(player -> player.lifeStatus().name().equals("ALIVE") && !ally(context, player.playerId()));
        if (nearbyThreat && self.food() > 2 && self.water() > 2 && self.armor() < 12 && self.armed() && (selected = first(set, BattleActionType.GUARD)) != null) return selected;
        if (self.health() < 70 && (selected = first(set, BattleActionType.REST)) != null) return selected;
        if ((selected = first(set, BattleActionType.SEARCH)) != null) return selected;
        if (self.health() < 90 && (selected = first(set, BattleActionType.HEAL)) != null) return selected;
        if (nearbyThreat && (selected = first(set, BattleActionType.GUARD)) != null) return selected;
        if (!self.exposed() && (selected = first(set, BattleActionType.HIDE)) != null) return selected;
        return first(set, BattleActionType.SKIP);
    }

    private boolean ally(BattleAgentContext context, Long target) {
        return context.social().alliances().stream().anyMatch(a -> a.getStatus().name().equals("ACTIVE")
                && a.includes(context.observation().self().playerId()) && a.includes(target));
    }

    private boolean shouldContestAlly(BattleAgentContext context) {
        var world = context.observation().publicWorld();
        var self = context.observation().self();
        return world.ringStage() >= 4 && (world.remainingPlayers() <= 2 || self.food() <= 1 || self.water() <= 1);
    }

    private BattleDecisionCandidate moveToward(BattleDecisionCandidateSet set, BattleAgentContext context,
                                               java.util.List<String> destinations) {
        var world = context.observation().publicWorld();
        String origin = context.observation().self().region();
        record Step(String region, String first) { }
        var queue = new ArrayDeque<Step>();
        var visited = new HashSet<String>();
        visited.add(origin);
        world.topology().getOrDefault(origin, java.util.List.of()).stream().sorted()
                .forEach(r -> { visited.add(r); queue.add(new Step(r, r)); });
        while (!queue.isEmpty()) {
            Step step = queue.remove();
            if (destinations.contains(step.region())) return set.candidates().stream()
                    .filter(c -> c.actionType() == BattleActionType.MOVE && Objects.equals(c.targetRegion(), step.first()))
                    .findFirst().orElse(null);
            world.topology().getOrDefault(step.region(), java.util.List.of()).stream().sorted()
                    .filter(visited::add).forEach(r -> queue.add(new Step(r, step.first())));
        }
        return null;
    }

    private BattleDecisionCandidate first(BattleDecisionCandidateSet set, BattleActionType type) {
        return set.candidates().stream().filter(candidate -> candidate.actionType() == type)
                .min(Comparator.comparing(BattleDecisionCandidate::candidateId)).orElse(null);
    }
}
