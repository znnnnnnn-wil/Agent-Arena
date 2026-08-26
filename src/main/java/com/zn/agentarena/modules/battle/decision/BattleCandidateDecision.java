package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.communication.BattleCommunicationDecision;
import com.zn.agentarena.modules.battle.social.BattleSocialDecision;

/** 生产模型输出：战术部分只能选择一个临时候选 ID。 */
public record BattleCandidateDecision(
        String tacticalCandidateId,
        String socialCandidateId,
        BattleSocialDecision socialAction,
        BattleCommunicationDecision communication,
        String reasonSummary,
        Integer confidence
) {
    /** 兼容旧快照测试与历史实验；生产 Prompt 使用 socialCandidateId。 */
    public BattleCandidateDecision(String tacticalCandidateId, BattleSocialDecision socialAction,
                                   BattleCommunicationDecision communication, String reasonSummary,
                                   Integer confidence) {
        this(tacticalCandidateId, null, socialAction, communication, reasonSummary, confidence);
    }
}
