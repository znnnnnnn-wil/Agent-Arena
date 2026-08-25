package com.zn.agentarena.modules.werewolf.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 狼人杀模型侧结构化协议。
 *
 * <p>模型只应使用游戏内座位号。{@code targetPlayerId} 和 {@code publicMessage}
 * 仅用于短期兼容旧模型输出，不会出现在新提示词中。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WerewolfAiDecision(
        String selfRole,
        String action,
        Integer targetSeatNo,
        Long targetPlayerId,
        String publicSpeech,
        String publicMessage,
        List<String> claims,
        List<Integer> suspects,
        List<Integer> supports,
        Integer voteIntentSeatNo,
        List<String> reasonTags,
        String reasonSummary,
        Double confidence
) {
    /** 保留上一版 Java 调用方和旧测试使用的构造方式。 */
    public WerewolfAiDecision(String selfRole, String action, Long targetPlayerId,
                              String publicMessage, String reasonSummary, Integer confidence) {
        this(selfRole, action, null, targetPlayerId, null, publicMessage,
                List.of(), List.of(), List.of(), null, List.of(), reasonSummary,
                confidence == null ? null : confidence.doubleValue());
    }
}
