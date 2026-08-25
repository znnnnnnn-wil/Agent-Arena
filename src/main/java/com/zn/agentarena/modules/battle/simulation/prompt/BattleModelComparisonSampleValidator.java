package com.zn.agentarena.modules.battle.simulation.prompt;

import com.zn.agentarena.modules.battle.simulation.trace.BattleDecisionPipelineTrace;

/** 防止 fallback、mock 或错误模型标签被计入真实模型对照。 */
public final class BattleModelComparisonSampleValidator {
    private BattleModelComparisonSampleValidator(){}
    public static void requireRealSample(BattleDecisionPipelineTrace trace, BattleModelComparisonGroup group){
        if(trace==null||!"MODEL".equals(trace.source())||trace.parserRepair()
                ||trace.rawOutput()==null||trace.rawOutput().isBlank()
                ||!group.modelName().equals(trace.model())||!"QWEN".equals(trace.provider())){
            throw new IllegalStateException("invalid real-model comparison sample for "+group);
        }
    }
}
