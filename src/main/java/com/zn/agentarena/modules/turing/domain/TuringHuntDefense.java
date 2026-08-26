package com.zn.agentarena.modules.turing.domain;

/**
 * 一次公开辩护。
 *
 * @param defense defense 字段
 * @param counterSuspectPlayerId counterSuspectPlayerId 字段
 * @param reasonSummary reasonSummary 字段
 */
public record TuringHuntDefense(String defense, Long counterSuspectPlayerId, String reasonSummary) {
}
