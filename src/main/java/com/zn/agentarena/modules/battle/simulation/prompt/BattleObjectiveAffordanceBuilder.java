package com.zn.agentarena.modules.battle.simulation.prompt;

import com.zn.agentarena.modules.battle.agent.BattleAgentContext;
import java.util.*;

/** 只重组已经测试过的 D3 与 A3，不创造任何新 Prompt 文本。 */
public final class BattleObjectiveAffordanceBuilder {
    private final BattleDecisionObjectiveBuilder objectives = new BattleDecisionObjectiveBuilder();
    private final BattleActionAffordanceBuilder affordances = new BattleActionAffordanceBuilder();

    public BattleObjectiveAffordancePrompt build(BattleObjectiveAffordanceVariant variant,
                                                 BattleAgentContext context,
                                                 List<BattleActionAffordance> computed) {
        String production = objectives.productionPrompt(context);
        String prompt = production;
        String section = null;
        List<String> changed = new ArrayList<>();
        if (variant.d3Objective()) {
            prompt = prompt.replace(BattleDecisionObjectiveBuilder.PRODUCTION_OBJECTIVE,
                    BattleDecisionObjectiveBuilder.OPPORTUNITY_OBJECTIVE);
            changed.add("DECISION_OBJECTIVE");
        }
        if (variant.a3Affordance()) {
            section = affordances.section(BattleActionAffordanceVariant.A3_DYNAMIC_AFFORDANCE,
                    computed == null ? List.of() : computed);
            prompt = prompt.replace(BattleActionAffordanceBuilder.INSERTION_MARKER,
                    section + BattleActionAffordanceBuilder.INSERTION_MARKER);
            changed.add("ACTION_AFFORDANCE");
        }
        return new BattleObjectiveAffordancePrompt(prompt,
                variant.d3Objective() ? "D3_OPPORTUNITY_FIRST_NEUTRAL" : "CURRENT_PRODUCTION_OBJECTIVE",
                variant.a3Affordance() ? "A3_DYNAMIC_AFFORDANCE" : "NO_AFFORDANCE",
                section, List.copyOf(changed));
    }

    public String productionPrompt(BattleAgentContext context) { return objectives.productionPrompt(context); }
}
