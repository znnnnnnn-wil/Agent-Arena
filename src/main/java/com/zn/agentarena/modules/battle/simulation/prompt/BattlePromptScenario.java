package com.zn.agentarena.modules.battle.simulation.prompt;

public enum BattlePromptScenario {
    ATTACK("ATTACK"), SEARCH("SEARCH"), HEAL("HEAL"), REVIVE("REVIVE");
    private final String expectedRelevantAction;
    BattlePromptScenario(String expectedRelevantAction) { this.expectedRelevantAction = expectedRelevantAction; }
    public String expectedRelevantAction() { return expectedRelevantAction; }
}
