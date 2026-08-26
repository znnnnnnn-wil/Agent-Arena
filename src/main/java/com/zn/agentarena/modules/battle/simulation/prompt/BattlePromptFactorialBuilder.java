package com.zn.agentarena.modules.battle.simulation.prompt;

import com.zn.agentarena.modules.battle.agent.BattleAgentContext;
import com.zn.agentarena.modules.battle.agent.BattleAgentContextBuilder;
import java.util.*;

/** 2×2 因果消融器；只替换两个已审计的生产 Prompt 片段。 */
public final class BattlePromptFactorialBuilder {
    public static final String LONG_TERM_PRODUCTION = "你正在参加一局多智能体大逃杀，目标是成为最后幸存者。\n";
    public static final String LONG_TERM_NEUTRAL = "你需要根据当前观察和可执行行动，选择此刻最合理的行动，并遵守游戏规则。\n";
    public static final String REGION_AFFORDANCE_PRODUCTION =
            "区域资源只是概率倾向，不是移动命令：A偏武器、B偏食物、C偏饮水与弹药、D偏护甲、E偏医疗。先处理当前致死风险，再考虑区域收益。\n";
    private final BattleAgentContextBuilder production = new BattleAgentContextBuilder();

    public BattlePromptFactorialPrompt build(BattlePromptFactorialVariant variant, BattleAgentContext context) {
        String prompt = production.build(context);
        requireOnce(prompt, LONG_TERM_PRODUCTION, "long-term goal");
        requireOnce(prompt, REGION_AFFORDANCE_PRODUCTION, "region affordance");
        List<String> changed = new ArrayList<>();
        if (!variant.longTermGoalPresent()) {
            prompt = prompt.replace(LONG_TERM_PRODUCTION, LONG_TERM_NEUTRAL);
            changed.add("LONG_TERM_GOAL");
        }
        if (!variant.regionAffordancePresent()) {
            prompt = prompt.replace(REGION_AFFORDANCE_PRODUCTION, "");
            changed.add("REGION_AFFORDANCE");
        }
        validateFactorAbsence(variant, prompt);
        return new BattlePromptFactorialPrompt(prompt, variant.longTermGoalPresent(),
                variant.regionAffordancePresent(), List.copyOf(changed));
    }

    public String productionPrompt(BattleAgentContext context) { return production.build(context); }

    private void validateFactorAbsence(BattlePromptFactorialVariant variant, String prompt) {
        if (!variant.longTermGoalPresent()) {
            for (String phrase : List.of("目标是成为最后幸存者", "提高长期生存概率", "长期生存", "生存到最后")) {
                if (prompt.contains(phrase)) throw new IllegalStateException("A- still contains strategic phrase: " + phrase);
            }
        }
        if (!variant.regionAffordancePresent()) {
            for (String phrase : List.of("区域资源只是概率倾向", "A偏武器", "B偏食物", "C偏饮水与弹药", "D偏护甲", "E偏医疗")) {
                if (prompt.contains(phrase)) throw new IllegalStateException("B- still contains region mapping: " + phrase);
            }
        }
    }

    private void requireOnce(String prompt, String value, String label) {
        int first = prompt.indexOf(value);
        if (first < 0 || prompt.indexOf(value, first + value.length()) >= 0) {
            throw new IllegalStateException(label + " production fragment must occur exactly once");
        }
    }
}
