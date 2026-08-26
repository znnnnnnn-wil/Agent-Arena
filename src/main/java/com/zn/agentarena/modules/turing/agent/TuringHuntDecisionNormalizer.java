package com.zn.agentarena.modules.turing.agent;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.modules.turing.domain.TuringHuntAction;
import com.zn.agentarena.modules.turing.domain.TuringHuntActionType;
import com.zn.agentarena.modules.turing.domain.TuringHuntGameState;
import com.zn.agentarena.modules.turing.domain.TuringHuntPlayerState;
import com.zn.agentarena.modules.turing.engine.TuringHuntGameEngine;
import com.zn.agentarena.modules.turing.text.TuringTextPolicy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 模型 seatNo 协议到内部规则动作的唯一转换边界。 */
public final class TuringHuntDecisionNormalizer {

    private final TuringTextPolicy textPolicy;

    public TuringHuntDecisionNormalizer() {
        this(TuringTextPolicy.INSTANCE);
    }

    TuringHuntDecisionNormalizer(TuringTextPolicy textPolicy) {
        this.textPolicy = Objects.requireNonNull(textPolicy);
    }

    public TuringHuntAction normalize(TuringHuntGameState state, TuringHuntPlayerState actor,
                                      TuringHuntActionType expected,
                                      TuringHuntAiDecision decision) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(actor, "actor");
        if (decision == null || decision.action() == null) {
            throw invalid("模型未返回行动类型");
        }
        TuringHuntActionType actual = parseAction(decision.action());
        if (actual != expected) {
            throw invalid("模型行动与当前阶段不匹配");
        }

        return switch (expected) {
            case ANSWER -> normalizeAnswer(actor, decision);
            case SUSPECT -> normalizeSuspicion(state, actor, decision);
            case QUESTION -> normalizeQuestion(state, actor, decision);
            case RESPOND -> normalizeResponse(state, actor, decision);
            case DEFEND -> normalizeDefense(state, actor, decision);
            case VOTE -> normalizeVote(state, actor, decision);
            case ADVANCE -> throw invalid("模型不能请求阶段推进");
        };
    }

    private TuringHuntAction normalizeAnswer(TuringHuntPlayerState actor, TuringHuntAiDecision decision) {
        String answer = modelText(TuringHuntActionType.ANSWER, decision.answer());
        return action(actor, TuringHuntActionType.ANSWER, answer, null, List.of(), List.of(),
                textPolicy.normalize(decision.reasonSummary()),
                null, null, null, null, null);
    }

    private TuringHuntAction normalizeSuspicion(TuringHuntGameState state, TuringHuntPlayerState actor,
                                                TuringHuntAiDecision decision) {
        Long primary = resolveOtherAlive(state, actor, decision.primarySuspectSeatNo(), true);
        List<Long> suspects = resolveSeatList(state, actor, decision.suspectSeatNos(),
                TuringHuntGameEngine.MAX_SUSPECTS);
        List<String> tags = normalizeTags(decision.reasonTags());
        return action(actor, TuringHuntActionType.SUSPECT, null, primary, suspects, tags,
                modelText(TuringHuntActionType.SUSPECT, decision.reasonSummary()),
                null, null, null, null, null);
    }

    private TuringHuntAction normalizeQuestion(TuringHuntGameState state, TuringHuntPlayerState actor,
                                               TuringHuntAiDecision decision) {
        Long target = resolveOtherAlive(state, actor, decision.targetSeatNo(), true);
        String question = modelText(TuringHuntActionType.QUESTION, decision.question());
        return action(actor, TuringHuntActionType.QUESTION, null, null, List.of(), List.of(),
                "", target, question, null, null, null);
    }

    private TuringHuntAction normalizeResponse(TuringHuntGameState state, TuringHuntPlayerState actor,
                                               TuringHuntAiDecision decision) {
        Long target = resolveOtherAlive(state, actor, decision.targetSeatNo(), true);
        String response = modelText(TuringHuntActionType.RESPOND, decision.response());
        return action(actor, TuringHuntActionType.RESPOND, null, null, List.of(), List.of(),
                textPolicy.normalize(decision.reasonSummary()),
                target, null, response, null, null);
    }

    private TuringHuntAction normalizeDefense(TuringHuntGameState state, TuringHuntPlayerState actor,
                                              TuringHuntAiDecision decision) {
        Long counter = decision.counterSuspectSeatNo() == null ? null
                : resolveOtherAlive(state, actor, decision.counterSuspectSeatNo(), true);
        String defense = modelText(TuringHuntActionType.DEFEND, decision.defense());
        return action(actor, TuringHuntActionType.DEFEND, null, null, List.of(), List.of(),
                textPolicy.normalize(decision.reasonSummary()),
                null, null, null, defense, counter);
    }

    private TuringHuntAction normalizeVote(TuringHuntGameState state, TuringHuntPlayerState actor,
                                           TuringHuntAiDecision decision) {
        Long target = resolveOtherAlive(state, actor, decision.targetSeatNo(), true);
        return action(actor, TuringHuntActionType.VOTE, null, null, List.of(), List.of(),
                modelText(TuringHuntActionType.VOTE, decision.reasonSummary()),
                target, null, null, null, null);
    }

    private TuringHuntAction action(TuringHuntPlayerState actor, TuringHuntActionType type,
                                    String answer, Long primary, List<Long> suspects, List<String> tags,
                                    String reason, Long target, String question, String response,
                                    String defense, Long counter) {
        return new TuringHuntAction(actor.getPlayerId(), type, answer, primary, suspects, tags,
                reason, target, question, response, defense, counter);
    }

    private Long resolveOtherAlive(TuringHuntGameState state, TuringHuntPlayerState actor,
                                   Integer seatNo, boolean required) {
        if (seatNo == null) {
            if (required) throw invalid("模型未返回目标座位");
            return null;
        }
        TuringHuntPlayerState target = state.findSeat(seatNo);
        if (target == null || !target.isAlive() || target.getPlayerId().equals(actor.getPlayerId())) {
            throw invalid("模型引用了非法目标座位");
        }
        return target.getPlayerId();
    }

    private List<Long> resolveSeatList(TuringHuntGameState state, TuringHuntPlayerState actor,
                                       List<Integer> seats, int maximum) {
        if (seats == null || seats.isEmpty()) return List.of();
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Integer seat : seats) {
            result.add(resolveOtherAlive(state, actor, seat, true));
            if (result.size() >= maximum) break;
        }
        return List.copyOf(result);
    }

    private List<String> normalizeTags(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(Objects::nonNull).map(value -> value.trim().toUpperCase(Locale.ROOT)
                        .replace('-', '_').replace(' ', '_').replaceAll("[^A-Z0-9_]", ""))
                .filter(value -> !value.isBlank()).distinct()
                .limit(TuringHuntGameEngine.MAX_REASON_TAGS).toList();
    }

    private String modelText(TuringHuntActionType type, String value) {
        String normalized = textPolicy.normalizeModelText(type, value);
        TuringTextPolicy.TextValidation validation = textPolicy.validate(type, normalized);
        if (!validation.valid()) throw invalid(validation.message());
        return normalized;
    }

    private TuringHuntActionType parseAction(String raw) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (TuringHuntActionType type : TuringHuntActionType.values()) {
            if (normalized.equals(type.name())) return type;
        }
        return switch (normalized) {
            case "回答" -> TuringHuntActionType.ANSWER;
            case "怀疑" -> TuringHuntActionType.SUSPECT;
            case "质询", "提问" -> TuringHuntActionType.QUESTION;
            case "回应" -> TuringHuntActionType.RESPOND;
            case "辩护" -> TuringHuntActionType.DEFEND;
            case "投票" -> TuringHuntActionType.VOTE;
            default -> throw invalid("模型返回了未知行动类型");
        };
    }

    private CustomException invalid(String message) {
        return CustomException.create(ResultCodeEnum.MODEL_RESPONSE_INVALID, message);
    }
}
