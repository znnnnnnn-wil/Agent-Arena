package com.zn.agentarena.modules.battle.goal;

import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.memory.*;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component @RequiredArgsConstructor
public class BattleHiddenGoalSystem {
    private final BattleMemorySystem memorySystem;

    public void ensureAssigned(BattleRoyaleGameState state) {
        if (state.getPlayers() == null) return;
        BattleHiddenGoalType[] types = BattleHiddenGoalType.values();
        for (BattlePlayerState player : state.getPlayers()) {
            if (state.getHiddenGoals().stream().anyMatch(goal -> Objects.equals(goal.getOwnerPlayerId(), player.getPlayerId()))) continue;
            BattleHiddenGoalType type = types[Math.floorMod(Objects.hash(state.getRandomSeed(), player.getPlayerId()), types.length)];
            int currentResources = Math.max(0, player.getFood()) + Math.max(0, player.getWater());
            int target = type == BattleHiddenGoalType.RESOURCE_HOARDER
                    ? currentResources + 1 : 1;
            state.getHiddenGoals().add(BattleHiddenGoal.builder().goalId("hidden-" + state.getGameId() + "-" + player.getPlayerId())
                    .ownerPlayerId(player.getPlayerId()).type(type).targetValue(target).status(BattleHiddenGoalStatus.ACTIVE)
                    .assignedRound(state.getRoundNo()).build());
            memorySystem.rememberFact(state, player.getPlayerId(), BattleMemoryType.HIDDEN_GOAL_ASSIGNED,
                    player.getPlayerId(), null, "私人目标：" + type, 100, null);
        }
    }

    public List<BattleHiddenGoal> visibleTo(BattleRoyaleGameState state, Long playerId) {
        ensureAssigned(state);
        return state.getHiddenGoals().stream().filter(g -> Objects.equals(g.getOwnerPlayerId(), playerId)).toList();
    }

    public List<EngineEvent> evaluate(BattleRoyaleGameState state, Long actorId, String trigger) {
        ensureAssigned(state);
        List<EngineEvent> events = new ArrayList<>();
        BattlePlayerState player = state.findPlayer(actorId);
        for (BattleHiddenGoal goal : visibleTo(state, actorId)) {
            if (goal.getStatus() != BattleHiddenGoalStatus.ACTIVE) continue;
            boolean progress = switch (goal.getType()) {
                case FIELD_MEDIC -> "REVIVE".equals(trigger);
                case ALLIANCE_KEEPER -> "ALLIANCE_FORMED".equals(trigger);
                case INFORMATION_BROKER -> "INFORMATION_TRADE_COMPLETED".equals(trigger);
                case RESOURCE_HOARDER -> player != null && player.getFood() + player.getWater() >= goal.getTargetValue();
            };
            if (!progress) continue;
            goal.setProgress(goal.getType() == BattleHiddenGoalType.RESOURCE_HOARDER ? goal.getTargetValue() : goal.getProgress() + 1);
            if (goal.getProgress() < goal.getTargetValue()) continue;
            goal.setStatus(BattleHiddenGoalStatus.COMPLETED); goal.setCompletedRound(state.getRoundNo());
            if (player != null) player.setArmor(Math.min(100, player.getArmor() + 10));
            memorySystem.rememberFact(state, actorId, BattleMemoryType.HIDDEN_GOAL_COMPLETED,
                    actorId, null, "已完成私人目标：" + goal.getType(), 100, null);
            state.setSequenceNo(state.getSequenceNo() + 1);
            events.add(new EngineEvent("BATTLE_HIDDEN_GOAL_COMPLETED", actorId,
                    Map.of("goalId", goal.getGoalId(), "goalType", goal.getType().name(), "rewardArmor", 10),
                    Map.of("playerId", actorId, "summary", "一名玩家完成了私人目标")));
        }
        return events;
    }
}
