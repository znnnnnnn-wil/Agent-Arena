package com.zn.agentarena.modules.turing.domain;

import java.util.List;

/**
 * 一次秘密怀疑提交，内部引用使用本局玩家 ID。
 *
 * @param primarySuspectPlayerId primarySuspectPlayerId 字段
 * @param suspectPlayerIds suspectPlayerIds 字段
 * @param reasonTags reasonTags 字段
 * @param reasonSummary reasonSummary 字段
 */
public record TuringHuntSuspicion(
        Long primarySuspectPlayerId,
        List<Long> suspectPlayerIds,
        List<String> reasonTags,
        String reasonSummary
) {
    public TuringHuntSuspicion {
        suspectPlayerIds = suspectPlayerIds == null ? List.of() : List.copyOf(suspectPlayerIds);
        reasonTags = reasonTags == null ? List.of() : List.copyOf(reasonTags);
    }
}
