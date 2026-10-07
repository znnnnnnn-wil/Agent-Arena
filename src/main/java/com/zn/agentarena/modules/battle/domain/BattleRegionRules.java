package com.zn.agentarena.modules.battle.domain;

import java.util.List;
import java.util.Map;

/** 地图、AI 与引擎共享的公开区域规则。 */
public final class BattleRegionRules {
    private BattleRegionRules() { }
    public record Profile(String name, String resources, int coverReduction, int restBonus, boolean searchExposes) { }
    public static final Map<String, Profile> PROFILES = Map.of(
            "A", new Profile("中央村落", "武器 / 弹药", 0, 0, true),
            "B", new Profile("北部林地", "食物 / 医疗", 4, 0, false),
            "C", new Profile("河畔仓库", "饮水 / 弹药", 0, 0, true),
            "D", new Profile("南部废墟", "护甲 / 弹药", 6, 0, true),
            "E", new Profile("野外营地", "医疗 / 补给", 0, 3, true));
    public static final Map<String, List<String>> TOPOLOGY = Map.of(
            "A", List.of("B", "C"), "B", List.of("A", "D"),
            "C", List.of("A", "D", "E"), "D", List.of("B", "C", "E"), "E", List.of("C", "D"));
    public static Profile profile(String region) {
        return PROFILES.getOrDefault(region == null ? "" : region, new Profile("未知区域", "未知", 0, 0, true));
    }
}
