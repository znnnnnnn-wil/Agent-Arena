package com.zn.agentarena.modules.battle.promise;

/** 显式承诺协议生命周期。 */
public enum BattlePromiseStatus {
    PROPOSED,
    ACCEPTED,
    FULFILLED,
    BROKEN,
    EXPIRED,
    CANCELLED
}
