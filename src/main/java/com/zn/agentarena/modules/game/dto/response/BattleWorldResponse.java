package com.zn.agentarena.modules.game.dto.response;

import com.zn.agentarena.modules.battle.domain.*;
import java.util.List;
import java.util.Map;

/** 仅包含公开规则与环境；不包含隐藏位置、目标或私信。 */
public record BattleWorldResponse(int ringStage, List<String> safeRegions, List<String> ringOrder,
        Integer nextShrinkRound, int zoneDamage, String supplyDropRegion,
        Map<String, BattleRegionRules.Profile> regionProfiles, Map<String, List<String>> topology,
        String victoryRule) {
    public static BattleWorldResponse from(BattleRoyaleGameState state) {
        List<String> order = state.getRingOrder() == null || state.getRingOrder().isEmpty()
                ? BattleRoyaleRules.ringOrder(state.getRegions(), state.getRandomSeed()) : List.copyOf(state.getRingOrder());
        int next = BattleRoyaleRules.nextShrinkRound(state.getRingStage(), state.getMaxRounds());
        return new BattleWorldResponse(state.getRingStage(), state.getSafeRegions() == null ? List.of() : List.copyOf(state.getSafeRegions()),
                order, next < 0 ? null : next, BattleRoyaleRules.zoneDamage(state.getRingStage()), state.getSupplyDropRegion(),
                BattleRegionRules.PROFILES, BattleRegionRules.TOPOLOGY,
                "最后未淘汰者获胜；达到回合上限或全员倒地时按公开生存积分结算，同分比较击杀、生命、玩家ID");
    }
}
