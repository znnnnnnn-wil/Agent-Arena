package com.zn.agentarena.modules.turing.domain;

import com.zn.agentarena.modules.game.engine.GameAction;

import java.util.List;

/**
 * 进入确定性引擎的图灵潜伏局行动；玩家引用均已在边界转换为内部 ID。
 *
 * @param actorPlayerId actorPlayerId 字段
 * @param type type 字段
 * @param answer answer 字段
 * @param primarySuspectPlayerId primarySuspectPlayerId 字段
 * @param suspectPlayerIds suspectPlayerIds 字段
 * @param reasonTags reasonTags 字段
 * @param reasonSummary reasonSummary 字段
 * @param targetPlayerId targetPlayerId 字段
 * @param question question 字段
 * @param response response 字段
 * @param defense defense 字段
 * @param counterSuspectPlayerId counterSuspectPlayerId 字段
 */
public record TuringHuntAction(
        Long actorPlayerId,
        TuringHuntActionType type,
        String answer,
        Long primarySuspectPlayerId,
        List<Long> suspectPlayerIds,
        List<String> reasonTags,
        String reasonSummary,
        Long targetPlayerId,
        String question,
        String response,
        String defense,
        Long counterSuspectPlayerId
) implements GameAction {
    public TuringHuntAction {
        suspectPlayerIds = suspectPlayerIds == null ? List.of() : List.copyOf(suspectPlayerIds);
        reasonTags = reasonTags == null ? List.of() : List.copyOf(reasonTags);
    }

    /**
     * 创建用于推进确定性状态转换的空动作。
     *
     * @return 推进动作
     */
    public static TuringHuntAction advance() {
        return new TuringHuntAction(null, TuringHuntActionType.ADVANCE, null, null,
                List.of(), List.of(), null, null, null, null, null, null);
    }

    /**
     * 返回用于事件记录的动作类型名称。
     *
     * @return 动作类型名称
     */
    @Override
    public String actionType() {
        return type == null ? null : type.name();
    }
}
