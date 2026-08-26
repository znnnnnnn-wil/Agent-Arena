package com.zn.agentarena.modules.battle.simulation.prompt;

import com.zn.agentarena.modules.battle.simulation.trace.BattleDecisionPipelineTrace;

public record BattlePromptExperimentResult(BattlePromptVariant variant, BattlePromptScenario scenario,
                                           String expectedRelevantAction, int selectedPosition,
                                           BattlePromptExperimentPrompt prompt,
                                           BattleDecisionPipelineTrace trace) { }
