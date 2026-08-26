package com.zn.agentarena.modules.turing.domain;

/** 图灵潜伏局可提交给规则引擎的行动。 */
public enum TuringHuntActionType {
    ANSWER,
    SUSPECT,
    QUESTION,
    RESPOND,
    DEFEND,
    VOTE,
    /** 不代表玩家行动，只推进一次确定性阶段。 */
    ADVANCE
}
