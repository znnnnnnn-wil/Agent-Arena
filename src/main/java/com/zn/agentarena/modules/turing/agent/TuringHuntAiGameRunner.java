package com.zn.agentarena.modules.turing.agent;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.modules.game.engine.ActionValidation;
import com.zn.agentarena.modules.game.engine.EngineResult;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import com.zn.agentarena.modules.turing.domain.*;
import com.zn.agentarena.modules.turing.engine.TuringHuntGameEngine;
import com.zn.agentarena.modules.turing.text.TuringTextPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 单步执行一个 AI 行动或一个确定性阶段转换，并在真人行动前暂停。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TuringHuntAiGameRunner {

    private final ModelGateway modelGateway;
    private final TuringHuntAgentContextBuilder contextBuilder;
    private final TuringHuntLocalStrategy localStrategy;
    private final TuringHuntFairnessMetrics fairnessMetrics;
    private final TuringHuntGameEngine gameEngine = new TuringHuntGameEngine();
    private final TuringHuntDecisionNormalizer normalizer = new TuringHuntDecisionNormalizer();

    public TuringHuntTurnRequirement inspect(TuringHuntGameState state) {
        if (gameEngine.isFinished(state)) return new TuringHuntTurnRequirement(null, null, false);
        TuringHuntActionType required = gameEngine.requiredActionType(state);
        Long actorId = gameEngine.requiredActorPlayerId(state);
        TuringHuntPlayerState actor = state.findPlayer(actorId);
        return new TuringHuntTurnRequirement(required, actorId, actor != null && actor.isHuman());
    }

    public EngineResult<TuringHuntGameState> runNextTurn(TuringHuntGameState state,
                                                          ModelRuntimeConfig runtimeConfig) {
        return runNextTurn(state, runtimeConfig, false);
    }

    public EngineResult<TuringHuntGameState> runNextTurn(TuringHuntGameState state,
                                                          ModelRuntimeConfig runtimeConfig,
                                                          boolean modelRateLimited) {
        TuringHuntTurnRequirement requirement = inspect(state);
        if (requirement.actionType() == null) {
            throw CustomException.create(ResultCodeEnum.GAME_STATUS_ERROR, "图灵潜伏局已经结束");
        }
        if (requirement.actionType() == TuringHuntActionType.ADVANCE) {
            return gameEngine.execute(state, TuringHuntAction.advance());
        }
        TuringHuntPlayerState actor = state.findPlayer(requirement.actorPlayerId());
        if (actor == null) throw CustomException.create(ResultCodeEnum.PLAYER_NOT_FOUND);
        if (actor.isHuman()) {
            throw CustomException.create(ResultCodeEnum.ACTION_NOT_ALLOWED, "当前正在等待真人完成操作");
        }

        TuringHuntActionType actionType = requirement.actionType();
        TuringHuntAction action = localStrategy.create(state, actor, actionType);
        boolean modelAccepted = false;
        boolean retried = false;
        boolean compressed = false;
        long startedAt = System.nanoTime();
        if (runtimeConfig != null && !modelRateLimited) {
            String correction = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    TuringHuntAiDecision decision = modelGateway.generateStructured(withActionBudget(runtimeConfig, actionType),
                            contextBuilder.build(state, actor, actionType, correction), TuringHuntAiDecision.class);
                    TuringHuntAction candidate = normalizer.normalize(state, actor, actionType, decision);
                    String raw = decisionText(decision, actionType);
                    compressed |= TuringTextPolicy.INSTANCE.codePointCount(TuringTextPolicy.INSTANCE.normalize(raw))
                            > TuringTextPolicy.INSTANCE.rule(actionType).maximum();
                    ActionValidation validation = gameEngine.validate(state, candidate);
                    if (validation.valid()) {
                        action = candidate;
                        modelAccepted = true;
                        break;
                    }
                    log.info("图灵潜伏局模型动作规则校验失败，使用本地合法动作: gameId={}, playerId={}, reason={}",
                            state.getGameId(), actor.getPlayerId(), validation.resultCode());
                    break;
                } catch (CustomException exception) {
                    boolean textCanRetry = exception.getResultCodeEnum() == ResultCodeEnum.MODEL_RESPONSE_INVALID;
                    if (attempt == 0 && textCanRetry) {
                        retried = true;
                        correction = exception.getMessage();
                        continue;
                    }
                    log.warn("图灵潜伏局模型决策失败，使用本地合法动作: gameId={}, playerId={}, reason={}",
                            state.getGameId(), actor.getPlayerId(), exception.getClass().getSimpleName());
                    break;
                } catch (RuntimeException exception) {
                    log.warn("图灵潜伏局模型决策失败，使用本地合法动作: gameId={}, playerId={}, reason={}",
                            state.getGameId(), actor.getPlayerId(), exception.getClass().getSimpleName());
                    break;
                }
            }
        }
        fairnessMetrics.record(actionType, action, System.nanoTime() - startedAt,
                retried, runtimeConfig != null && !modelAccepted, compressed);
        return gameEngine.execute(state, action);
    }

    private ModelRuntimeConfig withActionBudget(ModelRuntimeConfig config, TuringHuntActionType type) {
        int desired = switch (type) {
            case ANSWER -> 256;
            case SUSPECT -> 192;
            case QUESTION -> 160;
            case RESPOND -> 192;
            case DEFEND -> 224;
            case VOTE -> 128;
            case ADVANCE -> 128;
        };
        int maximum = config.maxTokens() == null ? desired : Math.min(config.maxTokens(), desired);
        return new ModelRuntimeConfig(config.provider(), config.modelName(), config.baseUrl(), config.apiKey(),
                config.temperature(), maximum, config.timeout(), config.maxRetries());
    }

    private String decisionText(TuringHuntAiDecision decision, TuringHuntActionType type) {
        if (decision == null) return "";
        return switch (type) {
            case ANSWER -> decision.answer();
            case SUSPECT, VOTE -> decision.reasonSummary();
            case QUESTION -> decision.question();
            case RESPOND -> decision.response();
            case DEFEND -> decision.defense();
            case ADVANCE -> "";
        };
    }
}
