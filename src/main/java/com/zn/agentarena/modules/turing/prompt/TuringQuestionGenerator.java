package com.zn.agentarena.modules.turing.prompt;

import org.springframework.stereotype.Component;

/** 使用随机种子和轮次生成可重放的公共问题。 */
@Component
public class TuringQuestionGenerator {
    public String generate(long randomSeed, int roundNo) {
        return switch (roundNo) {
            case 1 -> TuringQuestionBank.FIRST_ROUND_QUESTIONS.get(
                    Math.floorMod(Long.hashCode(randomSeed), TuringQuestionBank.FIRST_ROUND_QUESTIONS.size()));
            case 2 -> "从上一轮其他玩家的回答中，选择一个你认为最值得质疑的回答，并说明原因。";
            default -> "你的怀疑或投票意向是否发生过变化？请选择一次变化并解释原因。";
        };
    }
}
