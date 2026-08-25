package com.zn.agentarena.modules.battle.goal;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BattleHiddenGoal {
    private String goalId;
    private Long ownerPlayerId;
    private BattleHiddenGoalType type;
    private int targetValue;
    private int progress;
    private BattleHiddenGoalStatus status;
    private int assignedRound;
    private Integer completedRound;
}
