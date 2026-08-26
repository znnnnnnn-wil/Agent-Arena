package com.zn.agentarena.modules.battle.objective;

import com.zn.agentarena.modules.battle.domain.BattleResourceType;
import lombok.*;
import java.util.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BattleCooperativeObjective {
    private String objectiveId;
    private String region;
    @Builder.Default private Map<BattleResourceType, Integer> required = new EnumMap<>(BattleResourceType.class);
    @Builder.Default private Map<BattleResourceType, Integer> contributed = new EnumMap<>(BattleResourceType.class);
    @Builder.Default private Map<Long, Integer> contributionByPlayer = new LinkedHashMap<>();
    @Builder.Default private int minimumContributors = 2;
    private int createdRound;
    private int deadlineRound;
    private BattleCooperativeObjectiveStatus status;
    private Integer resolvedRound;
}
