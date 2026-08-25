package com.zn.agentarena.modules.spectator.service;

import java.util.Set;

/** Central field-level privacy policy shared by replay responses and live SSE events. */
public final class PublicEventPolicy {

    private static final Set<String> PRIVATE_ACTOR_EVENTS = Set.of(
            "AI_DECISION", "WOLF_TARGET_SELECTED", "GUARD_PROTECTED",
            "SEER_CHECKED", "WITCH_ACTION_COMPLETED",
            "TURING_ANSWER_SUBMITTED", "TURING_SUSPICION_SUBMITTED",
            "TURING_VOTE_SUBMITTED");

    private PublicEventPolicy() {
    }

    /**
     * 根据事件的隐私级别返回可公开的操作者标识。
     *
     * @param eventType 事件类型
     * @param actorPlayerId 原始操作者标识
     * @return 可公开的操作者标识；私密事件返回 {@code null}
     */
    public static Long publicActorPlayerId(String eventType, Long actorPlayerId) {
        return PRIVATE_ACTOR_EVENTS.contains(eventType) ? null : actorPlayerId;
    }
}
