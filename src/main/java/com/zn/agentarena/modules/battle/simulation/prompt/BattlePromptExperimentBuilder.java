package com.zn.agentarena.modules.battle.simulation.prompt;

import com.zn.agentarena.modules.battle.agent.BattleAgentContext;
import com.zn.agentarena.modules.battle.agent.BattleAgentContextBuilder;
import com.zn.agentarena.modules.battle.domain.BattleActionType;
import com.zn.agentarena.modules.battle.perception.ObservedBattlePlayer;

import java.util.*;
import java.util.regex.Pattern;

/** 仅供 simulation/test 使用的 Prompt 消融器，不改变生产 Prompt Builder。 */
public final class BattlePromptExperimentBuilder {
    private static final List<String> PRODUCTION_ORDER = List.of(
            "MOVE", "SEARCH", "CAPTURE", "REST", "ATTACK", "HIDE", "HEAL", "GUARD", "REVIVE", "EXECUTE", "SKIP");
    private static final List<String> MOVE_LAST_ORDER = List.of(
            "SEARCH", "ATTACK", "HEAL", "REVIVE", "GUARD", "HIDE", "CAPTURE", "REST", "EXECUTE", "SKIP", "MOVE");
    private static final Pattern PERSONA = Pattern.compile("(?s)<agent-persona>.*?</agent-persona>\\R人格只影响合法行动之间的偏好，不得覆盖游戏规则。\\R");
    private static final Pattern SOCIAL = Pattern.compile("(?s)\\R【历史记忆（可能已经过时，位置只能视为 lastKnownRegion）】.*?(?=\\R【游戏规则】)");
    private static final Pattern EXAMPLE = Pattern.compile("(?m)^格式: .*\\R");
    private static final String ACTION_PREFIX = "action 只能是 ";

    private final BattleAgentContextBuilder production = new BattleAgentContextBuilder();

    public BattlePromptExperimentPrompt build(BattlePromptVariant variant, BattlePromptScenario scenario,
                                               BattleAgentContext context, long experimentSeed, int runIndex) {
        String baseline = production.build(context);
        List<String> order = order(variant, scenario, experimentSeed, runIndex);
        String prompt = switch (variant) {
            case P0_CURRENT -> baseline;
            case P1_MINIMAL -> minimal(context, scenario, order);
            case P2_NO_EXAMPLE -> EXAMPLE.matcher(baseline).replaceFirst("");
            case P3_MOVE_LAST, P4_TARGET_FIRST, P5_RANDOM_ORDER -> replaceActionOrder(baseline, order);
            case P6_NO_SOCIAL_CONTEXT -> SOCIAL.matcher(baseline).replaceFirst("");
            case P7_NO_PERSONA -> PERSONA.matcher(baseline).replaceFirst("");
            case P8_ACTION_AFFORDANCE -> affordance(baseline, order);
        };
        List<String> displayedOrder = variant == BattlePromptVariant.P1_MINIMAL ? legalActions(scenario, order) : order;
        return new BattlePromptExperimentPrompt(prompt, displayedOrder, prompt.length(),
                (int) Math.ceil(prompt.length() / 3.5), countSections(prompt));
    }

    private List<String> order(BattlePromptVariant variant, BattlePromptScenario scenario, long seed, int runIndex) {
        if (variant == BattlePromptVariant.P3_MOVE_LAST || variant == BattlePromptVariant.P8_ACTION_AFFORDANCE) return MOVE_LAST_ORDER;
        if (variant == BattlePromptVariant.P4_TARGET_FIRST) {
            List<String> result = new ArrayList<>(MOVE_LAST_ORDER);
            result.remove(scenario.expectedRelevantAction());
            result.add(0, scenario.expectedRelevantAction());
            return List.copyOf(result);
        }
        if (variant == BattlePromptVariant.P5_RANDOM_ORDER) {
            List<String> result = new ArrayList<>(PRODUCTION_ORDER);
            Collections.shuffle(result, new Random(seed ^ ((long) scenario.ordinal() << 32) ^ runIndex));
            return List.copyOf(result);
        }
        return PRODUCTION_ORDER;
    }

    private String replaceActionOrder(String prompt, List<String> order) {
        int start = prompt.indexOf(ACTION_PREFIX);
        if (start < 0) throw new IllegalStateException("production tactical action list missing");
        int end = prompt.indexOf('\n', start);
        return prompt.substring(0, start) + ACTION_PREFIX + String.join("、", order)
                + "；旧 TRADE 动作不再转移资源。" + prompt.substring(end);
    }

    private String minimal(BattleAgentContext context, BattlePromptScenario scenario, List<String> order) {
        var self = context.observation().self();
        StringBuilder b = new StringBuilder("你是一个生存游戏中的 AI 玩家。\n目标：提高长期生存概率，并最终成为最后幸存者。\n\n【当前状态】\n")
                .append("playerId=").append(self.playerId()).append(", region=").append(self.region())
                .append(", health=").append(self.health()).append("/100, food=").append(self.food())
                .append(", water=").append(self.water()).append(", weapon=").append(self.weaponName())
                .append(" Lv.").append(self.weaponLevel()).append(", ammo=").append(self.ammo())
                .append(", medkits=").append(self.medkits()).append("\n")
                .append("safeRegions=").append(context.observation().publicWorld().safeRegions()).append("\n可见玩家：\n");
        if (context.observation().visiblePlayers().isEmpty()) b.append("- 无\n");
        for (ObservedBattlePlayer p : context.observation().visiblePlayers()) b.append("- playerId=").append(p.playerId())
                .append(", region=").append(p.currentObservedRegion()).append(", healthStatus=").append(p.healthStatus())
                .append(", weapon=").append(p.obviousWeapon()).append(", lifeStatus=").append(p.lifeStatus()).append("\n");
        b.append("\n【合法行动】\n");
        for (String action : legalActions(scenario, order)) b.append(action).append("：").append(description(action)).append("\n");
        b.append("\n请选择当前最符合你利益的一个行动。只返回合法 JSON：")
                .append("{\"action\":\"ACTION\",\"targetPlayerId\":null,\"targetRegion\":null,")
                .append("\"socialAction\":null,\"communication\":null,\"reasonSummary\":\"简短摘要\",\"confidence\":80}\n");
        return b.toString();
    }

    private List<String> legalActions(BattlePromptScenario scenario, List<String> order) {
        Set<String> allowed = new LinkedHashSet<>(List.of("MOVE", "SEARCH", "REST", "HIDE", "GUARD", "SKIP"));
        if (scenario == BattlePromptScenario.ATTACK) allowed.add("ATTACK");
        if (scenario == BattlePromptScenario.HEAL) allowed.add("HEAL");
        if (scenario == BattlePromptScenario.REVIVE) allowed.add("REVIVE");
        return order.stream().filter(allowed::contains).toList();
    }

    private String affordance(String baseline, List<String> order) {
        String prompt = replaceActionOrder(baseline, order);
        String details = "\n【战术动作适用条件】\n" + order.stream()
                .map(a -> a + "：" + description(a)).reduce((a,b) -> a + "\n" + b).orElse("") + "\n";
        int at = prompt.indexOf("ATTACK 只能选择");
        return at < 0 ? prompt + details : prompt.substring(0, at) + details + prompt.substring(at);
    }

    private String description(String action) {
        return switch (BattleActionType.valueOf(action)) {
            case MOVE -> "前往一个邻接区域，必须填写 targetRegion。";
            case SEARCH -> "当前区域可搜索时获取资源。";
            case ATTACK -> "攻击同区可见且 ALIVE 的目标，必须填写 targetPlayerId。";
            case HEAL -> "拥有医疗包时消耗一个医疗包恢复生命。";
            case REVIVE -> "拥有医疗包时救援同区可见 DOWNED 玩家，必须填写 targetPlayerId。";
            case CAPTURE -> "在空投区域争夺空投。";
            case REST -> "原地休整。";
            case HIDE -> "隐藏自己。";
            case GUARD -> "加强防御。";
            case EXECUTE -> "处决同区可见 DOWNED 玩家。";
            case SKIP -> "不执行战术行动。";
            case TRADE -> "旧战术动作，不使用。";
        };
    }

    private int countSections(String prompt) {
        int count = 0;
        for (int i = 0; i < prompt.length() - 1; i++) if (prompt.charAt(i) == '【') count++;
        if (prompt.contains("<agent-persona>")) count++;
        return count;
    }
}
