package com.zn.agentarena.modules.battle.domain;

/** 基础资源读取和变更的唯一规则入口，供承诺与原子交易复用。 */
public final class BattleResourceInventory {
    private BattleResourceInventory() {
    }

    public static int amount(BattlePlayerState player, BattleResourceType type) {
        if (player == null || type == null) return 0;
        return switch (type) {
            case FOOD -> player.getFood();
            case WATER -> player.getWater();
            case MEDKIT -> player.getMedkits();
            case AMMO -> player.getAmmo();
        };
    }

    public static boolean has(BattlePlayerState player, BattleResourceAmount resource) {
        return resource != null && resource.valid() && amount(player, resource.resourceType()) >= resource.amount();
    }

    public static void remove(BattlePlayerState player, BattleResourceAmount resource) {
        change(player, resource.resourceType(), -resource.amount());
    }

    public static void add(BattlePlayerState player, BattleResourceAmount resource) {
        change(player, resource.resourceType(), resource.amount());
    }

    private static void change(BattlePlayerState player, BattleResourceType type, int delta) {
        switch (type) {
            case FOOD -> player.setFood(player.getFood() + delta);
            case WATER -> player.setWater(player.getWater() + delta);
            case MEDKIT -> player.setMedkits(player.getMedkits() + delta);
            case AMMO -> player.setAmmo(player.getAmmo() + delta);
        }
    }
}
