package com.zn.agentarena.modules.battle.domain;

/** 一项强类型资源及数量。 */
public record BattleResourceAmount(BattleResourceType resourceType, int amount) {
    public boolean valid() {
        return resourceType != null && amount > 0 && amount <= 99;
    }
}
