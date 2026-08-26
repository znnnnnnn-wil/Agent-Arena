package com.zn.agentarena.modules.battle.social;

import com.zn.agentarena.modules.battle.promise.BattlePromiseDecision;
import com.zn.agentarena.modules.battle.trade.BattleTradeDecision;
import com.zn.agentarena.modules.battle.objective.BattleObjectiveContributionDecision;
import com.zn.agentarena.modules.battle.information.BattleInformationTradeDecision;
import com.zn.agentarena.modules.battle.information.BattleRumorDecision;

/** 模型输出中的单个可选社会动作；具体载荷保持强类型。 */
public record BattleSocialDecision(
        String type,
        Long targetPlayerId,
        String referenceId,
        BattlePromiseDecision promise,
        BattleTradeDecision trade,
        BattleObjectiveContributionDecision objectiveContribution,
        BattleInformationTradeDecision informationTrade,
        BattleRumorDecision rumor
) {
    public BattleSocialDecision(String type, Long targetPlayerId, String referenceId,
                                BattlePromiseDecision promise, BattleTradeDecision trade) {
        this(type, targetPlayerId, referenceId, promise, trade, null, null, null);
    }
}
