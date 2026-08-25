package com.zn.agentarena.modules.turing.domain;

/** 图灵潜伏局的确定性结束原因。 */
public enum TuringHuntFinishedReason {
    HUMAN_ELIMINATED,
    HUMAN_SURVIVED_THREE_ROUNDS,
    TWO_AIS_WRONGLY_ELIMINATED,
    FINAL_TWO_SURVIVORS,
    FINAL_ROUND_TIE
}
