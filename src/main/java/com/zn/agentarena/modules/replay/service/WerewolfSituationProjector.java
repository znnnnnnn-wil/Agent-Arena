package com.zn.agentarena.modules.replay.service;

import com.zn.agentarena.dao.entity.GameEvent;
import com.zn.agentarena.dao.entity.GamePlayer;
import com.zn.agentarena.modules.replay.dto.response.WerewolfSituationResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 从公开事件载荷构建关系图，不读取角色、私有载荷或夜间秘密。 */
public final class WerewolfSituationProjector {
    private static final Set<String> PUBLIC_STANCE_EVENTS = Set.of(
            "PLAYER_SPOKE", "PLAYER_QUESTIONED", "PLAYER_RESPONDED", "PLAYER_FINAL_STATEMENT");

    /**
     * 从公开事件和玩家信息中生成狼人杀局势关系图。
     *
     * @param events 已公开的游戏事件
     * @param players 游戏玩家
     * @return 用于复盘展示的局势数据
     */
    public WerewolfSituationResponse project(List<GameEvent> events, List<GamePlayer> players) {
        Map<Long, GamePlayer> byId = players.stream().collect(Collectors.toMap(GamePlayer::getId,
                Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<Integer, GamePlayer> bySeat = players.stream().filter(player -> player.getSeatNo() != null)
                .collect(Collectors.toMap(GamePlayer::getSeatNo, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        Map<Integer, MutableStance> stances = new LinkedHashMap<>();
        List<GameEvent> safeEvents = events == null ? List.of() : events;
        int currentRound = safeEvents.stream().map(GameEvent::getRoundNo).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0);
        String phase = safeEvents.isEmpty() ? null : safeEvents.get(safeEvents.size() - 1).getPhase();

        for (GameEvent event : safeEvents) {
            if (!PUBLIC_STANCE_EVENTS.contains(event.getEventType())) continue;
            Map<String, Object> payload = publicPayload(event);
            GamePlayer actor = byId.get(event.getActorPlayerId());
            Integer actorSeat = intValue(payload.get("actorSeatNo"));
            if (actorSeat == null && actor != null) actorSeat = actor.getSeatNo();
            if (actorSeat == null || !bySeat.containsKey(actorSeat)) continue;
            MutableStance stance = stances.computeIfAbsent(actorSeat, MutableStance::new);
            boolean replacesRelations = !"PLAYER_QUESTIONED".equals(event.getEventType());
            if (replacesRelations) {
                stance.claims = stringList(payload.get("claims"));
                stance.suspects = seatList(payload.get("suspects"), bySeat);
                stance.supports = seatList(payload.get("supports"), bySeat);
                stance.voteIntent = validSeat(payload.get("voteIntentSeatNo"), bySeat);
            }
            stance.reasonTags = stringList(payload.get("reasonTags"));
            stance.reasonSummary = stringValue(payload.get("reasonSummary"));
            stance.lastAction = actionLabel(event.getEventType());
            stance.updatedRound = event.getRoundNo();
        }

        Map<Integer, Integer> suspectCounts = new LinkedHashMap<>();
        Map<Integer, Integer> supportCounts = new LinkedHashMap<>();
        Map<Integer, Integer> voteIntentCounts = new LinkedHashMap<>();
        List<WerewolfSituationResponse.Relationship> relationships = new ArrayList<>();
        for (MutableStance stance : stances.values()) {
            stance.suspects.forEach(seat -> {
                suspectCounts.merge(seat, 1, Integer::sum);
                relationships.add(relationship("SUSPECT", stance.seatNo, seat, bySeat));
            });
            stance.supports.forEach(seat -> {
                supportCounts.merge(seat, 1, Integer::sum);
                relationships.add(relationship("SUPPORT", stance.seatNo, seat, bySeat));
            });
            if (stance.voteIntent != null) {
                voteIntentCounts.merge(stance.voteIntent, 1, Integer::sum);
                relationships.add(relationship("VOTE_INTENT", stance.seatNo, stance.voteIntent, bySeat));
            }
        }
        for (GameEvent event : safeEvents) {
            if (!"PLAYER_VOTED".equals(event.getEventType()) || !Objects.equals(currentRound, event.getRoundNo())) continue;
            Map<String, Object> payload = publicPayload(event);
            Integer from = validSeat(payload.get("actorSeatNo"), bySeat);
            Integer to = validSeat(payload.get("targetSeatNo"), bySeat);
            if (from != null && to != null) relationships.add(relationship("VOTE", from, to, bySeat));
        }

        List<WerewolfSituationResponse.Tally> suspicionRanking = ranking(suspectCounts, bySeat);
        List<WerewolfSituationResponse.Tally> supportRanking = ranking(supportCounts, bySeat);
        List<WerewolfSituationResponse.Tally> voteDistribution = ranking(voteIntentCounts, bySeat);
        List<WerewolfSituationResponse.PlayerRef> controversial = suspicionRanking.stream()
                .filter(item -> item.getCount() > 0).limit(2).map(WerewolfSituationResponse.Tally::getPlayer).toList();
        WerewolfSituationResponse.PlayerRef mostSuspected = controversial.isEmpty() ? null : controversial.get(0);
        List<WerewolfSituationResponse.PlayerStance> playerStances = stances.values().stream()
                .sorted(Comparator.comparingInt(item -> item.seatNo))
                .map(item -> toResponse(item, bySeat)).toList();

        return WerewolfSituationResponse.builder().roundNo(currentRound).phase(phase)
                .players(playerStances).suspicionRanking(suspicionRanking).supportRanking(supportRanking)
                .voteIntentDistribution(voteDistribution).controversialPlayers(controversial)
                .mostSuspected(mostSuspected).relationships(List.copyOf(relationships)).build();
    }

    private WerewolfSituationResponse.PlayerStance toResponse(MutableStance stance,
                                                               Map<Integer, GamePlayer> bySeat) {
        return WerewolfSituationResponse.PlayerStance.builder()
                .seatNo(stance.seatNo).displayName(name(bySeat.get(stance.seatNo)))
                .claims(List.copyOf(stance.claims))
                .suspects(stance.suspects.stream().map(seat -> ref(seat, bySeat)).toList())
                .supports(stance.supports.stream().map(seat -> ref(seat, bySeat)).toList())
                .voteIntent(stance.voteIntent == null ? null : ref(stance.voteIntent, bySeat))
                .reasonTags(List.copyOf(stance.reasonTags)).reasonSummary(stance.reasonSummary)
                .lastAction(stance.lastAction).updatedRound(stance.updatedRound).build();
    }

    private List<WerewolfSituationResponse.Tally> ranking(Map<Integer, Integer> counts,
                                                           Map<Integer, GamePlayer> bySeat) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> WerewolfSituationResponse.Tally.builder()
                        .player(ref(entry.getKey(), bySeat)).count(entry.getValue()).build()).toList();
    }

    private WerewolfSituationResponse.Relationship relationship(String type, Integer from, Integer to,
                                                                  Map<Integer, GamePlayer> bySeat) {
        return WerewolfSituationResponse.Relationship.builder().type(type)
                .from(ref(from, bySeat)).to(ref(to, bySeat)).build();
    }

    private WerewolfSituationResponse.PlayerRef ref(Integer seatNo, Map<Integer, GamePlayer> bySeat) {
        GamePlayer player = bySeat.get(seatNo);
        return WerewolfSituationResponse.PlayerRef.builder().seatNo(seatNo).displayName(name(player)).build();
    }

    private String name(GamePlayer player) {
        return player == null || player.getDisplayName() == null ? "未知玩家" : player.getDisplayName();
    }

    private Map<String, Object> publicPayload(GameEvent event) {
        return event.getPublicPayload() == null ? Map.of() : event.getPublicPayload();
    }

    private List<Integer> seatList(Object value, Map<Integer, GamePlayer> bySeat) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(this::intValue).filter(Objects::nonNull).filter(bySeat::containsKey)
                .distinct().toList();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }

    private Integer validSeat(Object value, Map<Integer, GamePlayer> bySeat) {
        Integer seat = intValue(value);
        return seat != null && bySeat.containsKey(seat) ? seat : null;
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? null : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String actionLabel(String eventType) {
        return switch (eventType) {
            case "PLAYER_QUESTIONED" -> "QUESTION";
            case "PLAYER_RESPONDED" -> "RESPOND";
            case "PLAYER_FINAL_STATEMENT" -> "FINAL_STATEMENT";
            default -> "SPEAK";
        };
    }

    private static final class MutableStance {
        private final int seatNo;
        private List<String> claims = List.of();
        private List<Integer> suspects = List.of();
        private List<Integer> supports = List.of();
        private Integer voteIntent;
        private List<String> reasonTags = List.of();
        private String reasonSummary;
        private String lastAction;
        private Integer updatedRound;

        private MutableStance(int seatNo) {
            this.seatNo = seatNo;
        }
    }
}
