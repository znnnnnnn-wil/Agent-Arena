package com.zn.agentarena.modules.battle.social;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 一个观察者对另一个玩家的主观关系，所有维度均限制在 0 到 100。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleRelationshipState {
    @Builder.Default
    private int trust = 50;
    @Builder.Default
    private int fear = 0;
    @Builder.Default
    private int gratitude = 0;
    @Builder.Default
    private int resentment = 0;
    @Builder.Default
    private int suspicion = 10;
    @Builder.Default
    private int perceivedStrength = 50;
}
