package com.zn.agentarena.modules.battle.simulation.prompt;

import java.util.List;

public record BattlePromptExperimentPrompt(String text, List<String> actionOrder,
                                           int characters, int estimatedTokens, int sectionCount) { }
