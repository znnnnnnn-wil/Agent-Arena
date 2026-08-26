package com.zn.agentarena.modules.battle.promise;

/** 模型在提出承诺时使用的强类型、可验证参数。 */
public record BattlePromiseDecision(
        String promiseType,
        String resourceType,
        Integer amount,
        Integer deadlineRound
) {
}
