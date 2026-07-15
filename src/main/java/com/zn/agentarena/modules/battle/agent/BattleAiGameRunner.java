package com.zn.agentarena.modules.battle.agent;

import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.engine.BattleRoyaleGameEngine;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import com.zn.agentarena.modules.game.engine.EngineResult;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** 使用真实模型或本地回退策略推进大逃杀。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BattleAiGameRunner {
    private final ModelGateway modelGateway;
    private final BattleAgentContextBuilder contextBuilder;
    private final BattleRoyaleGameEngine gameEngine = new BattleRoyaleGameEngine();

    public EngineResult<BattleRoyaleGameState> runNextTurn(BattleRoyaleGameState state,
                                                            BattlePlayerState actor,
                                                            ModelRuntimeConfig config) {
        if (config == null) {
            BattleRoyaleAction action = fallbackAction(state, actor);
            return executeWithDecision(state, actor, action, "本地策略根据资源、生命和同区域目标作出行动。", 60, "LOCAL");
        }
        try {
            BattleAiDecision decision = modelGateway.generateStructured(config, contextBuilder.build(state, actor), BattleAiDecision.class);
            BattleRoyaleAction action = sanitizeAction(state, actor, decision);
            return executeWithDecision(state, actor, action, decision.reasonSummary(), decision.confidence(), "MODEL");
        } catch (RuntimeException exception) {
            log.warn("大逃杀模型决策失败，改用本地策略: gameId={}, playerId={}, reason={}",
                    state.getGameId(), actor.getPlayerId(), exception.getClass().getSimpleName());
            BattleRoyaleAction action = fallbackAction(state, actor);
            return executeWithDecision(state, actor, action,
                    "模型调用或结构化输出失败，系统已使用本地生存策略继续对局。", 0, "MODEL_FALLBACK");
        }
    }

    private BattleRoyaleAction sanitizeAction(BattleRoyaleGameState state, BattlePlayerState actor, BattleAiDecision decision) {
        BattleActionType type;
        try { type = BattleActionType.valueOf(decision.action().trim().toUpperCase()); }
        catch (Exception ignored) { return fallbackAction(state, actor); }
        BattlePlayerState target = state.findPlayer(decision.targetPlayerId());
        String forcedRegion = null;
        if (type == BattleActionType.HEAL && actor.getMedkits() <= 0) {
            type = actor.getHealth() < 60 ? BattleActionType.REST : BattleActionType.SEARCH;
        }
        if (type == BattleActionType.SEARCH && "SEARCH".equals(actor.getLastAction()) && actor.getRepeatedActions() >= 2) {
            if (state.getSupplyDropRegion() != null && !state.getSupplyDropRegion().equals(actor.getRegion())) {
                type = BattleActionType.MOVE;
                forcedRegion = state.getSupplyDropRegion();
            } else if (actor.getArmor() < 30) type = BattleActionType.GUARD;
            else if (actor.getHealth() < 85) type = BattleActionType.REST;
        }
        if (type == BattleActionType.ATTACK || type == BattleActionType.TRADE) {
            if (target == null || !target.isAlive() || target.getPlayerId().equals(actor.getPlayerId())
                    || !actor.getRegion().equals(target.getRegion())) {
                target = sameRegionTarget(state, actor);
            }
            if (target == null) type = BattleActionType.SEARCH;
        }
        String region = forcedRegion == null ? decision.targetRegion() : forcedRegion;
        if (type == BattleActionType.MOVE && !state.getRegions().contains(region)) {
            region = state.getRegions().stream().filter(item -> !item.equals(actor.getRegion())).findFirst().orElse(actor.getRegion());
        }
        return new BattleRoyaleAction(actor.getPlayerId(), type, target == null ? null : target.getPlayerId(), region);
    }

    private BattleRoyaleAction fallbackAction(BattleRoyaleGameState state, BattlePlayerState actor) {
        BattlePlayerState target = sameRegionTarget(state, actor);
        BattleActionType type = actor.getHealth() < 45 && actor.getMedkits() > 0 ? BattleActionType.HEAL
                : actor.getHealth() < 30 ? BattleActionType.REST
                : state.getSafeRegions() != null && !state.getSafeRegions().contains(actor.getRegion()) ? BattleActionType.MOVE
                : target != null && ((actor.isArmed() && actor.getAmmo() > 0) || state.getRoundNo() >= 4) ? BattleActionType.ATTACK
                : actor.getArmor() < 20 && actor.isArmed() ? BattleActionType.GUARD
                : BattleActionType.SEARCH;
        String targetRegion = type == BattleActionType.MOVE
                ? state.getSafeRegions().stream().findFirst().orElse(state.getSafeRegion()) : null;
        return new BattleRoyaleAction(actor.getPlayerId(), type, target == null ? null : target.getPlayerId(), targetRegion);
    }

    private BattlePlayerState sameRegionTarget(BattleRoyaleGameState state, BattlePlayerState actor) {
        return state.alivePlayers().stream().filter(item -> !item.getPlayerId().equals(actor.getPlayerId()))
                .filter(item -> actor.getRegion().equals(item.getRegion())).findFirst().orElse(null);
    }

    private EngineResult<BattleRoyaleGameState> executeWithDecision(BattleRoyaleGameState state,
                                                                     BattlePlayerState actor,
                                                                     BattleRoyaleAction action,
                                                                     String summary,
                                                                     Integer confidence,
                                                                     String source) {
        state.setSequenceNo(state.getSequenceNo() + 1);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action.type().name());
        payload.put("targetPlayerId", action.targetPlayerId());
        payload.put("targetRegion", action.targetRegion());
        payload.put("summary", summary == null ? "模型未提供决策摘要" : summary);
        payload.put("confidence", confidence == null ? 0 : confidence);
        payload.put("source", source);
        Map<String, Object> publicPayload = payload;
        if (action.type() == BattleActionType.HIDE) {
            publicPayload = new LinkedHashMap<>();
            publicPayload.put("action", action.type().name());
            publicPayload.put("confidence", confidence == null ? 0 : confidence);
            publicPayload.put("source", source);
        }
        EngineResult<BattleRoyaleGameState> result = gameEngine.execute(state, action);
        ArrayList<EngineEvent> events = new ArrayList<>();
        events.add(new EngineEvent("AI_DECISION", actor.getPlayerId(), payload, publicPayload));
        events.addAll(result.events());
        return new EngineResult<>(state, events);
    }
}
