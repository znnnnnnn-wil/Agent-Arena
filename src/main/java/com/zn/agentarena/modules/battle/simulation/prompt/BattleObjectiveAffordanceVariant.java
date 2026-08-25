package com.zn.agentarena.modules.battle.simulation.prompt;

public enum BattleObjectiveAffordanceVariant {
    I0_CURRENT_NONE(false, false),
    I1_D3_NONE(true, false),
    I2_CURRENT_A3(false, true),
    I3_D3_A3(true, true);

    private final boolean d3Objective;
    private final boolean a3Affordance;
    BattleObjectiveAffordanceVariant(boolean d3Objective, boolean a3Affordance) {
        this.d3Objective = d3Objective; this.a3Affordance = a3Affordance;
    }
    public boolean d3Objective() { return d3Objective; }
    public boolean a3Affordance() { return a3Affordance; }
}
