package com.zn.agentarena.modules.turing.domain;

/**
 * 已完成或正在进行的一组质询与回应。
 *
 * @param questionerPlayerId questionerPlayerId 字段
 * @param targetPlayerId targetPlayerId 字段
 * @param question question 字段
 * @param response response 字段
 */
public record TuringHuntQuestionExchange(
        Long questionerPlayerId,
        Long targetPlayerId,
        String question,
        String response
) {
}
