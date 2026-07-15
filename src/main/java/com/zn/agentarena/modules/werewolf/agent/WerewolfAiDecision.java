package com.zn.agentarena.modules.werewolf.agent;

/**
 * 狼人杀AI结构化决策。
 *
 * @param action 行动类型名称
 * @param targetPlayerId 目标玩家ID
 * @param publicMessage 公开发言
 * @param reasonSummary 决策摘要
 * @param confidence 决策置信度
 * @author zhangxin
 * @date 2026/07/12
 */
public record WerewolfAiDecision(
        String action,
        Long targetPlayerId,
        String publicMessage,
        String reasonSummary,
        Integer confidence
) {
}
