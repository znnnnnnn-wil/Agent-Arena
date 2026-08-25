package com.zn.agentarena.modules.turing.domain;

/**
 * 一次公开回应。
 *
 * @param targetPlayerId targetPlayerId 字段
 * @param response response 字段
 * @param reasonSummary reasonSummary 字段
 */
public record TuringHuntResponse(Long targetPlayerId, String response, String reasonSummary) {
}
