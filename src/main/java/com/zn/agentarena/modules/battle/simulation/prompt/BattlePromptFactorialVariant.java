package com.zn.agentarena.modules.battle.simulation.prompt;

public enum BattlePromptFactorialVariant {
    V00(true, true),
    V01(true, false),
    V10(false, true),
    V11(false, false);

    private final boolean longTermGoalPresent;
    private final boolean regionAffordancePresent;

    BattlePromptFactorialVariant(boolean longTermGoalPresent, boolean regionAffordancePresent) {
        this.longTermGoalPresent = longTermGoalPresent;
        this.regionAffordancePresent = regionAffordancePresent;
    }
    public boolean longTermGoalPresent() { return longTermGoalPresent; }
    public boolean regionAffordancePresent() { return regionAffordancePresent; }
}
