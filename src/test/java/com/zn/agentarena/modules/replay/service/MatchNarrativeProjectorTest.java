package com.zn.agentarena.modules.replay.service;

import com.zn.agentarena.common.enums.GameMode;
import com.zn.agentarena.dao.entity.GameEvent;
import com.zn.agentarena.dao.entity.GamePlayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MatchNarrativeProjectorTest {

    private final MatchNarrativeProjector projector = new MatchNarrativeProjector();

    @Test
    void projectsStableShareSafeWerewolfHighlight() {
        List<GamePlayer> players = List.of(
                GamePlayer.builder().id(11L).displayName("Alpha").build(),
                GamePlayer.builder().id(12L).displayName("Bravo").build());
        List<GameEvent> events = List.of(
                event(40, "VOTE_RESULT", null, Map.of("secret", "role"),
                        Map.of("candidateIds", List.of(12L), "counts", Map.of("12", 6)), 2, "DAY_VOTE"),
                event(41, "PLAYER_ELIMINATED", 12L, Map.of("role", "WEREWOLF"),
                        Map.of("playerId", 12L, "reason", "VOTE"), 2, "DAY_VOTE"));

        var first = projector.project(9L, GameMode.WEREWOLF, events, players);
        var second = projector.project(9L, GameMode.WEREWOLF, events, players);

        assertEquals(1, first.getHighlights().size());
        assertEquals("g9-s40-vote-elimination", first.getHighlights().get(0).getClipId());
        assertEquals(first.getHighlights().get(0).getClipId(), second.getHighlights().get(0).getClipId());
        assertEquals(40L, first.getHighlights().get(0).getStartSequenceNo());
        assertEquals(41L, first.getHighlights().get(0).getEndSequenceNo());
        assertFalse(first.getHighlights().get(0).getNarration().contains("WEREWOLF"));
    }

    @Test
    void recognizesCriticalHitAndSupplyDropCapture() {
        List<GamePlayer> players = List.of(
                GamePlayer.builder().id(21L).displayName("猎手").build(),
                GamePlayer.builder().id(22L).displayName("斥候").build());
        List<GameEvent> events = List.of(
                event(9, "BATTLE_ACTION_EXECUTED", 21L, Map.of(),
                        Map.of("action", "ATTACK", "targetPlayerId", 22L, "damage", 52,
                                "targetHealth", 18, "critical", true, "health", 70), 3, "ROUND"),
                event(10, "BATTLE_ACTION_EXECUTED", 22L, Map.of(),
                        Map.of("action", "SEARCH", "supplyDrop", true, "lootName", "精确步枪",
                                "health", 18), 3, "ROUND"));

        var projection = projector.project(20L, GameMode.BATTLE_ROYALE, events, players);

        assertEquals(List.of("CRITICAL_HIT", "SUPPLY_DROP_CAPTURE"), projection.getHighlights().stream()
                .map(item -> item.getCategory()).toList());
        assertEquals(2, projection.getCommentary().size());
        assertEquals(5, projection.getHighlights().get(0).getImportance());
    }

    @Test
    void explainsStanceChangesMutualSuspicionConcentrationAndVoteLeadChanges() {
        List<GamePlayer> players = List.of(
                player(11L, 1, "Alpha"), player(12L, 2, "Bravo"),
                player(13L, 3, "Charlie"), player(14L, 4, "Delta"));
        List<GameEvent> events = List.of(
                publicEvent(1, "PLAYER_SPOKE", 11L, Map.of("actorSeatNo", 1, "claims", List.of("VILLAGER"),
                        "suspects", List.of(3), "voteIntentSeatNo", 3, "reasonSummary", "3号前后矛盾")),
                publicEvent(2, "PLAYER_SPOKE", 12L, Map.of("actorSeatNo", 2, "claims", List.of("VILLAGER"),
                        "suspects", List.of(3), "voteIntentSeatNo", 3, "reasonSummary", "跟进核对3号")),
                publicEvent(3, "PLAYER_SPOKE", 13L, Map.of("actorSeatNo", 3, "claims", List.of("VILLAGER"),
                        "suspects", List.of(1), "voteIntentSeatNo", 1, "reasonSummary", "反驳1号依据")),
                publicEvent(4, "PLAYER_SPOKE", 14L, Map.of("actorSeatNo", 4, "claims", List.of(),
                        "suspects", List.of(3), "voteIntentSeatNo", 3, "reasonSummary", "3号仍未解释")),
                publicEvent(5, "PLAYER_FINAL_STATEMENT", 11L, Map.of("actorSeatNo", 1, "claims", List.of("SEER"),
                        "suspects", List.of(3), "voteIntentSeatNo", 4, "reasonSummary", "新证据指向4号")),
                publicEvent(6, "PLAYER_FINAL_STATEMENT", 12L, Map.of("actorSeatNo", 2, "claims", List.of("SEER"),
                        "suspects", List.of(3), "voteIntentSeatNo", 3, "reasonSummary", "公开修改声明")),
                publicEvent(7, "PLAYER_VOTED", 11L, Map.of("actorSeatNo", 1, "targetPlayerId", 13L, "targetSeatNo", 3)),
                publicEvent(8, "PLAYER_VOTED", 12L, Map.of("actorSeatNo", 2, "targetPlayerId", 14L, "targetSeatNo", 4)),
                publicEvent(9, "PLAYER_VOTED", 13L, Map.of("actorSeatNo", 3, "targetPlayerId", 14L, "targetSeatNo", 4)));

        var projection = projector.project(9L, GameMode.WEREWOLF, events, players);
        List<String> titles = projection.getCommentary().stream().map(item -> item.getTitle()).toList();
        List<String> narrations = projection.getCommentary().stream().map(item -> item.getNarration()).toList();

        assertTrue(titles.stream().anyMatch(title -> title.contains("互相怀疑")));
        assertTrue(titles.stream().anyMatch(title -> title.contains("最受怀疑")));
        assertTrue(narrations.stream().anyMatch(text -> text.contains("从 3号转向 4号")));
        assertTrue(narrations.stream().anyMatch(text -> text.contains("声明由 VILLAGER 变为 SEER")));
        assertTrue(narrations.stream().anyMatch(text -> text.contains("领先者由 Charlie 变为 Delta")));
    }

    private GamePlayer player(long id, int seatNo, String name) {
        return GamePlayer.builder().id(id).seatNo(seatNo).displayName(name).build();
    }

    private GameEvent publicEvent(long sequence, String type, Long actor, Map<String, Object> publicPayload) {
        return event(sequence, type, actor, Map.of(), publicPayload, 2,
                type.equals("PLAYER_VOTED") ? "DAY_VOTE" : "DAY_DISCUSSION");
    }

    private GameEvent event(long sequence, String type, Long actor, Map<String, Object> payload,
                            Map<String, Object> publicPayload, int round, String phase) {
        return GameEvent.builder().gameId(9L).sequenceNo(sequence).eventType(type).actorPlayerId(actor)
                .payload(payload).publicPayload(publicPayload).roundNo(round).phase(phase).build();
    }
}
