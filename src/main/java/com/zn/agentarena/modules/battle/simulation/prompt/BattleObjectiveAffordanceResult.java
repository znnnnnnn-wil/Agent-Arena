package com.zn.agentarena.modules.battle.simulation.prompt;

import com.zn.agentarena.modules.battle.simulation.trace.BattleDecisionPipelineTrace;
import java.util.List;
import java.util.Map;

public record BattleObjectiveAffordanceResult(BattleObjectiveAffordanceVariant variant,
                                              String objectiveVariant, String affordanceVariant,
                                              BattlePromptScenario scenario,
                                              String expectedRelevantAction,
                                              BattleObjectiveAffordancePrompt prompt,
                                              List<BattleActionAffordance> computedAffordances,
                                              Map<String,Object> observableState,
                                              boolean contextRecognized,
                                              boolean affordanceRecognized,
                                              BattleDecisionPipelineTrace trace) { }
