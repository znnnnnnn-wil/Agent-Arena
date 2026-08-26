package com.zn.agentarena.modules.battle.agent;

import com.zn.agentarena.modules.battle.communication.BattleCommunicationDecision;
import com.zn.agentarena.modules.battle.social.BattleSocialDecision;

/** 大逃杀模型结构化决策。 */
public record BattleAiDecision(
        String action,
        Long targetPlayerId,
        String targetRegion,
        BattleSocialDecision socialAction,
        BattleCommunicationDecision communication,
        String reasonSummary,
        Integer confidence
) {
    public BattleAiDecision(String action, Long targetPlayerId, String targetRegion,
                            String reasonSummary, Integer confidence) {
        this(action, targetPlayerId, targetRegion, null, null, reasonSummary, confidence);
    }

    public BattleAiDecision(String action, Long targetPlayerId, String targetRegion,
                            BattleCommunicationDecision communication,
                            String reasonSummary, Integer confidence) {
        this(action, targetPlayerId, targetRegion, null, communication, reasonSummary, confidence);
    }
}
