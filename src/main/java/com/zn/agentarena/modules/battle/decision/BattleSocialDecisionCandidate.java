package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.communication.BattleCommunicationDecision;
import com.zn.agentarena.modules.battle.social.BattleSocialDecision;

/** 一个具体、不可由模型改写载荷的社会选择；可携带传播传闻所需的配套消息。 */
public record BattleSocialDecisionCandidate(
        String candidateId,
        String semanticName,
        String effectSummary,
        String costSummary,
        BattleSocialDecision decision,
        BattleCommunicationDecision coupledCommunication
) { }
