package com.zn.agentarena.modules.turing.domain;

/**
 * 带行动者和用途的辩护历史。
 *
 * @param playerId playerId 字段
 * @param purpose purpose 字段
 * @param defense defense 字段
 */
public record TuringHuntDefenseRecord(
        Long playerId,
        TuringHuntDefensePurpose purpose,
        TuringHuntDefense defense
) {
}
