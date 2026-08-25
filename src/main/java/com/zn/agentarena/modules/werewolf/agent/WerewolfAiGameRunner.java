package com.zn.agentarena.modules.werewolf.agent;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.modules.game.engine.ActionValidation;
import com.zn.agentarena.modules.game.engine.EngineResult;
import com.zn.agentarena.modules.model.domain.ModelFailureInfo;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import com.zn.agentarena.modules.werewolf.domain.*;
import com.zn.agentarena.modules.werewolf.engine.WerewolfGameEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** 狼人杀 AI 自动推进器；没有配置模型时使用确定性本地策略。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WerewolfAiGameRunner {
    private final ModelGateway modelGateway;
    private final WerewolfAgentContextBuilder contextBuilder;
    private final WerewolfGameEngine gameEngine = new WerewolfGameEngine();
    private final WerewolfDecisionNormalizer decisionNormalizer = new WerewolfDecisionNormalizer();

    /** 根据当前状态执行下一位 AI 玩家的行动。 */
    public EngineResult<WerewolfGameState> runNextTurn(WerewolfGameState state,
                                                        Map<Long, ModelRuntimeConfig> runtimeConfigs) {
        return runNextTurn(state, runtimeConfigs, false);
    }

    public EngineResult<WerewolfGameState> runNextTurn(WerewolfGameState state,
                                                        Map<Long, ModelRuntimeConfig> runtimeConfigs,
                                                        boolean modelRateLimited) {
        WerewolfPlayerState actor = findNextActor(state);
        if (actor == null) {
            throw CustomException.create(ResultCodeEnum.ACTION_NOT_ALLOWED, "当前没有可行动玩家");
        }
        ModelRuntimeConfig config = runtimeConfigs.get(actor.getPlayerId());
        if (config == null) {
            WerewolfAction action = createFallbackAction(state, actor);
            if (modelRateLimited) {
                ModelFailureInfo failure = ModelFailureInfo.rateLimited();
                return executeWithDecision(state, actor, action, failure.summary(), 0D,
                        "MODEL_FALLBACK", failure.code(), Map.of());
            }
            return executeWithDecision(state, actor, action,
                    "本地策略根据当前阶段选择了合法行动。", 0.6D, "LOCAL", null, Map.of());
        }
        try {
            RoleCheckedDecision checkedDecision = generateRoleCheckedDecision(state, actor, config);
            WerewolfAiDecision decision = checkedDecision.decision();
            WerewolfDecisionNormalizer.NormalizedDecision normalized = normalizeDecision(state, actor, decision);
            WerewolfAction modelAction = new WerewolfAction(actor.getPlayerId(), normalized.actionType(),
                    normalized.targetPlayerId(), normalized.publicSpeech(), normalized.claims(),
                    normalized.suspects(), normalized.supports(), normalized.voteIntentSeatNo(),
                    normalized.reasonTags(), normalized.reasonSummary(), normalized.confidence());
            ActionValidation validation = gameEngine.validate(state, modelAction);
            boolean valid = validation.valid();
            WerewolfAction finalAction = valid ? modelAction : createFallbackAction(state, actor);
            String summary = valid ? normalized.reasonSummary()
                    : "模型给出的行动已按规则自动校正（" + validation.message() + "），对局已继续。";
            Map<String, Object> diagnostics = identityDiagnostics(checkedDecision);
            diagnostics.putAll(normalizationDiagnostics(normalized));
            if (!valid) diagnostics.putAll(adjustmentDiagnostics(decision, modelAction, validation));
            return executeWithDecision(state, actor, finalAction, summary, normalized.confidence(),
                    valid ? "MODEL" : "MODEL_FALLBACK", valid ? null : "RULE_ADJUSTED", diagnostics);
        } catch (RuntimeException exception) {
            ModelFailureInfo failure = exception instanceof RoleIdentityMismatchException
                    ? new ModelFailureInfo("IDENTITY_MISMATCH",
                    "模型经身份纠正后仍未能确认系统分配的角色，本回合已使用本地身份策略。")
                    : ModelFailureInfo.from(exception);
            log.warn("狼人杀模型决策失败，改用本地策略: gameId={}, playerId={}, code={}, reason={}",
                    state.getGameId(), actor.getPlayerId(), failure.code(), exception.getClass().getSimpleName());
            WerewolfAction action = createFallbackAction(state, actor);
            return executeWithDecision(state, actor, action, failure.summary(), 0D,
                    "MODEL_FALLBACK", failure.code(), Map.of());
        }
    }

    private EngineResult<WerewolfGameState> executeWithDecision(WerewolfGameState state,
                                                                 WerewolfPlayerState actor,
                                                                 WerewolfAction action,
                                                                 String summary,
                                                                 Number confidence,
                                                                 String source,
                                                                 String fallbackCode,
                                                                 Map<String, Object> diagnostics) {
        WerewolfPhase phaseAtDecision = state.getPhase();
        state.setSequenceNo(state.getSequenceNo() + 1);
        Map<String, Object> decisionPayload = new LinkedHashMap<>();
        decisionPayload.put("action", action.type().name());
        decisionPayload.put("targetPlayerId", action.targetPlayerId());
        WerewolfPlayerState target = state.findPlayer(action.targetPlayerId());
        decisionPayload.put("targetSeatNo", target == null ? null : target.getSeatNo());
        decisionPayload.put("summary", summary == null ? "模型未提供决策摘要" : summary);
        decisionPayload.put("confidence", confidence == null ? 0D : confidence);
        decisionPayload.put("claims", action.claims());
        decisionPayload.put("suspects", action.suspects());
        decisionPayload.put("supports", action.supports());
        decisionPayload.put("voteIntentSeatNo", action.voteIntentSeatNo());
        decisionPayload.put("reasonTags", action.reasonTags());
        decisionPayload.put("reasonSummary", action.reasonSummary());
        decisionPayload.put("source", source);
        decisionPayload.put("decisionPhase", phaseAtDecision.name());
        decisionPayload.put("selfRole", actor.getRole().name());
        if (fallbackCode != null) decisionPayload.put("fallbackCode", fallbackCode);
        if (diagnostics != null) decisionPayload.putAll(diagnostics);
        EngineResult<WerewolfGameState> engineResult = gameEngine.execute(state, action);
        ArrayList<com.zn.agentarena.modules.game.engine.EngineEvent> events = new ArrayList<>();
        Map<String, Object> publicDecisionPayload = switch (phaseAtDecision) {
            case DAY_DISCUSSION, DAY_QUESTION, DAY_RESPONSE, DAY_FINAL_STATEMENT, DAY_VOTE -> redactPrivateDecision(decisionPayload);
            default -> fallbackCode == null
                    ? Map.of("completed", true, "source", source)
                    : Map.of("completed", true, "source", source, "fallbackCode", fallbackCode);
        };
        events.add(new com.zn.agentarena.modules.game.engine.EngineEvent("AI_DECISION", actor.getPlayerId(),
                decisionPayload, publicDecisionPayload));
        events.addAll(engineResult.events());
        return new EngineResult<>(state, events);
    }

    private Map<String, Object> redactPrivateDecision(Map<String, Object> decisionPayload) {
        Map<String, Object> publicPayload = new LinkedHashMap<>(decisionPayload);
        publicPayload.remove("summary");
        publicPayload.remove("selfRole");
        publicPayload.remove("legacyTargetReference");
        publicPayload.remove("normalizationKind");
        publicPayload.remove("requestedTargetPlayerId");
        publicPayload.remove("modelTargetReference");
        publicPayload.remove("adjustmentReason");
        publicPayload.put("summaryHidden", true);
        return publicPayload;
    }

    /** 连续执行 AI 行动，直到游戏暂停或达到最大行动次数。 */
    public EngineResult<WerewolfGameState> runUntilPause(WerewolfGameState state,
                                                         Map<Long, ModelRuntimeConfig> runtimeConfigs,
                                                         int maxActions) {
        if (maxActions <= 0) throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "最大行动数必须为正数");
        EngineResult<WerewolfGameState> result = new EngineResult<>(state, java.util.List.of());
        for (int i = 0; i < maxActions && !gameEngine.isFinished(state); i++) {
            result = runNextTurn(state, runtimeConfigs);
        }
        return result;
    }

    private WerewolfPlayerState findNextActor(WerewolfGameState state) {
        return switch (state.getPhase()) {
            case NIGHT_WOLF -> state.getPlayers().stream().filter(p -> p.isAlive() && p.getRole() == WerewolfRole.WEREWOLF).findFirst().orElse(null);
            case NIGHT_GUARD -> state.getPlayers().stream().filter(p -> p.isAlive() && p.getRole() == WerewolfRole.GUARD).findFirst().orElse(null);
            case NIGHT_SEER -> state.getPlayers().stream().filter(p -> p.isAlive() && p.getRole() == WerewolfRole.SEER).findFirst().orElse(null);
            case NIGHT_WITCH -> state.getPlayers().stream().filter(p -> p.isAlive() && p.getRole() == WerewolfRole.WITCH).findFirst().orElse(null);
            case DAY_DISCUSSION -> state.getDiscussionIndex() < state.alivePlayers().size() ? state.alivePlayers().get(state.getDiscussionIndex()) : null;
            case DAY_QUESTION -> interactionActor(state, true);
            case DAY_RESPONSE -> interactionActor(state, false);
            case DAY_FINAL_STATEMENT -> state.getFinalStatementIndex() < state.alivePlayers().size()
                    ? state.alivePlayers().get(state.getFinalStatementIndex()) : null;
            case DAY_VOTE -> state.alivePlayers().stream().filter(p -> !state.getVotes().containsKey(p.getPlayerId())).findFirst().orElse(null);
            case FINISHED -> null;
        };
    }

    private WerewolfAction createFallbackAction(WerewolfGameState state, WerewolfPlayerState actor) {
        Long targetId = state.alivePlayers().stream()
                .filter(p -> !p.getPlayerId().equals(actor.getPlayerId()))
                .filter(p -> state.getPhase() != WerewolfPhase.NIGHT_WOLF || !p.getRole().isWolfCamp())
                .map(WerewolfPlayerState::getPlayerId).findFirst().orElse(null);
        return switch (state.getPhase()) {
            case NIGHT_WOLF -> new WerewolfAction(actor.getPlayerId(), WerewolfActionType.WOLF_KILL, targetId, null);
            case NIGHT_GUARD -> new WerewolfAction(actor.getPlayerId(), WerewolfActionType.GUARD_PROTECT,
                    state.alivePlayers().stream().map(WerewolfPlayerState::getPlayerId)
                            .filter(id -> !id.equals(state.getLastGuardTargetId())).findFirst().orElse(actor.getPlayerId()), null);
            case NIGHT_SEER -> new WerewolfAction(actor.getPlayerId(), WerewolfActionType.SEER_CHECK,
                    state.alivePlayers().stream().map(WerewolfPlayerState::getPlayerId)
                            .filter(id -> !id.equals(actor.getPlayerId()) && !actor.getSeerChecks().containsKey(id))
                            .findFirst().orElse(targetId), null);
            case NIGHT_WITCH -> actor.isAntidoteAvailable() && state.getNightKillTargetId() != null
                    ? new WerewolfAction(actor.getPlayerId(), WerewolfActionType.WITCH_SAVE, null, null)
                    : new WerewolfAction(actor.getPlayerId(), WerewolfActionType.SKIP, null, null);
            case DAY_DISCUSSION -> createFallbackSpeechAction(state, actor, targetId);
            case DAY_QUESTION -> createFallbackInteractionAction(state, actor, WerewolfActionType.QUESTION, true);
            case DAY_RESPONSE -> createFallbackInteractionAction(state, actor, WerewolfActionType.RESPOND, false);
            case DAY_FINAL_STATEMENT -> createFallbackInteractionAction(state, actor,
                    WerewolfActionType.FINAL_STATEMENT, false);
            case DAY_VOTE -> new WerewolfAction(actor.getPlayerId(), WerewolfActionType.VOTE, fallbackVoteTarget(state, actor, targetId), null);
            case FINISHED -> throw CustomException.create(ResultCodeEnum.ACTION_NOT_ALLOWED, "游戏已经结束");
        };
    }

    private String fallbackSpeech(WerewolfGameState state, WerewolfPlayerState actor) {
        if (actor.getRole() == WerewolfRole.SEER && actor.getSeerChecks().containsValue(true)) {
            Long wolfId = actor.getSeerChecks().entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).findFirst().orElse(null);
            WerewolfPlayerState wolf = state.findPlayer(wolfId);
            return wolf == null ? "我掌握了一些查验信息，会结合投票逐步公开。" : "我查验到" + wolf.getSeatNo() + "号属于狼人阵营，建议本轮重点讨论并投票。";
        }
        if (actor.getRole().isWolfCamp()) return "目前公开信息仍不充分，我更关注发言与投票是否前后矛盾，请大家给出明确依据。";
        String latest = state.getPublicHistory().isEmpty() ? "暂无历史发言" : state.getPublicHistory().get(state.getPublicHistory().size() - 1);
        return "我会结合上一条公开记录“" + latest + "”判断，优先排查发言和投票不一致的玩家。";
    }

    private WerewolfAction createFallbackSpeechAction(WerewolfGameState state, WerewolfPlayerState actor,
                                                       Long defaultTarget) {
        Long suspectId = fallbackVoteTarget(state, actor, defaultTarget);
        WerewolfPlayerState suspect = state.findPlayer(suspectId);
        List<Integer> suspects = suspect == null || suspect.getPlayerId().equals(actor.getPlayerId())
                ? List.of() : List.of(suspect.getSeatNo());
        List<String> claims = actor.getRole() == WerewolfRole.SEER && actor.getSeerChecks().containsValue(true)
                ? List.of("SEER")
                : actor.getRole().isWolfCamp() || actor.getRole() == WerewolfRole.VILLAGER
                ? List.of("VILLAGER") : List.of();
        return new WerewolfAction(actor.getPlayerId(), WerewolfActionType.SPEAK, null,
                fallbackSpeech(state, actor), claims, suspects, List.of(),
                suspects.isEmpty() ? null : suspects.get(0), List.of("PUBLIC_EVIDENCE"),
                suspects.isEmpty() ? "公开信息不足，继续观察" : suspects.get(0) + "号需要进一步解释",
                0.55D);
    }

    private WerewolfAction createFallbackInteractionAction(WerewolfGameState state, WerewolfPlayerState actor,
                                                            WerewolfActionType type, boolean question) {
        WerewolfQuestionPair pair = currentQuestionPair(state);
        Long targetId = type == WerewolfActionType.FINAL_STATEMENT || pair == null ? null
                : question ? pair.getRespondentPlayerId() : pair.getQuestionerPlayerId();
        WerewolfPlayerState target = state.findPlayer(targetId);
        WerewolfPublicStance previous = state.getPublicStances() == null ? null
                : state.getPublicStances().get(actor.getPlayerId());
        List<String> claims = previous == null || previous.getClaims() == null ? List.of() : previous.getClaims();
        List<Integer> suspects = previous == null || previous.getSuspects() == null ? List.of() : previous.getSuspects();
        List<Integer> supports = previous == null || previous.getSupports() == null ? List.of() : previous.getSupports();
        Integer voteIntent = previous == null ? null : previous.getVoteIntentSeatNo();
        String speech = switch (type) {
            case QUESTION -> "请" + target.getSeatNo() + "号解释你当前立场与公开投票意向的依据。";
            case RESPOND -> "我的判断来自公开发言和票型变化；如果出现新证据，我会明确更新立场。";
            case FINAL_STATEMENT -> suspects.isEmpty()
                    ? "当前证据仍有限，我会依据公开矛盾完成投票。"
                    : "我目前优先怀疑" + suspects.get(0) + "号，并会按这一公开判断投票。";
            default -> "我会继续依据公开信息判断。";
        };
        return new WerewolfAction(actor.getPlayerId(), type, targetId, speech, claims, suspects,
                supports, voteIntent, List.of(type == WerewolfActionType.QUESTION
                ? "REQUEST_EXPLANATION" : "PUBLIC_EVIDENCE"),
                type == WerewolfActionType.QUESTION ? "要求争议玩家解释公开立场" : "依据公开信息确认当前立场",
                0.5D);
    }

    private WerewolfPlayerState interactionActor(WerewolfGameState state, boolean question) {
        WerewolfQuestionPair pair = currentQuestionPair(state);
        return pair == null ? null : state.findPlayer(question
                ? pair.getQuestionerPlayerId() : pair.getRespondentPlayerId());
    }

    private WerewolfQuestionPair currentQuestionPair(WerewolfGameState state) {
        List<WerewolfQuestionPair> queue = state.getQuestionQueue();
        return queue == null || state.getQuestionIndex() < 0 || state.getQuestionIndex() >= queue.size()
                ? null : queue.get(state.getQuestionIndex());
    }

    private Long fallbackVoteTarget(WerewolfGameState state, WerewolfPlayerState actor, Long defaultTarget) {
        if (actor.getRole() == WerewolfRole.SEER) {
            return actor.getSeerChecks().entrySet().stream().filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey).filter(id -> state.findPlayer(id) != null && state.findPlayer(id).isAlive())
                    .findFirst().orElse(defaultTarget);
        }
        if (actor.getRole().isWolfCamp()) {
            return state.alivePlayers().stream().filter(player -> !player.getRole().isWolfCamp())
                    .map(WerewolfPlayerState::getPlayerId).findFirst().orElse(defaultTarget);
        }
        return defaultTarget;
    }

    private WerewolfDecisionNormalizer.NormalizedDecision normalizeDecision(WerewolfGameState state,
                                                                             WerewolfPlayerState actor,
                                                                             WerewolfAiDecision decision) {
        if (decision == null || decision.action() == null) {
            throw CustomException.create(ResultCodeEnum.MODEL_RESPONSE_INVALID, "模型未返回行动类型");
        }
        WerewolfActionType actionType = normalizeAction(decision.action());
        return decisionNormalizer.normalize(state, actor, decision, actionType);
    }

    private Map<String, Object> normalizationDiagnostics(
            WerewolfDecisionNormalizer.NormalizedDecision normalized) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        if (normalized.protocolNormalized()) {
            diagnostics.put("protocolNormalized", true);
            diagnostics.put("normalizationKind", normalized.normalizationKind());
            diagnostics.put("legacyTargetReference", normalized.legacyTargetReference());
        }
        return diagnostics;
    }

    private Map<String, Object> adjustmentDiagnostics(WerewolfAiDecision decision,
                                                       WerewolfAction modelAction,
                                                       ActionValidation validation) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("requestedAction", decision.action());
        if (decision.targetSeatNo() != null) {
            diagnostics.put("requestedTargetSeatNo", decision.targetSeatNo());
        } else if (decision.targetPlayerId() != null) {
            diagnostics.put("modelTargetReference", decision.targetPlayerId());
        }
        if (modelAction.targetPlayerId() != null) {
            diagnostics.put("requestedTargetPlayerId", modelAction.targetPlayerId());
        }
        diagnostics.put("adjustmentReason", validation.message());
        return diagnostics;
    }

    private RoleCheckedDecision generateRoleCheckedDecision(WerewolfGameState state,
                                                             WerewolfPlayerState actor,
                                                             ModelRuntimeConfig config) {
        WerewolfAiDecision firstDecision = modelGateway.generateStructured(config,
                contextBuilder.build(state, actor), WerewolfAiDecision.class);
        if (matchesActorRole(firstDecision, actor)) {
            return new RoleCheckedDecision(firstDecision, false);
        }

        log.warn("狼人杀模型未正确确认自身身份，执行一次身份纠正: gameId={}, playerId={}",
                state.getGameId(), actor.getPlayerId());
        WerewolfAiDecision correctedDecision = modelGateway.generateStructured(config,
                contextBuilder.buildIdentityCorrection(state, actor), WerewolfAiDecision.class);
        if (!matchesActorRole(correctedDecision, actor)) {
            throw new RoleIdentityMismatchException();
        }
        return new RoleCheckedDecision(correctedDecision, true);
    }

    private boolean matchesActorRole(WerewolfAiDecision decision, WerewolfPlayerState actor) {
        return decision != null && decision.selfRole() != null
                && actor.getRole().name().equalsIgnoreCase(decision.selfRole().trim());
    }

    private Map<String, Object> identityDiagnostics(RoleCheckedDecision checkedDecision) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("identityVerified", true);
        if (checkedDecision.retried()) diagnostics.put("identityRetry", true);
        return diagnostics;
    }

    private record RoleCheckedDecision(WerewolfAiDecision decision, boolean retried) {
    }

    private static final class RoleIdentityMismatchException extends RuntimeException {
    }

    private WerewolfActionType normalizeAction(String rawAction) {
        String normalized = rawAction.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        for (WerewolfActionType type : WerewolfActionType.values()) {
            if (normalized.equals(type.name()) || normalized.contains(type.name())) return type;
        }
        if (normalized.contains("袭击") || normalized.contains("击杀") || normalized.equals("KILL")) return WerewolfActionType.WOLF_KILL;
        if (normalized.contains("守护") || normalized.contains("保护") || normalized.equals("GUARD")) return WerewolfActionType.GUARD_PROTECT;
        if (normalized.contains("查验") || normalized.contains("验人") || normalized.equals("CHECK")) return WerewolfActionType.SEER_CHECK;
        if (normalized.contains("解药") || normalized.contains("救") || normalized.equals("SAVE")) return WerewolfActionType.WITCH_SAVE;
        if (normalized.contains("毒") || normalized.equals("POISON")) return WerewolfActionType.WITCH_POISON;
        if (normalized.contains("发言") || normalized.contains("讨论") || normalized.equals("DISCUSS")) return WerewolfActionType.SPEAK;
        if (normalized.contains("质询") || normalized.contains("提问")) return WerewolfActionType.QUESTION;
        if (normalized.contains("回应") || normalized.contains("回答")) return WerewolfActionType.RESPOND;
        if (normalized.contains("最终陈述") || normalized.contains("总结")) return WerewolfActionType.FINAL_STATEMENT;
        if (normalized.contains("投票") || normalized.equals("VOTE_OUT")) return WerewolfActionType.VOTE;
        if (normalized.contains("跳过") || normalized.contains("不使用") || normalized.equals("PASS")) return WerewolfActionType.SKIP;
        throw CustomException.create(ResultCodeEnum.MODEL_RESPONSE_INVALID, "模型返回了未知行动类型：" + rawAction);
    }
}
