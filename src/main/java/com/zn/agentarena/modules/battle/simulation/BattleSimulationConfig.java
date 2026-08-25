package com.zn.agentarena.modules.battle.simulation;

public record BattleSimulationConfig(BattleSimulationMode mode, int games, int players,
                                     int maxRounds, long firstSeed, boolean invariantChecks) {
    public BattleSimulationConfig {
        if (games <= 0 || players < 6 || players > 10 || maxRounds < 12 || maxRounds > 30) {
            throw new IllegalArgumentException("invalid simulation config");
        }
    }
    public static BattleSimulationConfig baseline() {
        return new BattleSimulationConfig(BattleSimulationMode.FAST_SIMULATION, 1000, 8, 20, 100_000L, true);
    }
}
