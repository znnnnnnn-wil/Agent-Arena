package com.zn.agentarena.modules.turing.domain;

/**
 * 一次秘密回答。
 *
 * @param answer answer 字段
 * @param reasonSummary reasonSummary 字段
 */
public record TuringHuntAnswer(String answer, String reasonSummary) {
}
