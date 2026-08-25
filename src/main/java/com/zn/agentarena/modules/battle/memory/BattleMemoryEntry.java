package com.zn.agentarena.modules.battle.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 一条可快照、可回放的局内私有记忆。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleMemoryEntry {
    private String id;
    private int round;
    private Long sourcePlayerId;
    private Long targetPlayerId;
    private BattleMemoryType type;
    private String summary;
    private int importance;
    private long sequenceNo;
    private String relatedRegion;
    private int certainty;
}
