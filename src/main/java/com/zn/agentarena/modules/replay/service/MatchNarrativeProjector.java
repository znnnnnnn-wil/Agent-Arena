package com.zn.agentarena.modules.replay.service;

import com.zn.agentarena.common.enums.GameMode;
import com.zn.agentarena.dao.entity.GameEvent;
import com.zn.agentarena.dao.entity.GamePlayer;
import com.zn.agentarena.modules.replay.dto.response.HighlightClipResponse;
import com.zn.agentarena.modules.replay.dto.response.MatchCommentaryResponse;
import com.zn.agentarena.modules.replay.dto.response.MatchProjectionResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 将公开事件转换为稳定、可分享的结构化赛事内容。 */
@Component
public class MatchNarrativeProjector {
    private final WerewolfSituationProjector werewolfSituationProjector = new WerewolfSituationProjector();

    public MatchProjectionResponse project(Long gameId, GameMode mode, List<GameEvent> events,
                                           List<GamePlayer> players) {
        Map<Long, GamePlayer> playerById = players.stream()
                .collect(Collectors.toMap(GamePlayer::getId, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));
        List<MatchCommentaryResponse> commentary = new ArrayList<>();
        List<HighlightClipResponse> highlights = new ArrayList<>();
        Map<Long, Integer> lastHealth = new LinkedHashMap<>();
        Map<Long, Integer> eliminationStreak = new LinkedHashMap<>();
        WerewolfNarrativeState werewolfNarrative = new WerewolfNarrativeState();
        GameEvent pendingDecision = null;

        for (int index = 0; index < events.size(); index++) {
            GameEvent event = events.get(index);
            if ("AI_DECISION".equals(event.getEventType())) {
                pendingDecision = event;
                continue;
            }
            MatchCommentaryResponse item = commentaryFor(gameId, mode, event, pendingDecision,
                    playerById, lastHealth, werewolfNarrative);
            pendingDecision = "PHASE_CHANGED".equals(event.getEventType()) ? pendingDecision : null;

            HighlightClipResponse highlight = highlightFor(gameId, mode, events, index, event,
                    playerById, eliminationStreak, lastHealth);
            if (highlight != null) {
                highlights.add(highlight);
                if (item != null) item.setHighlightClipId(highlight.getClipId());
            }
            if (item != null) commentary.add(item);
            rememberHealth(event, lastHealth);
        }

        long throughSequence = events.isEmpty() ? 0L
                : Objects.requireNonNullElse(events.get(events.size() - 1).getSequenceNo(), 0L);
        return MatchProjectionResponse.builder().schemaVersion(2).gameId(gameId).mode(mode)
                .generatedThroughSequence(throughSequence)
                .commentary(commentary).highlights(highlights)
                .werewolfSituation(mode == GameMode.WEREWOLF
                        ? werewolfSituationProjector.project(events, players) : null)
                .build();
    }

    private MatchCommentaryResponse commentaryFor(Long gameId, GameMode mode, GameEvent event,
                                                    GameEvent decision, Map<Long, GamePlayer> players,
                                                    Map<Long, Integer> lastHealth,
                                                    WerewolfNarrativeState werewolfNarrative) {
        Map<String, Object> payload = publicPayload(event);
        String type = event.getEventType();
        Long actorId = publicActorId(type, event.getActorPlayerId());
        Long targetId = longValue(payload.get("targetPlayerId"));
        if (targetId == null) targetId = longValue(payload.get("playerId"));
        String actor = playerName(players, actorId, "系统");
        String target = playerName(players, targetId, "目标");
        String title;
        String narration;
        String category = "ACTION";
        int importance = 1;

        switch (type) {
            case "PLAYER_SPOKE" -> {
                StanceNarrative stance = updateStanceNarrative(actorId, actor, payload, players, werewolfNarrative);
                title = stance.title();
                narration = stance.narration();
            }
            case "PLAYER_QUESTIONED" -> {
                title = actor + " 质询 " + target;
                narration = stringValue(payload.get("publicSpeech"), "要求对方解释公开立场。");
                category = "QUESTION";
                importance = 2;
            }
            case "PLAYER_RESPONDED" -> {
                title = actor + " 回应 " + target;
                StanceNarrative stance = updateStanceNarrative(actorId, actor, payload, players, werewolfNarrative);
                narration = stance.narration();
                category = "RESPONSE";
                importance = 2;
            }
            case "PLAYER_FINAL_STATEMENT" -> {
                title = actor + " 最终陈述";
                StanceNarrative stance = updateStanceNarrative(actorId, actor, payload, players, werewolfNarrative);
                narration = stance.narration();
                category = "FINAL_STATEMENT";
            }
            case "CONTROVERSY_IDENTIFIED" -> {
                List<?> controversial = payload.get("controversialPlayers") instanceof List<?> list ? list : List.of();
                Map<?, ?> first = controversial.isEmpty() || !(controversial.get(0) instanceof Map<?, ?> item)
                        ? Map.of() : item;
                Integer seatNo = intValueNullable(first.get("seatNo"));
                String displayName = stringValue(first.get("displayName"), null);
                String focus = seatNo == null ? "争议焦点" : seatNo + "号" + (displayName == null ? "" : " " + displayName);
                title = controversial.isEmpty() ? "本轮跳过定向质询" : focus + " 成为首要争议玩家";
                narration = controversial.isEmpty() ? "公开怀疑与投票意向不足以形成有效质询。"
                        : "系统仅依据白天公开关系识别出 " + controversial.size() + " 名争议玩家。";
                category = "CONTROVERSY";
                importance = 3;
            }
            case "PLAYER_VOTED" -> {
                title = actor + " 投票给 " + target;
                narration = updateVoteNarrative(event, actorId, targetId, target, players, werewolfNarrative);
            }
            case "DAWN_RESULT" -> {
                title = booleanValue(payload.get("peaceful")) ? "平安夜" : "天亮公布结果";
                List<Long> eliminated = longList(payload.get("eliminatedIds"));
                narration = eliminated.isEmpty() ? "昨夜无人出局，公开讨论继续。"
                        : "昨夜 " + joinPlayerNames(players, eliminated) + " 出局。";
                category = "ROUND_RESULT";
                importance = eliminated.isEmpty() ? 2 : 3;
            }
            case "PLAYER_ELIMINATED", "BATTLE_PLAYER_ELIMINATED" -> {
                title = target + " 被淘汰";
                narration = eliminationNarration(payload, actor, target);
                category = "ELIMINATION";
                importance = 4;
            }
            case "VOTE_RESULT" -> {
                title = "本轮票型完成统计";
                narration = voteNarration(payload, players);
                category = "VOTE_RESULT";
                importance = 3;
            }
            case "VOTE_TIED" -> {
                title = "放逐投票出现平票";
                narration = joinPlayerNames(players, longList(payload.get("candidateIds"))) + " 票数相同，本轮无人被放逐。";
                category = "VOTE_RESULT";
                importance = 3;
            }
            case "PHASE_CHANGED" -> {
                String phase = stringValue(payload.get("phase"), event.getPhase());
                title = phaseTitle(phase);
                narration = phaseNarration(phase);
                category = "PHASE";
            }
            case "GAME_FINISHED", "BATTLE_FINISHED" -> {
                title = "对局胜负已定";
                narration = finishNarration(payload, players, mode);
                category = "FINISH";
                importance = 5;
            }
            case "BATTLE_ACTION_EXECUTED" -> {
                title = actor + " · " + battleActionLabel(stringValue(payload.get("action"), "SKIP"));
                narration = battleActionNarration(payload, actor, target, lastHealth.get(actorId));
                category = "BATTLE_ACTION";
                importance = booleanValue(payload.get("critical")) || intValue(payload.get("damage")) >= 45 ? 4 : 1;
            }
            case "BATTLE_ZONE_UPDATED" -> {
                title = "安全区再次收缩";
                narration = "毒圈进入第 " + intValue(payload.get("ringStage")) + " 阶段，安全区域更新为 "
                        + payload.getOrDefault("safeRegions", List.of()) + "。";
                category = "ZONE";
                importance = 3;
            }
            case "BATTLE_SUPPLY_DROP" -> {
                title = "空投抵达 " + stringValue(payload.get("region"), "未知区域");
                narration = "高级武器与补给已投放，区域价值发生变化。";
                category = "SUPPLY_DROP";
                importance = 3;
            }
            case "BATTLE_ZONE_DAMAGE" -> {
                title = actor + " 承受毒圈伤害";
                narration = actor + " 在圈外损失 " + intValue(payload.get("damage")) + " 点生命，剩余 "
                        + intValue(payload.get("health")) + "。";
                category = "ZONE_DAMAGE";
                importance = intValue(payload.get("health")) <= 20 ? 3 : 2;
            }
            default -> {
                return null;
            }
        }

        Map<String, Object> metrics = metrics(payload);
        String decisionSummary = decision == null ? null
                : stringValue(publicPayload(decision).get("reasonSummary"),
                stringValue(publicPayload(decision).get("summary"), null));
        if (decision != null && "MODEL_FALLBACK".equals(publicPayload(decision).get("source"))) {
            narration = narration + " 本回合由系统策略继续。";
        }
        List<String> eventTypes = decision == null ? List.of(type) : List.of(decision.getEventType(), type);
        long start = decision == null ? event.getSequenceNo() : decision.getSequenceNo();
        return MatchCommentaryResponse.builder()
                .itemId("c-g" + gameId + "-s" + event.getSequenceNo())
                .sequenceNo(start).endSequenceNo(event.getSequenceNo())
                .roundNo(event.getRoundNo()).phase(event.getPhase()).category(category)
                .title(title).narration(narration).decisionSummary(decisionSummary).importance(importance)
                .actorPlayerId(actorId).actorName(actorId == null ? null : actor)
                .targetPlayerId(targetId).targetName(targetId == null ? null : target)
                .eventTypes(eventTypes).metrics(metrics).build();
    }

    private HighlightClipResponse highlightFor(Long gameId, GameMode mode, List<GameEvent> events, int index,
                                                GameEvent event, Map<Long, GamePlayer> players,
                                                Map<Long, Integer> eliminationStreak,
                                                Map<Long, Integer> lastHealth) {
        Map<String, Object> payload = publicPayload(event);
        String type = event.getEventType();
        String category;
        String title;
        String narration;
        int importance;
        Long actorId = publicActorId(type, event.getActorPlayerId());
        Long targetId = longValue(payload.get("targetPlayerId"));
        if (targetId == null) targetId = longValue(payload.get("playerId"));
        String actor = playerName(players, actorId, "系统");
        String target = playerName(players, targetId, "目标");

        switch (type) {
            case "DAWN_RESULT" -> {
                List<Long> eliminated = longList(payload.get("eliminatedIds"));
                category = eliminated.isEmpty() ? "PEACEFUL_NIGHT" : "NIGHT_TURNING_POINT";
                title = eliminated.isEmpty() ? "无人倒下的夜晚" : "黎明揭晓夜间结果";
                narration = eliminated.isEmpty() ? "各方夜间行动相互抵消，平安夜改变了白天的推理起点。"
                        : joinPlayerNames(players, eliminated) + " 在黎明时出局，阵营人数随之改变。";
                importance = eliminated.isEmpty() ? 3 : 4;
            }
            case "VOTE_TIED" -> {
                category = "VOTE_TIE";
                title = "关键平票";
                narration = joinPlayerNames(players, longList(payload.get("candidateIds"))) + " 陷入平票，本轮无人被放逐。";
                importance = 4;
            }
            case "PLAYER_ELIMINATED" -> {
                if ("NIGHT".equals(payload.get("reason"))) return null;
                category = "VOTE_ELIMINATION";
                title = target + " 被投票放逐";
                narration = target + " 成为本轮公开票型的最终出局者。";
                importance = 4;
            }
            case "GAME_FINISHED" -> {
                category = "MATCH_POINT";
                title = "阵营胜负揭晓";
                narration = finishNarration(payload, players, mode);
                importance = 5;
            }
            case "BATTLE_PLAYER_ELIMINATED" -> {
                boolean playerKill = actorId != null
                        && !actorId.equals(targetId)
                        && "ATTACK".equals(stringValue(payload.get("reason"), ""));
                int streak = playerKill ? eliminationStreak.merge(actorId, 1, Integer::sum) : 0;
                category = streak >= 2 ? "KILL_STREAK" : "ELIMINATION";
                title = streak >= 2 ? actor + " 完成连续淘汰" : target + " 被淘汰";
                narration = eliminationNarration(payload, actor, target)
                        + (streak >= 2 ? " " + actor + " 已连续拿下 " + streak + " 次淘汰。" : "");
                importance = streak >= 2 ? 5 : 4;
            }
            case "BATTLE_ACTION_EXECUTED" -> {
                boolean critical = booleanValue(payload.get("critical"));
                int damage = intValue(payload.get("damage"));
                boolean supplyDrop = booleanValue(payload.get("supplyDrop"));
                Integer previousHealth = lastHealth.get(actorId);
                boolean escape = previousHealth != null && previousHealth <= 25
                        && List.of("MOVE", "HIDE", "HEAL").contains(stringValue(payload.get("action"), ""));
                if (critical || damage >= 45) {
                    category = critical ? "CRITICAL_HIT" : "HEAVY_HIT";
                    title = critical ? actor + " 打出致命暴击" : actor + " 完成重击";
                    narration = actor + " 对 " + target + " 造成 " + damage + " 点伤害，战局瞬间倾斜。";
                    importance = critical ? 5 : 4;
                } else if (supplyDrop && "SEARCH".equals(payload.get("action"))) {
                    category = "SUPPLY_DROP_CAPTURE";
                    title = actor + " 抢到空投物资";
                    narration = actor + " 在空投区获得 " + stringValue(payload.get("lootName"), "高级补给") + "。";
                    importance = 4;
                } else if (escape) {
                    category = "LOW_HEALTH_ESCAPE";
                    title = actor + " 残血求生";
                    narration = actor + " 在低生命状态下执行 " + battleActionLabel(stringValue(payload.get("action"), ""))
                            + "，暂时延续了生存机会。";
                    importance = 4;
                } else {
                    return null;
                }
            }
            case "BATTLE_ZONE_UPDATED" -> {
                category = "ZONE_SHIFT";
                title = "安全区收缩改变战局";
                narration = "安全区域更新为 " + payload.getOrDefault("safeRegions", List.of()) + "，玩家被进一步推向遭遇。";
                importance = 3;
            }
            case "BATTLE_SUPPLY_DROP" -> {
                category = "SUPPLY_DROP";
                title = "空投进入战场";
                narration = "空投落在 " + stringValue(payload.get("region"), "未知区域") + "，新的争夺焦点出现。";
                importance = 3;
            }
            case "BATTLE_FINISHED" -> {
                category = "MATCH_POINT";
                title = stringValue(payload.get("winnerName"), "最终幸存者") + " 赢得大逃杀";
                narration = finishNarration(payload, players, mode);
                importance = 5;
            }
            default -> {
                return null;
            }
        }

        long start = contextualStart(events, index, type);
        List<String> eventTypes = events.stream()
                .filter(item -> item.getSequenceNo() >= start && item.getSequenceNo() <= event.getSequenceNo())
                .map(GameEvent::getEventType).toList();
        String clipId = "g" + gameId + "-s" + start + "-" + category.toLowerCase().replace('_', '-');
        return HighlightClipResponse.builder().clipId(clipId).gameId(gameId).category(category)
                .title(title).narration(narration).importance(importance)
                .startSequenceNo(start).endSequenceNo(event.getSequenceNo())
                .roundNo(event.getRoundNo()).phase(event.getPhase())
                .actorPlayerId(actorId).actorName(actorId == null ? null : actor)
                .targetPlayerId(targetId).targetName(targetId == null ? null : target)
                .eventTypes(eventTypes).metrics(metrics(payload)).build();
    }

    private long contextualStart(List<GameEvent> events, int index, String type) {
        long start = events.get(index).getSequenceNo();
        int lookback = switch (type) {
            case "PLAYER_ELIMINATED", "BATTLE_PLAYER_ELIMINATED", "GAME_FINISHED", "BATTLE_FINISHED" -> 3;
            default -> 1;
        };
        for (int cursor = index - 1; cursor >= 0 && index - cursor <= lookback; cursor--) {
            GameEvent previous = events.get(cursor);
            if (previous.getRoundNo() != null && events.get(index).getRoundNo() != null
                    && !previous.getRoundNo().equals(events.get(index).getRoundNo())) break;
            start = previous.getSequenceNo();
        }
        return start;
    }

    private void rememberHealth(GameEvent event, Map<Long, Integer> lastHealth) {
        if (!"BATTLE_ACTION_EXECUTED".equals(event.getEventType())
                && !"BATTLE_ZONE_DAMAGE".equals(event.getEventType())) return;
        Long playerId = event.getActorPlayerId();
        Object health = publicPayload(event).get("health");
        if (playerId != null && health instanceof Number number) lastHealth.put(playerId, number.intValue());
    }

    private Map<String, Object> publicPayload(GameEvent event) {
        return event.getPublicPayload() == null ? Map.of() : event.getPublicPayload();
    }

    private Long publicActorId(String eventType, Long actorId) {
        return switch (eventType) {
            case "WOLF_TARGET_SELECTED", "GUARD_PROTECTED", "SEER_CHECKED", "WITCH_ACTION_COMPLETED" -> null;
            default -> actorId;
        };
    }

    private String playerName(Map<Long, GamePlayer> players, Long playerId, String fallback) {
        GamePlayer player = playerId == null ? null : players.get(playerId);
        return player == null || player.getDisplayName() == null ? fallback : player.getDisplayName();
    }

    private String joinPlayerNames(Map<Long, GamePlayer> players, List<Long> playerIds) {
        if (playerIds.isEmpty()) return "无人";
        return playerIds.stream().map(id -> playerName(players, id, "玩家 #" + id))
                .collect(Collectors.joining("、"));
    }

    private String eliminationNarration(Map<String, Object> payload, String actor, String target) {
        String reason = stringValue(payload.get("reason"), "UNKNOWN");
        return switch (reason) {
            case "ATTACK" -> actor + " 完成攻击并淘汰了 " + target + "。";
            case "ZONE" -> target + " 未能及时进入安全区，被毒圈淘汰。";
            case "SURVIVAL" -> target + " 因补给耗尽退出战场。";
            case "VOTE" -> target + " 被公开投票放逐。";
            default -> target + " 已离开本场对局。";
        };
    }

    private String voteNarration(Map<String, Object> payload, Map<Long, GamePlayer> players) {
        List<Long> candidates = longList(payload.get("candidateIds"));
        if (candidates.isEmpty()) return "票型已完成统计。";
        return "最高票集中在 " + joinPlayerNames(players, candidates) + "。";
    }

    private String finishNarration(Map<String, Object> payload, Map<Long, GamePlayer> players, GameMode mode) {
        if (mode == GameMode.BATTLE_ROYALE) {
            Long winnerId = longValue(payload.get("winnerPlayerId"));
            String winner = stringValue(payload.get("winnerName"), playerName(players, winnerId, "最终幸存者"));
            return winner + " 成为最后的幸存者，最终评分 " + intValue(payload.get("score")) + "。";
        }
        String winner = stringValue(payload.get("winner"), "UNKNOWN");
        return "GOOD".equals(winner) ? "好人阵营清除了全部狼人，赢得本局。"
                : "WOLF".equals(winner) ? "狼人阵营人数形成优势，赢得本局。" : "本局胜负已经结算。";
    }

    private String battleActionNarration(Map<String, Object> payload, String actor, String target,
                                         Integer previousHealth) {
        String action = stringValue(payload.get("action"), "SKIP");
        return switch (action) {
            case "ATTACK" -> actor + " 攻击 " + target + "，造成 " + intValue(payload.get("damage"))
                    + " 点伤害，对方剩余生命 " + intValue(payload.get("targetHealth")) + "。";
            case "MOVE" -> actor + " 移动至 " + stringValue(payload.get("region"), "新区域") + "。";
            case "SEARCH" -> actor + " 搜索并获得 " + stringValue(payload.get("lootName"), "补给") + "。";
            case "HEAL" -> actor + " 使用医疗包恢复生命" + (previousHealth == null ? "。" : "，摆脱了低生命压力。 ");
            case "HIDE" -> actor + " 进入隐蔽状态，暂时隐藏所在区域。";
            case "GUARD" -> actor + " 加固防具，当前护甲 " + intValue(payload.get("armor")) + "。";
            case "TRADE" -> actor + " 与 " + target + " 完成物资交易。";
            case "REST" -> actor + " 原地休整，恢复生存状态。";
            default -> actor + " 保持观察，没有执行主动行动。";
        };
    }

    private String battleActionLabel(String action) {
        return switch (action) {
            case "ATTACK" -> "进攻";
            case "MOVE" -> "转移";
            case "SEARCH" -> "搜索";
            case "HEAL" -> "治疗";
            case "HIDE" -> "隐蔽";
            case "GUARD" -> "防御";
            case "TRADE" -> "交易";
            case "REST" -> "休整";
            default -> "观察";
        };
    }

    private String phaseTitle(String phase) {
        return switch (phase) {
            case "NIGHT_WOLF" -> "夜幕降临";
            case "NIGHT_GUARD" -> "守卫开始行动";
            case "NIGHT_SEER" -> "预言家开始行动";
            case "NIGHT_WITCH" -> "女巫开始行动";
            case "DAY_DISCUSSION" -> "天亮，公开讨论开始";
            case "DAY_QUESTION" -> "争议质询开始";
            case "DAY_RESPONSE" -> "被质询玩家回应";
            case "DAY_FINAL_STATEMENT" -> "投票前最终陈述";
            case "DAY_VOTE" -> "进入放逐投票";
            case "FINISHED" -> "对局结束";
            default -> "阶段切换";
        };
    }

    private String phaseNarration(String phase) {
        return switch (phase) {
            case "NIGHT_WOLF", "NIGHT_GUARD", "NIGHT_SEER", "NIGHT_WITCH" -> "夜间角色依次提交秘密行动，公开视角保持隐藏。";
            case "DAY_DISCUSSION" -> "所有存活玩家将依次公开发言。";
            case "DAY_QUESTION" -> "质询者将针对公开矛盾提出一个简短问题。";
            case "DAY_RESPONSE" -> "争议玩家将直接回应质询并更新公开立场。";
            case "DAY_FINAL_STATEMENT" -> "所有存活玩家简短确认最终立场与投票意向。";
            case "DAY_VOTE" -> "发言结束，存活玩家依次投出放逐票。";
            default -> "对局进入新的规则阶段。";
        };
    }

    private Map<String, Object> metrics(Map<String, Object> payload) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        for (String key : List.of("damage", "targetHealth", "health", "kills", "score", "ringStage",
                "region", "safeRegions", "lootType", "lootName", "candidateIds", "eliminatedIds")) {
            if (payload.containsKey(key)) metrics.put(key, payload.get(key));
        }
        return metrics;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? null : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private String stringValue(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private List<Long> longList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(this::longValue).filter(Objects::nonNull).toList();
    }

    private List<Integer> integerList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(this::intValueNullable).filter(Objects::nonNull).toList();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }

    private StanceNarrative updateStanceNarrative(Long actorId, String actor,
                                                   Map<String, Object> payload,
                                                   Map<Long, GamePlayer> players,
                                                   WerewolfNarrativeState state) {
        List<Integer> suspects = integerList(payload.get("suspects")).stream().distinct().toList();
        List<String> claims = stringList(payload.get("claims")).stream().distinct().toList();
        Integer voteIntent = intValueNullable(payload.get("voteIntentSeatNo"));
        Integer payloadActorSeat = intValueNullable(payload.get("actorSeatNo"));
        final Integer actorSeat = payloadActorSeat == null && players.get(actorId) != null
                ? players.get(actorId).getSeatNo() : payloadActorSeat;

        List<Integer> previousSuspects = state.suspectsByActor.getOrDefault(actorId, List.of());
        previousSuspects.forEach(seat -> state.suspicionCounts.computeIfPresent(seat,
                (ignored, count) -> count <= 1 ? null : count - 1));
        suspects.forEach(seat -> state.suspicionCounts.merge(seat, 1, Integer::sum));

        Integer previousVoteIntent = state.voteIntentByActor.put(actorId, voteIntent);
        List<String> previousClaims = state.claimsByActor.put(actorId, claims);
        state.suspectsByActor.put(actorId, suspects);

        boolean voteChanged = previousVoteIntent != null && voteIntent != null
                && !previousVoteIntent.equals(voteIntent);
        boolean claimChanged = previousClaims != null && !previousClaims.isEmpty() && !claims.isEmpty()
                && !new java.util.LinkedHashSet<>(previousClaims).equals(new java.util.LinkedHashSet<>(claims));
        Integer mutualSeat = actorSeat == null ? null : suspects.stream().filter(seat -> {
            GamePlayer suspectPlayer = playerBySeat(players, seat);
            return suspectPlayer != null && state.suspectsByActor
                    .getOrDefault(suspectPlayer.getId(), List.of()).contains(actorSeat);
        }).findFirst().orElse(null);
        Map.Entry<Integer, Integer> concentrated = state.suspicionCounts.entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(entry -> -entry.getKey()))
                .filter(entry -> entry.getValue() >= 3).orElse(null);

        String reason = stringValue(payload.get("reasonSummary"), "基于当前公开发言更新了判断。");
        if (voteChanged) {
            return new StanceNarrative(actor + " 改变投票意向",
                    actor + " 从 " + previousVoteIntent + "号转向 " + voteIntent + "号。" + reason);
        }
        if (claimChanged) {
            return new StanceNarrative(actor + " 改变身份声明",
                    "声明由 " + String.join("/", previousClaims) + " 变为 " + String.join("/", claims) + "。" + reason);
        }
        if (mutualSeat != null) {
            String mutualName = seatLabel(players, mutualSeat);
            return new StanceNarrative(actor + " 与 " + mutualName + " 互相怀疑",
                    "双方已把对方列入公开怀疑名单。" + reason);
        }
        if (concentrated != null && suspects.contains(concentrated.getKey())) {
            String focus = seatLabel(players, concentrated.getKey());
            return new StanceNarrative(focus + " 成为当前最受怀疑玩家",
                    "共有 " + concentrated.getValue() + " 名玩家将其列入怀疑名单。" + reason);
        }
        return new StanceNarrative(suspects.isEmpty() ? actor + " 公开陈述"
                : actor + " 怀疑 " + joinSeatNames(players, suspects), reason);
    }

    private String updateVoteNarrative(GameEvent event, Long actorId, Long targetId, String target,
                                       Map<Long, GamePlayer> players, WerewolfNarrativeState state) {
        if (!Objects.equals(state.voteRound, event.getRoundNo())) {
            state.voteRound = event.getRoundNo();
            state.formalVotes.clear();
            state.formalVoteCounts.clear();
            state.voteLeader = null;
            state.lastSoleLeader = null;
        }
        Long previousTarget = state.formalVotes.put(actorId, targetId);
        if (previousTarget != null) state.formalVoteCounts.computeIfPresent(previousTarget,
                (ignored, count) -> count <= 1 ? null : count - 1);
        if (targetId != null) state.formalVoteCounts.merge(targetId, 1, Integer::sum);

        Long previousLeader = state.lastSoleLeader;
        int highest = state.formalVoteCounts.values().stream().max(Integer::compareTo).orElse(0);
        List<Long> leaders = state.formalVoteCounts.entrySet().stream()
                .filter(entry -> entry.getValue() == highest).map(Map.Entry::getKey).toList();
        state.voteLeader = leaders.size() == 1 ? leaders.get(0) : null;
        StringBuilder narration = new StringBuilder(target).append("目前获得 ")
                .append(state.formalVoteCounts.getOrDefault(targetId, 0)).append(" 票。");
        if (state.voteLeader != null && previousLeader != null && !state.voteLeader.equals(previousLeader)) {
            narration.append("领先者由 ").append(playerName(players, previousLeader, "上一候选人"))
                    .append(" 变为 ").append(playerName(players, state.voteLeader, "当前候选人")).append("。");
        } else if (state.voteLeader != null) {
            narration.append(playerName(players, state.voteLeader, "当前候选人")).append(" 暂时领票。");
        } else if (leaders.size() > 1) {
            narration.append(joinPlayerNames(players, leaders)).append(" 同为 ").append(highest).append(" 票。");
        }
        if (state.voteLeader != null) state.lastSoleLeader = state.voteLeader;
        return narration.toString();
    }

    private GamePlayer playerBySeat(Map<Long, GamePlayer> players, Integer seatNo) {
        return players.values().stream().filter(player -> Objects.equals(player.getSeatNo(), seatNo))
                .findFirst().orElse(null);
    }

    private String seatLabel(Map<Long, GamePlayer> players, Integer seatNo) {
        GamePlayer player = playerBySeat(players, seatNo);
        return seatNo + "号" + (player == null || player.getDisplayName() == null ? "" : " " + player.getDisplayName());
    }

    private Integer intValueNullable(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? null : Integer.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String joinSeatNames(Map<Long, GamePlayer> players, List<Integer> seats) {
        return seats.stream().map(seat -> players.values().stream()
                        .filter(player -> Objects.equals(player.getSeatNo(), seat)).findFirst()
                        .map(player -> seat + "号 " + player.getDisplayName()).orElse(seat + "号"))
                .collect(Collectors.joining("、"));
    }

    private record StanceNarrative(String title, String narration) {
    }

    private static final class WerewolfNarrativeState {
        private final Map<Long, Integer> voteIntentByActor = new LinkedHashMap<>();
        private final Map<Long, List<String>> claimsByActor = new LinkedHashMap<>();
        private final Map<Long, List<Integer>> suspectsByActor = new LinkedHashMap<>();
        private final Map<Integer, Integer> suspicionCounts = new LinkedHashMap<>();
        private final Map<Long, Long> formalVotes = new LinkedHashMap<>();
        private final Map<Long, Integer> formalVoteCounts = new LinkedHashMap<>();
        private Integer voteRound;
        private Long voteLeader;
        private Long lastSoleLeader;
    }
}
