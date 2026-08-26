package com.zn.agentarena.modules.turing.domain;

/**
 * 一次秘密投票。
 *
 * @param targetPlayerId targetPlayerId 字段
 * @param reasonSummary reasonSummary 字段
 */
public record TuringHuntVote(Long targetPlayerId, String reasonSummary) {
}
