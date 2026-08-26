package com.zn.agentarena.modules.battle.simulation.prompt;

import com.zn.agentarena.modules.battle.simulation.trace.BattleDecisionPipelineTrace;

public record BattlePromptFactorialResult(BattlePromptFactorialVariant variant,
                                          BattlePromptScenario scenario,
                                          String expectedRelevantAction,
                                          BattlePromptFactorialPrompt prompt,
                                          BattleDecisionPipelineTrace trace) { }
