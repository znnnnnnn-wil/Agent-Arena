package com.zn.agentarena.modules.battle.simulation.prompt;

import com.zn.agentarena.modules.battle.agent.BattleAgentContext;
import com.zn.agentarena.modules.battle.agent.BattleAgentContextBuilder;
import java.util.*;

/** 仅在生产 Prompt 的输出约束前插入实验 Affordance Section。 */
public final class BattleActionAffordanceBuilder {
    public static final String INSERTION_MARKER = "只返回合法 JSON，不要 Markdown 或解释。\n";
    private final BattleAgentContextBuilder production = new BattleAgentContextBuilder();

    public BattleActionAffordancePrompt build(BattleActionAffordanceVariant variant,
                                              BattleAgentContext context,
                                              List<BattleActionAffordance> affordances) {
        String baseline = production.build(context);
        requireOnce(baseline, INSERTION_MARKER);
        if (variant == BattleActionAffordanceVariant.A0_CURRENT) {
            return new BattleActionAffordancePrompt(baseline, null, List.of());
        }
        String section = section(variant, affordances == null ? List.of() : affordances);
        String prompt = baseline.replace(INSERTION_MARKER, section + INSERTION_MARKER);
        return new BattleActionAffordancePrompt(prompt, section, List.of("ACTION_AFFORDANCE"));
    }

    public String productionPrompt(BattleAgentContext context) { return production.build(context); }

    String section(BattleActionAffordanceVariant variant, List<BattleActionAffordance> affordances) {
        StringBuilder b = new StringBuilder("\n【CURRENT ACTION AFFORDANCES｜实验动态动作能力】\n");
        if (variant == BattleActionAffordanceVariant.A1_LEGAL_ACTION_NAMES) {
            b.append("根据当前可观察状态与生产规则校验，当前可执行的动作名称：\n");
            affordances.forEach(a -> b.append("- ").append(a.action()).append("\n"));
        } else if (variant == BattleActionAffordanceVariant.A2_ACTION_PRECONDITIONS) {
            b.append("根据当前可观察状态与生产规则校验，以下动作当前可执行：\n");
            affordances.forEach(a -> b.append("- ").append(a.action()).append("\n  可执行条件：")
                    .append(a.executableCondition()).append("。\n"));
        } else if (variant == BattleActionAffordanceVariant.A3_DYNAMIC_AFFORDANCE) {
            b.append("以下候选已由程序根据当前可观察状态和生产规则完成可执行性校验：\n");
            affordances.forEach(a -> {
                b.append(a.action()).append("\n- currently executable: yes\n");
                a.relevantState().forEach((key, value) -> b.append("- ").append(key).append(": ").append(value).append("\n"));
            });
        } else throw new IllegalArgumentException("A0 has no inserted section");
        b.append("该列表只陈述可执行性，不表示推荐、优先级或收益排序。\n\n");
        return b.toString();
    }

    private void requireOnce(String prompt, String value) {
        int first = prompt.indexOf(value);
        if (first < 0 || prompt.indexOf(value, first + value.length()) >= 0)
            throw new IllegalStateException("production insertion marker must occur exactly once");
    }
}
