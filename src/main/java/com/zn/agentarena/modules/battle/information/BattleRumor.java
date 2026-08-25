package com.zn.agentarena.modules.battle.information;

import lombok.*;
import java.util.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BattleRumor {
    private String rumorId;
    private Long originatorPlayerId;
    private Long speakerPlayerId;
    private String sourceRumorId;
    private Long subjectPlayerId;
    private BattleRumorClaimType claimType;
    private String claim;
    private int confidence;
    private int generation;
    private int createdRound;
    @Builder.Default private List<Long> knownByPlayerIds = new ArrayList<>();
}
