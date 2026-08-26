package com.zn.agentarena.modules.replay.service;

import com.zn.agentarena.dao.entity.GameEvent;
import com.zn.agentarena.dao.entity.GamePlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("狼人杀公开局势投影测试")
class WerewolfSituationProjectorTest {
    private final WerewolfSituationProjector projector = new WerewolfSituationProjector();

    @Test
    @DisplayName("只使用公开白天载荷生成怀疑、支持和投票意向关系")
    void projectsOnlyPublicDaytimeRelationships() {
        List<GamePlayer> players = List.of(player(11L, 1, "黑鸦"), player(12L, 2, "猎犬"),
                player(13L, 3, "白塔"));
        List<GameEvent> events = List.of(
                event(1, "SEER_CHECKED", 13L, Map.of("targetPlayerId", 12L, "wolf", true),
                        Map.of("completed", true), 1, "NIGHT_SEER"),
                event(2, "PLAYER_SPOKE", 11L, Map.of("selfRole", "WEREWOLF"),
                        Map.of("actorSeatNo", 1, "claims", List.of("VILLAGER"),
                                "suspects", List.of(2, 99), "supports", List.of(3),
                                "voteIntentSeatNo", 2, "reasonTags", List.of("VOTE_SHIFT"),
                                "reasonSummary", "2号立场变化"), 1, "DAY_DISCUSSION"),
                event(3, "PLAYER_SPOKE", 12L, Map.of("wolfTeam", List.of(11L)),
                        Map.of("actorSeatNo", 2, "claims", List.of(), "suspects", List.of(1),
                                "supports", List.of(), "voteIntentSeatNo", 1,
                                "reasonTags", List.of("COUNTER_SUSPECT"),
                                "reasonSummary", "回应1号怀疑"), 1, "DAY_DISCUSSION"));

        var projection = projector.project(events, players);

        assertEquals(2, projection.getPlayers().size());
        assertEquals(List.of(2), projection.getPlayers().get(0).getSuspects().stream()
                .map(item -> item.getSeatNo()).toList());
        assertEquals("白塔", projection.getPlayers().get(0).getSupports().get(0).getDisplayName());
        assertEquals(5, projection.getRelationships().size());
        assertTrue(projection.getRelationships().stream().anyMatch(item -> "SUSPECT".equals(item.getType())
                && item.getFrom().getSeatNo() == 1 && item.getTo().getSeatNo() == 2));
        assertTrue(projection.getRelationships().stream().anyMatch(item -> "SUSPECT".equals(item.getType())
                && item.getFrom().getSeatNo() == 2 && item.getTo().getSeatNo() == 1));
        assertFalse(projection.toString().contains("WEREWOLF"));
        assertFalse(projection.toString().contains("wolfTeam"));
        assertFalse(projection.toString().contains("SEER_CHECKED"));
    }

    private GamePlayer player(Long id, int seatNo, String name) {
        return GamePlayer.builder().id(id).seatNo(seatNo).displayName(name).build();
    }

    private GameEvent event(long sequence, String type, Long actor, Map<String, Object> privatePayload,
                            Map<String, Object> publicPayload, int round, String phase) {
        return GameEvent.builder().sequenceNo(sequence).eventType(type).actorPlayerId(actor)
                .payload(privatePayload).publicPayload(publicPayload).roundNo(round).phase(phase).build();
    }
}
