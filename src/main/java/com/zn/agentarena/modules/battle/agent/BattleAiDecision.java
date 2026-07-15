package com.zn.agentarena.modules.battle.agent;

/** 大逃杀模型结构化决策。 */
public record BattleAiDecision(
        String action,
        Long targetPlayerId,
        String targetRegion,
        String reasonSummary,
        Integer confidence
) {
}
