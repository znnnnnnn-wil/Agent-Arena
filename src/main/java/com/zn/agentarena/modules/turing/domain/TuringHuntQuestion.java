package com.zn.agentarena.modules.turing.domain;

/**
 * 一次公开质询。
 *
 * @param targetPlayerId targetPlayerId 字段
 * @param question question 字段
 */
public record TuringHuntQuestion(Long targetPlayerId, String question) {
}
