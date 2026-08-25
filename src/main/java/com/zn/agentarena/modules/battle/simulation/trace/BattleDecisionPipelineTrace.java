package com.zn.agentarena.modules.battle.simulation.trace;

import java.util.List;
import java.util.Map;

/** 完整动作管线诊断记录，仅用于 simulation/debug，不进入公开游戏事件。 */
public record BattleDecisionPipelineTrace(
        String scenarioId, long seed, int runIndex, int round, Long actorPlayerId,
        String model, String provider, Map<String, Object> context,
        String prompt, String rawOutput, boolean parserRepair,
        String parsedAction, Long parsedTargetPlayerId, String parsedTargetRegion,
        String parsedSocialAction, String communication, String reasonSummary, Integer confidence,
        String sanitizedAction, Long sanitizedTargetPlayerId, String sanitizedTargetRegion,
        boolean sanitizedChanged, String sanitizeReason,
        boolean valid, String validationReason,
        String executedAction, Long executedTargetPlayerId, String executedSocialAction,
        List<String> engineEvents, String source, String fallbackCode) {
}
