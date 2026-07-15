package com.zn.agentarena.modules.werewolf.agent;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.modules.game.engine.EngineResult;
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

/** 狼人杀 AI 自动推进器；没有配置模型时使用确定性本地策略。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WerewolfAiGameRunner {
    private final ModelGateway modelGateway;
    private final WerewolfAgentContextBuilder contextBuilder;
    private final WerewolfGameEngine gameEngine = new WerewolfGameEngine();

    /** 根据当前状态执行下一位 AI 玩家的行动。 */
    public EngineResult<WerewolfGameState> runNextTurn(WerewolfGameState state,
                                                        Map<Long, ModelRuntimeConfig> runtimeConfigs) {
        WerewolfPlayerState actor = findNextActor(state);
        if (actor == null) {
            throw CustomException.create(ResultCodeEnum.ACTION_NOT_ALLOWED, "当前没有可行动玩家");
        }
        ModelRuntimeConfig config = runtimeConfigs.get(actor.getPlayerId());
        if (config == null) {
            WerewolfAction action = createFallbackAction(state, actor);
            return executeWithDecision(state, actor, action, "本地策略根据当前阶段选择了合法行动。", 60, "LOCAL");
        }
        try {
        WerewolfAiDecision decision = modelGateway.generateStructured(config,
                contextBuilder.build(state, actor), WerewolfAiDecision.class);
        WerewolfAction modelAction = toAction(actor, decision);
        boolean valid = gameEngine.validate(state, modelAction).valid();
        WerewolfAction finalAction = valid ? modelAction : createFallbackAction(state, actor);
        String summary = valid ? decision.reasonSummary() : "模型行动不符合当前规则，系统已切换为合法策略。";
        return executeWithDecision(state, actor, finalAction, summary, decision.confidence(), valid ? "MODEL" : "MODEL_FALLBACK");
        } catch (RuntimeException exception) {
            log.warn("狼人杀模型决策失败，改用本地策略: gameId={}, playerId={}, reason={}",
                    state.getGameId(), actor.getPlayerId(), exception.getClass().getSimpleName());
            WerewolfAction action = createFallbackAction(state, actor);
            return executeWithDecision(state, actor, action,
                    "模型调用或结构化输出失败，系统已使用本地身份策略继续对局。", 0, "MODEL_FALLBACK");
        }
    }

    private EngineResult<WerewolfGameState> executeWithDecision(WerewolfGameState state,
                                                                 WerewolfPlayerState actor,
                                                                 WerewolfAction action,
                                                                 String summary,
                                                                 Integer confidence,
                                                                 String source) {
        state.setSequenceNo(state.getSequenceNo() + 1);
        Map<String, Object> decisionPayload = new LinkedHashMap<>();
        decisionPayload.put("action", action.type().name());
        decisionPayload.put("targetPlayerId", action.targetPlayerId());
        decisionPayload.put("summary", summary == null ? "模型未提供决策摘要" : summary);
        decisionPayload.put("confidence", confidence == null ? 0 : confidence);
        decisionPayload.put("source", source);
        EngineResult<WerewolfGameState> engineResult = gameEngine.execute(state, action);
        ArrayList<com.zn.agentarena.modules.game.engine.EngineEvent> events = new ArrayList<>();
        Map<String, Object> publicDecisionPayload = switch (state.getPhase()) {
            case DAY_DISCUSSION, DAY_VOTE -> new LinkedHashMap<>(decisionPayload);
            default -> Map.of("completed", true, "source", source);
        };
        events.add(new com.zn.agentarena.modules.game.engine.EngineEvent("AI_DECISION", actor.getPlayerId(),
                decisionPayload, publicDecisionPayload));
        events.addAll(engineResult.events());
        return new EngineResult<>(state, events);
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
            case DAY_DISCUSSION -> new WerewolfAction(actor.getPlayerId(), WerewolfActionType.SPEAK, null,
                    fallbackSpeech(state, actor));
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

    private WerewolfAction toAction(WerewolfPlayerState actor, WerewolfAiDecision decision) {
        if (decision == null || decision.action() == null) {
            throw CustomException.create(ResultCodeEnum.MODEL_RESPONSE_INVALID, "模型未返回行动类型");
        }
        WerewolfActionType actionType = normalizeAction(decision.action());
        return new WerewolfAction(actor.getPlayerId(), actionType, decision.targetPlayerId(), decision.publicMessage());
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
        if (normalized.contains("投票") || normalized.equals("VOTE_OUT")) return WerewolfActionType.VOTE;
        if (normalized.contains("跳过") || normalized.contains("不使用") || normalized.equals("PASS")) return WerewolfActionType.SKIP;
        throw CustomException.create(ResultCodeEnum.MODEL_RESPONSE_INVALID, "模型返回了未知行动类型：" + rawAction);
    }
}
