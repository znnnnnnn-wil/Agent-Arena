package com.zn.agentarena.modules.battle.communication;

/** 模型决策中的可选通信。channel 使用字符串以便非法值可被单独丢弃而不影响战术行动。 */
public record BattleCommunicationDecision(
        String channel,
        Long targetPlayerId,
        String content
) {
}
