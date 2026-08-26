package com.zn.agentarena.modules.battle.simulation.prompt;

import com.zn.agentarena.modules.battle.simulation.trace.BattleDecisionPipelineTrace;
import java.util.List;
import java.util.Map;

public record BattleActionAffordanceResult(BattleActionAffordanceVariant variant,
                                           BattlePromptScenario scenario,
                                           String expectedRelevantAction,
                                           BattleActionAffordancePrompt prompt,
                                           List<BattleActionAffordance> computedAffordances,
                                           Map<String, Object> observableState,
                                           boolean contextRecognized,
                                           boolean affordanceRecognized,
                                           BattleDecisionPipelineTrace trace) { }
