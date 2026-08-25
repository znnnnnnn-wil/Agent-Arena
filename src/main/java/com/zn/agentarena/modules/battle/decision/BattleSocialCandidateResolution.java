package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.communication.BattleCommunicationDecision;
import com.zn.agentarena.modules.battle.social.BattleSocialDecision;

/** 社会候选解析结果；无效选择只取消社会动作，不影响战术动作和普通通信。 */
public record BattleSocialCandidateResolution(
        boolean selected,
        BattleSocialDecision decision,
        BattleCommunicationDecision coupledCommunication,
        String code
) { }
