package com.zn.agentarena.modules.battle.social;

import com.zn.agentarena.modules.battle.alliance.BattleAlliance;
import com.zn.agentarena.modules.battle.promise.BattlePromise;
import com.zn.agentarena.modules.battle.trade.BattleTradeProposal;
import com.zn.agentarena.modules.battle.goal.BattleHiddenGoal;
import com.zn.agentarena.modules.battle.objective.BattleCooperativeObjective;
import com.zn.agentarena.modules.battle.information.BattleInformationTradeProposal;
import com.zn.agentarena.modules.battle.information.BattleRumor;

import java.util.List;

/** 已按当前玩家知识边界过滤的正式社会协议上下文。 */
public record BattleSocialContext(
        List<BattleAlliance> alliances,
        List<BattlePromise> promises,
        List<BattleTradeProposal> tradeProposals,
        List<BattleHiddenGoal> hiddenGoals,
        List<BattleCooperativeObjective> cooperativeObjectives,
        List<BattleInformationTradeProposal> informationTradeProposals,
        List<BattleRumor> rumors
) {
    public BattleSocialContext(List<BattleAlliance> alliances, List<BattlePromise> promises,
                               List<BattleTradeProposal> tradeProposals) {
        this(alliances, promises, tradeProposals, List.of(), List.of(), List.of(), List.of());
    }
}
