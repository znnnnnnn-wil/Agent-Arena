package com.zn.agentarena.modules.turing.prompt;

import java.util.List;

/** 不依赖场外信息、身体经历或模型调用的第一轮固定题库。 */
public final class TuringQuestionBank {
    public static final List<String> FIRST_ROUND_QUESTIONS = List.of(
            "你必须删除地图、时钟和镜子中的一个，你会删除哪个？请说明理由。",
            "在永远不会迷路和永远不会忘记之间选择一种能力，并说明原因。",
            "一个规则必须对所有人公平，但结果不一定公平。你是否接受？说明理由。",
            "请选择一个看似没用、但在特殊情况下很重要的物品。",
            "如果必须让团队中的一个人拥有否决权，你会如何限制这项权力？",
            "当效率和可解释性冲突时，你会优先保留哪一个？请给出判断标准。",
            "如果只能用一个原则处理信息不足的决定，你会选什么原则？"
    );

    private TuringQuestionBank() {
    }
}
