package com.zn.agentarena.modules.battle.simulation.prompt;

public enum BattleModelComparisonGroup {
    M0_CONTROL("qwen-turbo"),
    M1_STRONG("qwen-max");
    private final String modelName;
    BattleModelComparisonGroup(String modelName){this.modelName=modelName;}
    public String modelName(){return modelName;}
}
