package com.zn.agentarena.modules.turing.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 模型侧统一决策协议。模型只接触座位号，不存在任何数据库玩家 ID 字段。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TuringHuntAiDecision(
        String action,
        String answer,
        Integer primarySuspectSeatNo,
        List<Integer> suspectSeatNos,
        List<String> reasonTags,
        String reasonSummary,
        Integer targetSeatNo,
        String question,
        String response,
        String defense,
        Integer counterSuspectSeatNo
) {
    public TuringHuntAiDecision {
        suspectSeatNos = suspectSeatNos == null ? List.of() : List.copyOf(suspectSeatNos);
        reasonTags = reasonTags == null ? List.of() : List.copyOf(reasonTags);
    }
}
