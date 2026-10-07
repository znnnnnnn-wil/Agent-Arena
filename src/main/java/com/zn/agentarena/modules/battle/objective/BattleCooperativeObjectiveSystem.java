package com.zn.agentarena.modules.battle.objective;

import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.memory.*;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component @RequiredArgsConstructor
public class BattleCooperativeObjectiveSystem {
    private final BattleMemorySystem memorySystem;

    public void ensureObjective(BattleRoyaleGameState state) {
        if (!state.getCooperativeObjectives().isEmpty()) return;
        EnumMap<BattleResourceType,Integer> required = new EnumMap<>(BattleResourceType.class);
        int perResource = Math.max(2, (state.getPlayers().size() + 3) / 4);
        required.put(BattleResourceType.FOOD, perResource);
        required.put(BattleResourceType.WATER, perResource);
        state.getCooperativeObjectives().add(BattleCooperativeObjective.builder()
                .objectiveId("beacon-" + state.getGameId()).region("C").required(required)
                .minimumContributors(Math.min(2, Math.max(1, state.getPlayers().size())))
                .createdRound(state.getRoundNo()).deadlineRound(Math.max(8, state.getRoundNo() + 9))
                .status(BattleCooperativeObjectiveStatus.ACTIVE).build());
    }

    public List<EngineEvent> contribute(BattleRoyaleGameState state, Long actorId, BattleObjectiveContributionDecision decision) {
        ensureObjective(state);
        if (decision == null || decision.resourceType() == null || decision.amount() == null || decision.amount() <= 0) return List.of();
        BattleCooperativeObjective objective = state.getCooperativeObjectives().stream()
                .filter(o -> o.getStatus() == BattleCooperativeObjectiveStatus.ACTIVE)
                .filter(o -> decision.objectiveId() == null || decision.objectiveId().equals(o.getObjectiveId())).findFirst().orElse(null);
        BattlePlayerState actor = state.findPlayer(actorId);
        if (objective == null || actor == null || !actor.isActive() || !Objects.equals(actor.getRegion(), objective.getRegion())) return List.of();
        BattleResourceType type;
        try { type = BattleResourceType.valueOf(decision.resourceType().trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return List.of(); }
        int remaining = objective.getRequired().getOrDefault(type, 0) - objective.getContributed().getOrDefault(type, 0);
        int totalRequired = objective.getRequired().values().stream().mapToInt(Integer::intValue).sum();
        int actorContributed = objective.getContributionByPlayer().getOrDefault(actorId, 0);
        int missingContributors = Math.max(0,
                objective.getMinimumContributors() - objective.getContributionByPlayer().size());
        int reservedForOthers = objective.getContributionByPlayer().containsKey(actorId)
                ? missingContributors : Math.max(0, missingContributors - 1);
        int actorAllowance = totalRequired - reservedForOthers - actorContributed;
        int amount = Math.min(Math.min(decision.amount(), remaining), actorAllowance);
        BattleResourceAmount resource = new BattleResourceAmount(type, amount);
        if (amount <= 0 || !BattleResourceInventory.has(actor, resource)) return List.of();
        BattleResourceInventory.remove(actor, resource);
        objective.getContributed().merge(type, amount, Integer::sum);
        objective.getContributionByPlayer().merge(actorId, amount, Integer::sum);
        int immediateArmor = Math.min(6, amount * 2);
        actor.setArmor(BattleRoyaleRules.addArmor(actor.getArmor(), immediateArmor, BattleRoyaleRules.LOOT_ARMOR_CAP));
        memorySystem.rememberFact(state, actorId, BattleMemoryType.OBJECTIVE_CONTRIBUTED, actorId, null,
                "向公共信标贡献 " + type + " x" + amount, 100, objective.getRegion());
        List<EngineEvent> events = new ArrayList<>();
        events.add(event(state, "BATTLE_OBJECTIVE_CONTRIBUTED", actorId, objective,
                "公共目标获得资源贡献；贡献者立即获得" + immediateArmor + "护甲"));
        boolean resourcesComplete = objective.getRequired().entrySet().stream()
                .allMatch(e -> objective.getContributed().getOrDefault(e.getKey(), 0) >= e.getValue());
        boolean complete = resourcesComplete
                && objective.getContributionByPlayer().size() >= objective.getMinimumContributors();
        if (complete) {
            objective.setStatus(BattleCooperativeObjectiveStatus.COMPLETED); objective.setResolvedRound(state.getRoundNo());
            for (Long id : objective.getContributionByPlayer().keySet()) {
                BattlePlayerState contributor = state.findPlayer(id);
                if (contributor != null && !contributor.isEliminated()) {
                    contributor.setArmor(BattleRoyaleRules.addArmor(contributor.getArmor(), 10, BattleRoyaleRules.LOOT_ARMOR_CAP));
                }
                memorySystem.rememberFact(state, id, BattleMemoryType.OBJECTIVE_COMPLETED, id, null,
                        "协作信标完成，获得护甲奖励", 100, objective.getRegion());
            }
            events.add(event(state, "BATTLE_OBJECTIVE_COMPLETED", actorId, objective, "公共协作目标已完成"));
        }
        return events;
    }

    public List<EngineEvent> evaluate(BattleRoyaleGameState state) {
        ensureObjective(state); List<EngineEvent> result = new ArrayList<>();
        for (BattleCooperativeObjective o : state.getCooperativeObjectives()) if (o.getStatus() == BattleCooperativeObjectiveStatus.ACTIVE && state.getRoundNo() >= o.getDeadlineRound()) {
            o.setStatus(BattleCooperativeObjectiveStatus.EXPIRED); o.setResolvedRound(state.getRoundNo());
            result.add(event(state, "BATTLE_OBJECTIVE_EXPIRED", null, o, "公共协作目标已经失败"));
        }
        return result;
    }

    private EngineEvent event(BattleRoyaleGameState state, String type, Long actor, BattleCooperativeObjective o, String summary) {
        state.setSequenceNo(state.getSequenceNo()+1);
        Map<String,Object> p = new LinkedHashMap<>(); p.put("objectiveId",o.getObjectiveId()); p.put("region",o.getRegion()); p.put("required",o.getRequired()); p.put("contributed",o.getContributed()); p.put("minimumContributors", o.getMinimumContributors()); p.put("contributorCount", o.getContributionByPlayer().size()); p.put("status",o.getStatus().name());
        Map<String,Object> pub = new LinkedHashMap<>(p); pub.put("summary",summary);
        return new EngineEvent(type,actor,p,pub);
    }
}
