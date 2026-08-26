package com.zn.agentarena.modules.battle.simulation.prompt;

import java.util.List;
import java.util.Map;

/** 由可观察状态和生产引擎校验共同确定的实验动作能力。 */
public record BattleActionAffordance(String action, List<Long> targetPlayerIds,
                                     List<String> targetRegions,
                                     String executableCondition,
                                     Map<String, Object> relevantState) { }
