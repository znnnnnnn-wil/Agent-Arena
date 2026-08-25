package com.zn.agentarena.modules.battle.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 单个智能体的私有局内记忆集合。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleAgentMemory {
    @Builder.Default
    private List<BattleMemoryEntry> entries = new ArrayList<>();
}
