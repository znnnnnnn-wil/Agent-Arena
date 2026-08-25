package com.zn.agentarena.modules.battle.simulation.prompt;

import java.util.List;

public record BattleObjectiveAffordancePrompt(String text, String objectiveVariant,
                                              String affordanceVariant,
                                              String insertedAffordanceSection,
                                              List<String> changedSections) { }
