package com.zn.agentarena.modules.battle.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** 大逃杀唯一规则常量与可复现的开局策略，供引擎、观察和 Prompt 共用。 */
public final class BattleRoyaleRules {
    public static final int MAX_HEALTH = 100;
    public static final int METABOLISM_COST = 1;
    public static final int STARVATION_DAMAGE = 5;
    public static final int HEAL_AMOUNT = 35;
    public static final int REST_HEAL = 6;
    public static final int GUARD_ARMOR_GAIN = 8;
    public static final int GUARD_ARMOR_CAP = 36;
    public static final int LOOT_ARMOR_CAP = 60;
    public static final int REVIVE_HEALTH = 30;
    public static final int ALLY_COVER_REDUCTION = 6;
    public static final int CRITICAL_CHANCE = 12;

    private BattleRoyaleRules() { }

    /** 平衡分配出生区域后按种子洗牌，避免座位号永久绑定资源区和最终安全区。 */
    public static List<String> spawnAssignments(List<String> regions, int playerCount, long seed) {
        if (regions == null || regions.isEmpty() || playerCount <= 0) return List.of();
        List<String> assignments = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) assignments.add(regions.get(i % regions.size()));
        Collections.shuffle(assignments, new Random(seed ^ 0x51A7E5EEDL));
        return assignments;
    }

    /** 每局独立但可复现的缩圈保留顺序；首项是最终安全区，末项最先出圈。 */
    public static List<String> ringOrder(List<String> regions, long seed) {
        if (regions == null || regions.isEmpty()) return List.of();
        List<String> order = new ArrayList<>(regions);
        Collections.shuffle(order, new Random(seed ^ 0x71A9C1E5L));
        return order;
    }

    public static int ringStageForRound(int roundNo) {
        return Math.min(5, 1 + Math.max(0, roundNo - 1) / 3);
    }

    public static int nextShrinkRound(int ringStage) {
        return ringStage < 5 ? ringStage * 3 + 1 : -1;
    }

    public static int zoneDamage(int ringStage) {
        return switch (ringStage) { case 2 -> 8; case 3 -> 14; case 4 -> 22; case 5 -> 35; default -> 0; };
    }

    /** 达到最大回合或全员倒地时使用的公开限时生存积分。 */
    public static int survivalScore(BattlePlayerState player) {
        return Math.max(0, player.getHealth()) * 2 + player.getArmor() + player.getFood() * 2
                + player.getWater() * 2 + player.getMedkits() * 12 + player.getWeaponLevel() * 18
                + player.getAmmo() + player.getKills() * 120 + player.getTotalDamage() / 2;
    }
}
