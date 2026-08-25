package com.zn.agentarena.modules.battle.trade;

import java.util.List;

/** 模型提出交易时的强类型结构：offer 由提出者给出，request 由接受者给出。 */
public record BattleTradeDecision(
        List<BattleTradeItemDecision> offer,
        List<BattleTradeItemDecision> request
) {
}
