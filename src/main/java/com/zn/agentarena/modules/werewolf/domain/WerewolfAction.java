package com.zn.agentarena.modules.werewolf.domain;

import com.zn.agentarena.modules.game.engine.GameAction;

import java.util.List;

/**
 * 进入确定性规则引擎的狼人杀行动。核心目标已转换为本局玩家 ID，公开社会关系保留座位号。
  *
  * @param actorPlayerId actorPlayerId 字段
  * @param type type 字段
  * @param targetPlayerId targetPlayerId 字段
  * @param message message 字段
  * @param claims claims 字段
  * @param suspects suspects 字段
  * @param supports supports 字段
  * @param voteIntentSeatNo voteIntentSeatNo 字段
  * @param reasonTags reasonTags 字段
  * @param reasonSummary reasonSummary 字段
  * @param confidence confidence 字段
  */
public record WerewolfAction(
        Long actorPlayerId,
        WerewolfActionType type,
        Long targetPlayerId,
        String message,
        List<String> claims,
        List<Integer> suspects,
        List<Integer> supports,
        Integer voteIntentSeatNo,
        List<String> reasonTags,
        String reasonSummary,
        double confidence
) implements GameAction {

    /** 兼容夜间动作、旧调用方和历史测试。 */
    public WerewolfAction(Long actorPlayerId, WerewolfActionType type,
                          Long targetPlayerId, String message) {
        this(actorPlayerId, type, targetPlayerId, message, List.of(), List.of(), List.of(),
                null, List.of(), "", 0D);
    }

    public WerewolfAction {
        claims = claims == null ? List.of() : List.copyOf(claims);
        suspects = suspects == null ? List.of() : List.copyOf(suspects);
        supports = supports == null ? List.of() : List.copyOf(supports);
        reasonTags = reasonTags == null ? List.of() : List.copyOf(reasonTags);
    }

    /**
     * 返回用于事件记录的动作类型名称。
     *
     * @return 动作类型名称
     */
    @Override
    public String actionType() {
        return type.name();
    }
}
