package com.zn.agentarena.modules.battle.social;

import com.zn.agentarena.modules.battle.memory.BattleAgentMemory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/** 单个智能体的私有社会认知状态。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleAgentSocialState {
    @Builder.Default
    private BattleAgentMemory memory = new BattleAgentMemory();
    @Builder.Default
    private Map<Long, BattleRelationshipState> relationships = new LinkedHashMap<>();
}
