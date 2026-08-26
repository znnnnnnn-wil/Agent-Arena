package com.zn.agentarena.modules.battle.simulation.prompt;

import com.zn.agentarena.modules.battle.simulation.trace.BattleDecisionPipelineTrace;

public record BattleDecisionObjectiveResult(BattleDecisionObjectiveVariant variant,
                                            BattlePromptScenario scenario,
                                            String expectedRelevantAction,
                                            BattleDecisionObjectivePrompt prompt,
                                            BattleDecisionPipelineTrace trace) { }
