package com.zn.agentarena.modules.battle.simulation.prompt;

import java.util.List;

public record BattleActionAffordancePrompt(String text, String insertedSection,
                                           List<String> changedSections) { }
