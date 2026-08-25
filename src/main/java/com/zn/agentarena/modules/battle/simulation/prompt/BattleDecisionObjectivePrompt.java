package com.zn.agentarena.modules.battle.simulation.prompt;

import java.util.List;

public record BattleDecisionObjectivePrompt(String text, String objective,
                                             List<String> changedSections) { }
