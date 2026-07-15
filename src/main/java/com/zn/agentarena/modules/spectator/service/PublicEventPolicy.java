package com.zn.agentarena.modules.spectator.service;

import java.util.Set;

/** Central field-level privacy policy shared by replay responses and live SSE events. */
public final class PublicEventPolicy {

    private static final Set<String> PRIVATE_ACTOR_EVENTS = Set.of(
            "AI_DECISION", "WOLF_TARGET_SELECTED", "GUARD_PROTECTED",
            "SEER_CHECKED", "WITCH_ACTION_COMPLETED");

    private PublicEventPolicy() {
    }

    public static Long publicActorPlayerId(String eventType, Long actorPlayerId) {
        return PRIVATE_ACTOR_EVENTS.contains(eventType) ? null : actorPlayerId;
    }
}
