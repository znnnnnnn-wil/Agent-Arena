package com.zn.agentarena.modules.battle.perception;

import java.util.List;

/** 某一个智能体在当前行动时刻的有限认知。 */
public record BattleAgentObservation(
        BattleSelfObservation self,
        BattlePublicWorldObservation publicWorld,
        List<ObservedBattlePlayer> visiblePlayers
) {
}
