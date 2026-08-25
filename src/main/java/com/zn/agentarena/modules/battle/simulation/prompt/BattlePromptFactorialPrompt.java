package com.zn.agentarena.modules.battle.simulation.prompt;

import java.util.List;

public record BattlePromptFactorialPrompt(String text, boolean longTermGoalPresent,
                                          boolean regionAffordancePresent,
                                          List<String> changedSections) { }
