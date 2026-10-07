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
    public static final int GUARD_DAMAGE_REDUCTION = 8;
    public static final int MAX_CONSECUTIVE_HIDES = 2;
    public static final int DAMAGE_SCORE_MULTIPLIER = 2;
    public static final int LOOT_ARMOR_CAP = 60;
    public static final int REVIVE_HEALTH = 30;
    public static final int ALLY_COVER_REDUCTION = 6;
    public static final int CRITICAL_CHANCE = 12;

    private BattleRoyaleRules() { }

    /** 获得护甲只增加数值；其他系统授予的更高护甲不会被较低上限扣掉。 */
    public static int addArmor(int current, int amount, int cap) {
        return Math.max(current, Math.min(cap, current + Math.max(0, amount)));
    }

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

    /** 短局压缩准备阶段，确保停止轮次前留出决赛窗口；长局保持三轮节奏。 */
    public static int nextShrinkRound(int ringStage, int maxRounds) {
        if (ringStage >= 5) return -1;
        int finalRound = Math.min(13, Math.max(5, (maxRounds * 3) / 4));
        return 1 + (int) Math.ceil(ringStage * (finalRound - 1) / 4.0);
    }

    public static int ringStageForRound(int roundNo, int maxRounds) {
        int stage = 1;
        while (stage < 5 && roundNo >= nextShrinkRound(stage, maxRounds)) stage++;
        return stage;
    }

    public static int zoneDamage(int ringStage) {
        return switch (ringStage) { case 2 -> 8; case 3 -> 14; case 4 -> 22; case 5 -> 35; default -> 0; };
    }

    /** 达到最大回合或全员倒地时使用的公开限时生存积分。 */
    public static int survivalScore(BattlePlayerState player) {
        return scoreBreakdown(player).values().stream().mapToInt(Integer::intValue).sum();
    }

    public static java.util.Map<String, Integer> scoreBreakdown(BattlePlayerState player) {
        java.util.Map<String, Integer> points = new java.util.LinkedHashMap<>();
        points.put("health", Math.max(0, player.getHealth()) * 2);
        points.put("armor", player.getArmor());
        points.put("supplies", player.getFood() * 2 + player.getWater() * 2 + player.getMedkits() * 12);
        points.put("equipment", player.getWeaponLevel() * 18 + player.getAmmo());
        points.put("kills", player.getKills() * 120);
        points.put("damage", player.getTotalDamage() * DAMAGE_SCORE_MULTIPLIER);
        return points;
    }
}
