package com.zn.agentarena.modules.battle.simulation.prompt;

import com.zn.agentarena.modules.battle.agent.BattleAgentContext;
import com.zn.agentarena.modules.battle.agent.BattleAgentContextBuilder;
import java.util.List;

/** Decision Objective 单变量实验构建器；不修改生产 Prompt 构建器。 */
public final class BattleDecisionObjectiveBuilder {
    public static final String PRODUCTION_OBJECTIVE =
            "你正在参加一局多智能体大逃杀，目标是成为最后幸存者。\n";
    public static final String BALANCED_OBJECTIVE = """
            本局最终胜利条件仍是成为最后幸存者。
            你的任务是在每次行动时，根据当前实际知道的信息选择当前最合理的行动。
            做决定时，同时比较当前即时状态与风险、已经出现的明确行动机会、需要处理的社会关系、协议或义务、资源与位置的后续影响，以及最终获胜这一长期目标。
            不要因为某一种长期战略通常有价值，就忽略当前已经存在的明显状态或机会；每次都应依据当前局势重新判断总体收益。
            """;
    public static final String CHECKLIST_OBJECTIVE = """
            本局最终胜利条件仍是成为最后幸存者。
            在选择行动时，按以下检查框架权衡当前实际信息：
            第一，检查是否存在需要立即处理的状态，如严重受伤、直接危险、倒地玩家或迫近环境风险。
            第二，检查是否存在当前即可执行且收益明确的机会，如攻击、搜索、治疗、救援、交易或履行已有协议。
            第三，再考虑移动、区域资源规划、长期目标与未来布局。
            这不是固定优先级规则；如果后项整体收益更高仍可选择后项。你必须根据当前信息自行权衡。
            """;
    public static final String OPPORTUNITY_OBJECTIVE = """
            本局最终胜利条件仍是成为最后幸存者。
            根据当前观察选择行动：先比较当前已经合法且可立即执行的具体机会，再考虑为未来资源或位置而行动。
            长期胜利条件很重要，但不能自动覆盖当前已经出现的具体机会。
            选择你认为当前总体收益最高的行动。
            """;

    private final BattleAgentContextBuilder production = new BattleAgentContextBuilder();

    public BattleDecisionObjectivePrompt build(BattleDecisionObjectiveVariant variant,
                                                BattleAgentContext context) {
        String baseline = production.build(context);
        requireOnce(baseline, PRODUCTION_OBJECTIVE);
        String objective = objective(variant);
        String prompt = variant == BattleDecisionObjectiveVariant.D0_CURRENT
                ? baseline : baseline.replace(PRODUCTION_OBJECTIVE, objective);
        List<String> changed = variant == BattleDecisionObjectiveVariant.D0_CURRENT
                ? List.of() : List.of("DECISION_OBJECTIVE");
        return new BattleDecisionObjectivePrompt(prompt, objective, changed);
    }

    public String productionPrompt(BattleAgentContext context) { return production.build(context); }

    public String objective(BattleDecisionObjectiveVariant variant) {
        return switch (variant) {
            case D0_CURRENT -> PRODUCTION_OBJECTIVE;
            case D1_BALANCED_OBJECTIVE -> BALANCED_OBJECTIVE;
            case D2_DECISION_CHECKLIST -> CHECKLIST_OBJECTIVE;
            case D3_OPPORTUNITY_FIRST_NEUTRAL -> OPPORTUNITY_OBJECTIVE;
        };
    }

    private void requireOnce(String prompt, String value) {
        int first = prompt.indexOf(value);
        if (first < 0 || prompt.indexOf(value, first + value.length()) >= 0) {
            throw new IllegalStateException("production decision objective must occur exactly once");
        }
    }
}
