package com.zn.agentarena.modules.battle.trade;

/** 模型交易参数中的资源项；解析后转换为领域枚举。 */
public record BattleTradeItemDecision(String resourceType, Integer amount) {
}
