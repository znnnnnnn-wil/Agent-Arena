package com.zn.agentarena.modules.turing.domain;

/**
 * 一组由规则选出的质询关系。
 *
 * @param questionerPlayerId questionerPlayerId 字段
 * @param targetPlayerId targetPlayerId 字段
 */
public record TuringHuntQuestionPair(Long questionerPlayerId, Long targetPlayerId) {
}
