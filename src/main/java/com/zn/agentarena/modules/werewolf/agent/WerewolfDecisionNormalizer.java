package com.zn.agentarena.modules.werewolf.agent;

import com.zn.agentarena.modules.werewolf.domain.WerewolfActionType;
import com.zn.agentarena.modules.werewolf.domain.WerewolfGameState;
import com.zn.agentarena.modules.werewolf.domain.WerewolfPlayerState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 模型协议到规则引擎协议的唯一转换边界。
 *
 * <p>新协议优先按座位号解析；旧 {@code targetPlayerId} 先尝试匹配本局真实玩家 ID，
 * 再尝试按座位号解释。任何引用都只能在当前快照玩家集合内解析。</p>
 */
public final class WerewolfDecisionNormalizer {

    public static final int SPEECH_MAX_LENGTH = 160;
    public static final int QUESTION_MAX_LENGTH = 60;
    public static final int RESPONSE_MAX_LENGTH = 120;
    public static final int FINAL_STATEMENT_MAX_LENGTH = 100;
    public static final int REASON_SUMMARY_MAX_LENGTH = 60;
    public static final int MAX_SUSPECTS = 3;
    public static final int MAX_SUPPORTS = 2;
    public static final int MAX_REASON_TAGS = 3;

    public NormalizedDecision normalize(WerewolfGameState state, WerewolfPlayerState actor,
                                        WerewolfAiDecision decision, WerewolfActionType actionType) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(actionType, "actionType");

        TargetResolution target = resolveTarget(state, decision.targetSeatNo(), decision.targetPlayerId());
        String rawSpeech = hasText(decision.publicSpeech()) ? decision.publicSpeech() : decision.publicMessage();
        String publicSpeech = truncate(rawSpeech, speechLimit(actionType));
        String reasonSummary = truncate(decision.reasonSummary(), REASON_SUMMARY_MAX_LENGTH);
        List<Integer> suspects = sanitizeSeatList(state, actor, decision.suspects(), MAX_SUSPECTS);
        List<Integer> supports = sanitizeSeatList(state, actor, decision.supports(), MAX_SUPPORTS);
        Integer voteIntent = sanitizeSingleSeat(state, actor, decision.voteIntentSeatNo());
        List<String> claims = sanitizeClaims(decision.claims());
        List<String> reasonTags = sanitizeReasonTags(decision.reasonTags());
        double confidence = normalizeConfidence(decision.confidence());

        return new NormalizedDecision(actionType, target.playerId(), target.seatNo(), publicSpeech,
                claims, suspects, supports, voteIntent, reasonTags, reasonSummary, confidence,
                target.protocolNormalized(), target.normalizationKind(), decision.targetPlayerId());
    }

    private TargetResolution resolveTarget(WerewolfGameState state, Integer targetSeatNo, Long legacyReference) {
        if (targetSeatNo != null) {
            WerewolfPlayerState player = findBySeat(state, targetSeatNo);
            return new TargetResolution(player == null ? null : player.getPlayerId(), targetSeatNo,
                    false, null);
        }
        if (legacyReference == null) {
            return new TargetResolution(null, null, false, null);
        }

        WerewolfPlayerState byId = state.findPlayer(legacyReference);
        if (byId != null) {
            return new TargetResolution(byId.getPlayerId(), byId.getSeatNo(), true, "LEGACY_PLAYER_ID");
        }
        WerewolfPlayerState bySeat = legacyReference >= Integer.MIN_VALUE && legacyReference <= Integer.MAX_VALUE
                ? findBySeat(state, legacyReference.intValue()) : null;
        if (bySeat != null) {
            return new TargetResolution(bySeat.getPlayerId(), bySeat.getSeatNo(), true, "LEGACY_SEAT_NO");
        }
        return new TargetResolution(null, null, false, null);
    }

    private List<Integer> sanitizeSeatList(WerewolfGameState state, WerewolfPlayerState actor,
                                           List<Integer> rawSeats, int maxSize) {
        if (rawSeats == null || rawSeats.isEmpty()) return List.of();
        Set<Integer> result = new LinkedHashSet<>();
        for (Integer seatNo : rawSeats) {
            Integer valid = sanitizeSingleSeat(state, actor, seatNo);
            if (valid != null) result.add(valid);
            if (result.size() >= maxSize) break;
        }
        return List.copyOf(result);
    }

    private Integer sanitizeSingleSeat(WerewolfGameState state, WerewolfPlayerState actor, Integer seatNo) {
        if (seatNo == null) return null;
        WerewolfPlayerState target = findBySeat(state, seatNo);
        if (target == null || !target.isAlive() || target.getPlayerId().equals(actor.getPlayerId())) return null;
        return target.getSeatNo();
    }

    private WerewolfPlayerState findBySeat(WerewolfGameState state, Integer seatNo) {
        if (seatNo == null) return null;
        return state.getPlayers().stream()
                .filter(player -> seatNo.equals(player.getSeatNo()))
                .findFirst().orElse(null);
    }

    private List<String> sanitizeClaims(List<String> rawClaims) {
        if (rawClaims == null) return List.of();
        Set<String> allowed = Set.of("WEREWOLF", "SEER", "WITCH", "GUARD", "VILLAGER");
        return rawClaims.stream().filter(Objects::nonNull).map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(allowed::contains).distinct().limit(2).toList();
    }

    private List<String> sanitizeReasonTags(List<String> rawTags) {
        if (rawTags == null) return List.of();
        List<String> result = new ArrayList<>();
        for (String rawTag : rawTags) {
            if (!hasText(rawTag)) continue;
            String normalized = rawTag.trim().toUpperCase(Locale.ROOT)
                    .replace('-', '_').replace(' ', '_');
            normalized = normalized.replaceAll("[^A-Z0-9_]", "");
            if (!normalized.isBlank() && !result.contains(normalized)) result.add(normalized);
            if (result.size() >= MAX_REASON_TAGS) break;
        }
        return List.copyOf(result);
    }

    private int speechLimit(WerewolfActionType actionType) {
        return switch (actionType.name()) {
            case "QUESTION" -> QUESTION_MAX_LENGTH;
            case "RESPOND" -> RESPONSE_MAX_LENGTH;
            case "FINAL_STATEMENT" -> FINAL_STATEMENT_MAX_LENGTH;
            default -> SPEECH_MAX_LENGTH;
        };
    }

    private double normalizeConfidence(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) return 0D;
        double normalized = value > 1D ? value / 100D : value;
        return Math.max(0D, Math.min(1D, normalized));
    }

    private String truncate(String value, int maxCodePoints) {
        if (!hasText(value)) return "";
        String normalized = value.trim().replace("\u0000", "");
        int count = normalized.codePointCount(0, normalized.length());
        if (count <= maxCodePoints) return normalized;
        int end = normalized.offsetByCodePoints(0, Math.max(1, maxCodePoints - 1));
        return normalized.substring(0, end).stripTrailing() + "…";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record TargetResolution(Long playerId, Integer seatNo, boolean protocolNormalized,
                                    String normalizationKind) {
    }

    public record NormalizedDecision(
            WerewolfActionType actionType,
            Long targetPlayerId,
            Integer targetSeatNo,
            String publicSpeech,
            List<String> claims,
            List<Integer> suspects,
            List<Integer> supports,
            Integer voteIntentSeatNo,
            List<String> reasonTags,
            String reasonSummary,
            double confidence,
            boolean protocolNormalized,
            String normalizationKind,
            Long legacyTargetReference
    ) {
    }
}
