package com.zn.agentarena.modules.turing.domain;

/** 图灵潜伏局的公开阶段。 */
public enum TuringHuntPhase {
    WAITING,
    ROUND_PROMPT,
    ANSWER_COLLECTION,
    ANSWER_REVEAL,
    SUSPICION_COLLECTION,
    CONTROVERSY_REVEAL,
    QUESTION,
    RESPONSE,
    DEFENSE,
    VOTE_COLLECTION,
    VOTE_REVEAL,
    ROUND_RESULT,
    FINISHED
}
