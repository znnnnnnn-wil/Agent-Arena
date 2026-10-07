package com.zn.agentarena.modules.battle.agent;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.modules.battle.communication.BattleCommunicationDecision;
import com.zn.agentarena.modules.battle.communication.BattleCommunicationResult;
import com.zn.agentarena.modules.battle.communication.BattleCommunicationSystem;
import com.zn.agentarena.modules.battle.domain.BattleActionType;
import com.zn.agentarena.modules.battle.domain.BattlePlayerLifeStatus;
import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleAction;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.decision.BattleCandidateDecision;
import com.zn.agentarena.modules.battle.decision.BattleCandidateResolution;
import com.zn.agentarena.modules.battle.decision.BattleCandidateResolutionCode;
import com.zn.agentarena.modules.battle.decision.BattleDecisionCandidate;
import com.zn.agentarena.modules.battle.decision.BattleDecisionCandidateGenerator;
import com.zn.agentarena.modules.battle.decision.BattleDecisionCandidateResolver;
import com.zn.agentarena.modules.battle.decision.BattleDecisionCandidateSet;
import com.zn.agentarena.modules.battle.decision.BattleHeuristicCandidateSelector;
import com.zn.agentarena.modules.battle.decision.BattleHeuristicSocialCandidateSelector;
import com.zn.agentarena.modules.battle.decision.BattleSocialCandidateResolution;
import com.zn.agentarena.modules.battle.decision.BattleSocialDecisionCandidateGenerator;
import com.zn.agentarena.modules.battle.decision.BattleSocialDecisionCandidateResolver;
import com.zn.agentarena.modules.battle.decision.BattleSocialDecisionCandidateSet;
import com.zn.agentarena.modules.battle.engine.BattleRoyaleGameEngine;
import com.zn.agentarena.modules.battle.memory.BattleMemorySystem;
import com.zn.agentarena.modules.battle.memory.BattleMemoryType;
import com.zn.agentarena.modules.battle.perception.BattleAgentObservation;
import com.zn.agentarena.modules.battle.perception.BattleObservationSystem;
import com.zn.agentarena.modules.battle.perception.ObservedBattlePlayer;
import com.zn.agentarena.modules.battle.social.BattleRelationshipChange;
import com.zn.agentarena.modules.battle.social.BattleRelationshipState;
import com.zn.agentarena.modules.battle.social.BattleRelationshipSystem;
import com.zn.agentarena.modules.battle.social.BattleSocialStateInitializer;
import com.zn.agentarena.modules.battle.social.BattleSocialActionSystem;
import com.zn.agentarena.modules.battle.social.BattleSocialActionResult;
import com.zn.agentarena.modules.battle.social.BattleSocialContext;
import com.zn.agentarena.modules.battle.social.BattleBetrayalDetector;
import com.zn.agentarena.modules.battle.social.BattleBetrayalResult;
import com.zn.agentarena.modules.battle.social.BattleSocialDecision;
import com.zn.agentarena.modules.battle.alliance.BattleAllianceStatus;
import com.zn.agentarena.modules.battle.alliance.BattleAllianceSystem;
import com.zn.agentarena.modules.battle.promise.BattlePromiseSystem;
import com.zn.agentarena.modules.battle.trade.BattleTradeSystem;
import com.zn.agentarena.modules.battle.goal.BattleHiddenGoalSystem;
import com.zn.agentarena.modules.battle.objective.BattleCooperativeObjectiveSystem;
import com.zn.agentarena.modules.battle.information.BattleRumorSystem;
import com.zn.agentarena.modules.battle.information.BattleInformationTradeSystem;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import com.zn.agentarena.modules.game.engine.EngineResult;
import com.zn.agentarena.modules.model.domain.ModelFailureInfo;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import com.zn.agentarena.modules.model.gateway.LangChain4jModelGateway;
import com.zn.agentarena.modules.model.gateway.StructuredModelDiagnostics;
import com.zn.agentarena.modules.battle.simulation.trace.BattleDecisionPipelineTrace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 使用真实模型或遵守同一知识边界的本地策略推进大逃杀。 */
@Service
@Slf4j
public class BattleAiGameRunner {
    private final ModelGateway modelGateway;
    private final BattleAgentContextBuilder contextBuilder;
    private final BattleObservationSystem observationSystem;
    private final BattleMemorySystem memorySystem;
    private final BattleRelationshipSystem relationshipSystem;
    private final BattleCommunicationSystem communicationSystem;
    private final BattleSocialStateInitializer socialStateInitializer;
    private final BattleAllianceSystem allianceSystem;
    private final BattlePromiseSystem promiseSystem;
    private final BattleTradeSystem tradeSystem;
    private final BattleSocialActionSystem socialActionSystem;
    private final BattleBetrayalDetector betrayalDetector;
    private final BattleHiddenGoalSystem hiddenGoalSystem;
    private final BattleCooperativeObjectiveSystem objectiveSystem;
    private final BattleRumorSystem rumorSystem;
    private final BattleInformationTradeSystem informationTradeSystem;
    private final BattleDecisionCandidateGenerator candidateGenerator;
    private final BattleDecisionCandidateResolver candidateResolver;
    private final BattleHeuristicCandidateSelector heuristicCandidateSelector;
    private final BattleSocialDecisionCandidateGenerator socialCandidateGenerator;
    private final BattleSocialDecisionCandidateResolver socialCandidateResolver;
    private final BattleRoyaleGameEngine gameEngine = new BattleRoyaleGameEngine();
    private final BattleHeuristicSocialCandidateSelector localSocialSelector = new BattleHeuristicSocialCandidateSelector();

    @Autowired
    public BattleAiGameRunner(ModelGateway modelGateway,
                              BattleAgentContextBuilder contextBuilder,
                              BattleObservationSystem observationSystem,
                              BattleMemorySystem memorySystem,
                              BattleRelationshipSystem relationshipSystem,
                              BattleCommunicationSystem communicationSystem,
                              BattleSocialStateInitializer socialStateInitializer,
                              BattleAllianceSystem allianceSystem,
                              BattlePromiseSystem promiseSystem,
                              BattleTradeSystem tradeSystem,
                              BattleSocialActionSystem socialActionSystem,
                              BattleBetrayalDetector betrayalDetector,
                              BattleHiddenGoalSystem hiddenGoalSystem,
                              BattleCooperativeObjectiveSystem objectiveSystem,
                              BattleRumorSystem rumorSystem,
                              BattleInformationTradeSystem informationTradeSystem,
                              BattleDecisionCandidateGenerator candidateGenerator,
                              BattleDecisionCandidateResolver candidateResolver,
                              BattleHeuristicCandidateSelector heuristicCandidateSelector,
                              BattleSocialDecisionCandidateGenerator socialCandidateGenerator,
                              BattleSocialDecisionCandidateResolver socialCandidateResolver) {
        this.modelGateway = modelGateway;
        this.contextBuilder = contextBuilder;
        this.observationSystem = observationSystem;
        this.memorySystem = memorySystem;
        this.relationshipSystem = relationshipSystem;
        this.communicationSystem = communicationSystem;
        this.socialStateInitializer = socialStateInitializer;
        this.allianceSystem = allianceSystem;
        this.promiseSystem = promiseSystem;
        this.tradeSystem = tradeSystem;
        this.socialActionSystem = socialActionSystem;
        this.betrayalDetector = betrayalDetector;
        this.hiddenGoalSystem = hiddenGoalSystem;
        this.objectiveSystem = objectiveSystem;
        this.rumorSystem = rumorSystem;
        this.informationTradeSystem = informationTradeSystem;
        this.candidateGenerator = candidateGenerator;
        this.candidateResolver = candidateResolver;
        this.heuristicCandidateSelector = heuristicCandidateSelector;
        this.socialCandidateGenerator = socialCandidateGenerator;
        this.socialCandidateResolver = socialCandidateResolver;
    }

    /** 兼容不启动 Spring 容器的轻量单元测试。 */
    public BattleAiGameRunner(ModelGateway modelGateway, BattleAgentContextBuilder contextBuilder) {
        this.modelGateway = modelGateway;
        this.contextBuilder = contextBuilder;
        this.socialStateInitializer = new BattleSocialStateInitializer();
        this.observationSystem = new BattleObservationSystem();
        this.memorySystem = new BattleMemorySystem(socialStateInitializer);
        this.relationshipSystem = new BattleRelationshipSystem(socialStateInitializer);
        this.communicationSystem = new BattleCommunicationSystem(socialStateInitializer);
        this.allianceSystem = new BattleAllianceSystem(socialStateInitializer, memorySystem, relationshipSystem);
        this.promiseSystem = new BattlePromiseSystem(socialStateInitializer, memorySystem, relationshipSystem);
        this.tradeSystem = new BattleTradeSystem(socialStateInitializer, memorySystem, relationshipSystem);
        this.rumorSystem = new BattleRumorSystem(memorySystem);
        this.informationTradeSystem = new BattleInformationTradeSystem(rumorSystem, memorySystem);
        this.objectiveSystem = new BattleCooperativeObjectiveSystem(memorySystem);
        this.hiddenGoalSystem = new BattleHiddenGoalSystem(memorySystem);
        this.socialActionSystem = new BattleSocialActionSystem(allianceSystem, promiseSystem, tradeSystem,
                objectiveSystem, informationTradeSystem, rumorSystem);
        this.betrayalDetector = new BattleBetrayalDetector(allianceSystem, memorySystem, relationshipSystem);
        this.candidateGenerator = new BattleDecisionCandidateGenerator();
        this.candidateResolver = new BattleDecisionCandidateResolver();
        this.heuristicCandidateSelector = new BattleHeuristicCandidateSelector();
        this.socialCandidateGenerator = new BattleSocialDecisionCandidateGenerator();
        this.socialCandidateResolver = new BattleSocialDecisionCandidateResolver();
    }

    public EngineResult<BattleRoyaleGameState> runNextTurn(BattleRoyaleGameState state,
                                                            BattlePlayerState actor,
                                                            ModelRuntimeConfig config) {
        return runNextTurn(state, actor, config, false);
    }

    /** 供无头仿真复用与生产完全相同的执行链，不经过模型调用。 */
    public EngineResult<BattleRoyaleGameState> runPreparedTurn(BattleRoyaleGameState state,
                                                                BattlePlayerState actor,
                                                                BattleRoyaleAction action,
                                                                BattleSocialDecision socialDecision,
                                                                BattleCommunicationDecision communication) {
        return executeWithDecision(state, actor, action, socialDecision, communication,
                "FAST_SIMULATION deterministic policy", 100, "FAST_SIMULATION", null);
    }

    public EngineResult<BattleRoyaleGameState> runNextTurn(BattleRoyaleGameState state,
                                                            BattlePlayerState actor,
                                                            ModelRuntimeConfig config,
                                                            boolean modelRateLimited) {
        BattleAgentContext context = createContext(state, actor);
        BattleDecisionCandidateSet candidateSet = candidateGenerator.generate(state, actor, context);
        BattleSocialDecisionCandidateSet socialCandidateSet = socialCandidateGenerator.generate(state, actor, context);
        if (config == null) {
            BattleDecisionCandidate selected = heuristicCandidateSelector.select(candidateSet, context);
            BattleRoyaleAction action = selected == null
                    ? new BattleRoyaleAction(actor.getPlayerId(), BattleActionType.SKIP, null, null)
                    : selected.toAction();
            var social = localSocialSelector.select(socialCandidateSet, context, action);
            if (modelRateLimited) {
                ModelFailureInfo failure = ModelFailureInfo.rateLimited();
                return executeWithDecision(state, actor, action, social.decision(), localCommunication(social), failure.summary(), 0,
                        "MODEL_FALLBACK", failure.code());
            }
            return executeWithDecision(state, actor, action, social.decision(), localCommunication(social),
                    "本地策略仅根据当前观察、自身状态和主观关系作出行动。", 60, "LOCAL", null);
        }
        BattleCandidateDecision decision;
        try {
            decision = modelGateway.generateStructured(config,
                    contextBuilder.build(context, candidateSet, socialCandidateSet), BattleCandidateDecision.class);
            if (decision == null) {
                throw CustomException.create(ResultCodeEnum.MODEL_RESPONSE_INVALID, "模型未返回候选选择");
            }
        } catch (RuntimeException exception) {
            ModelFailureInfo failure = ModelFailureInfo.from(exception);
            log.warn("大逃杀模型决策失败，改用有限信息本地策略: gameId={}, playerId={}, code={}, reason={}",
                    state.getGameId(), actor.getPlayerId(), failure.code(), exception.getClass().getSimpleName());
            BattleDecisionCandidate selected = heuristicCandidateSelector.select(candidateSet, context);
            BattleRoyaleAction action = selected == null
                    ? new BattleRoyaleAction(actor.getPlayerId(), BattleActionType.SKIP, null, null)
                    : selected.toAction();
            var social = localSocialSelector.select(socialCandidateSet, context, action);
            return executeWithDecision(state, actor, action, social.decision(), localCommunication(social), failure.summary(), 0,
                    "MODEL_FALLBACK", failure.code());
        }
        BattleCandidateResolution resolution = candidateResolver.resolve(candidateSet,
                decision.tacticalCandidateId(), state, actor);
        BattleRoyaleAction action = resolution.action();
        if (!resolution.selected()) {
            var selected = heuristicCandidateSelector.select(candidateSet, context);
            action = selected == null ? new BattleRoyaleAction(actor.getPlayerId(), BattleActionType.SKIP, null, null)
                    : selected.toAction();
        }
        BattleSocialCandidateResolution socialResolution = socialCandidateResolver.resolve(socialCandidateSet,
                decision.socialCandidateId(), state, actor);
        BattleSocialDecision resolvedSocial = socialResolution.selected() ? socialResolution.decision() : null;
        String socialCode = socialResolution.code();
        if (resolvedSocial != null) {
            var selectedSocial = socialCandidateSet.candidates().stream()
                    .filter(c -> Objects.equals(c.decision(), socialResolution.decision())).findFirst().orElseThrow();
            if (!com.zn.agentarena.modules.battle.decision.BattleSocialCandidateCompatibility
                    .allows(selectedSocial, context, action)) {
                resolvedSocial = null;
                socialCode = "INCOMPATIBLE_TACTICAL_SOCIAL_PAIR";
            }
        }
        if (!"SELECTED".equals(socialCode)) {
            memorySystem.rememberFact(state, actor.getPlayerId(), BattleMemoryType.SOCIAL_ACTION_REJECTED,
                    actor.getPlayerId(), null, "社会候选未执行：" + socialCode
                            + "；请检查当前 S 编号及 compatibleTacticalIds", 100, actor.getRegion());
        }
        BattleCommunicationDecision resolvedCommunication = resolvedSocial != null
                && socialResolution.coupledCommunication() != null
                ? socialResolution.coupledCommunication() : decision.communication();
        var result = executeWithDecision(state, actor, action, resolvedSocial, resolvedCommunication,
                resolution.selected() ? decision.reasonSummary() : "模型候选无效，本回合使用本地战术策略。",
                resolution.selected() ? decision.confidence() : Integer.valueOf(0),
                resolution.selected() ? "MODEL" : "MODEL_FALLBACK",
                resolution.selected() ? null : resolution.code().name(),
                decision.tacticalCandidateId(), resolution.code().name());
        result.events().get(0).payload().put("selectedSocialCandidateId", decision.socialCandidateId());
        result.events().get(0).payload().put("socialCandidateResolution", socialCode);
        return result;
    }

    private BattleCommunicationDecision localCommunication(
            com.zn.agentarena.modules.battle.decision.BattleSocialDecisionCandidate candidate) {
        if (candidate.coupledCommunication() != null) return candidate.coupledCommunication();
        if (candidate.decision() == null || candidate.decision().targetPlayerId() == null) return null;
        return new BattleCommunicationDecision("PRIVATE", candidate.decision().targetPlayerId(),
                "我选择了：" + candidate.effectSummary());
    }

    /** 构建可用于测试和诊断的有限上下文；不会返回真实 GameState。 */
    public BattleAgentContext createContext(BattleRoyaleGameState state, BattlePlayerState actor) {
        socialStateInitializer.ensure(state);
        hiddenGoalSystem.ensureAssigned(state);
        objectiveSystem.ensureObjective(state);
        BattleAgentObservation observation = observationSystem.observe(state, actor.getPlayerId());
        memorySystem.rememberObservation(state, observation);
        relationshipSystem.updatePerceivedStrength(state, observation);
        return new BattleAgentContext(actor.getPersona(), observation,
                memorySystem.memoriesFor(state, actor.getPlayerId()),
                relationshipSystem.relationshipsFor(state, actor.getPlayerId()),
                communicationSystem.messagesVisibleTo(state, actor.getPlayerId()).stream()
                        .filter(message -> !Objects.equals(message.getSenderPlayerId(), actor.getPlayerId())).toList(),
                new BattleSocialContext(allianceSystem.visibleTo(state, actor.getPlayerId()),
                        promiseSystem.visibleTo(state, actor.getPlayerId()),
                        tradeSystem.visibleTo(state, actor.getPlayerId()),
                        hiddenGoalSystem.visibleTo(state, actor.getPlayerId()),
                        state.getCooperativeObjectives(),
                        informationTradeSystem.visibleTo(state, actor.getPlayerId()),
                        rumorSystem.visibleTo(state, actor.getPlayerId())),
                observation.publicWorld().gameRules());
    }

    /** 暴露只读、临时的生产候选集，供测试和内部仿真采样；不会写入 GameState。 */
    public BattleDecisionCandidateSet createTacticalCandidates(BattleRoyaleGameState state,
                                                                BattlePlayerState actor,
                                                                BattleAgentContext context) {
        return candidateGenerator.generate(state, actor, context);
    }

    /** 构建当前正式生产 Prompt。 */
    public String buildProductionPrompt(BattleRoyaleGameState state, BattlePlayerState actor,
                                        BattleAgentContext context) {
        return contextBuilder.build(context, createTacticalCandidates(state, actor, context),
                socialCandidateGenerator.generate(state, actor, context));
    }

    /** 一次真实生产决策的全链路诊断入口；不改变生产执行链，仅补充 simulation/debug 观测。 */
    public BattleDecisionPipelineTrace runDiagnosticTurn(BattleRoyaleGameState state, BattlePlayerState actor,
                                                          ModelRuntimeConfig config, String scenarioId,
                                                          long seed, int runIndex) {
        return runDiagnosticTurn(state, actor, config, scenarioId, seed, runIndex, null, Map.of());
    }

    /** 允许实验 Harness 覆盖 Prompt；生产 runNextTurn 始终仍使用 contextBuilder。 */
    public BattleDecisionPipelineTrace runDiagnosticTurn(BattleRoyaleGameState state, BattlePlayerState actor,
                                                          ModelRuntimeConfig config, String scenarioId,
                                                          long seed, int runIndex, String promptOverride,
                                                          Map<String, Object> experimentMetadata) {
        if (promptOverride == null) {
            return runCandidateDiagnosticTurn(state, actor, config, scenarioId, seed, runIndex,
                    experimentMetadata == null ? Map.of() : experimentMetadata);
        }
        BattleAgentContext context = createContext(state, actor);
        String prompt = promptOverride;
        BattleAiDecision parsed = null;
        BattleRoyaleAction sanitized = null;
        String source = "MODEL";
        String fallbackCode = null;
        String raw = null;
        boolean repair = false;
        String validationReason = "validated by production execution";
        try {
            parsed = modelGateway.generateStructured(config, prompt, BattleAiDecision.class);
            if (modelGateway instanceof StructuredModelDiagnostics gateway) {
                raw = gateway.lastRawResponse();
                repair = gateway.lastParserRepair();
            }
            sanitized = sanitizeAction(context, actor, parsed);
        } catch (RuntimeException ex) {
            source = "MODEL_FALLBACK";
            fallbackCode = ex.getClass().getSimpleName();
            sanitized = fallbackAction(context, actor);
            validationReason = "fallback after model/parser failure: " + ex.getClass().getSimpleName();
        }
        EngineResult<BattleRoyaleGameState> result = executeWithDecision(state, actor, sanitized,
                parsed == null ? null : parsed.socialAction(), parsed == null ? null : parsed.communication(),
                parsed == null ? validationReason : parsed.reasonSummary(),
                parsed == null ? Integer.valueOf(0) : parsed.confidence(), source, fallbackCode);
        String parsedAction = parsed == null ? null : parsed.action();
        boolean changed = parsedAction != null && !parsedAction.equalsIgnoreCase(sanitized.type().name());
        String reason = changed ? "production sanitize/normalization changed action" : null;
        List<String> eventTypes = result.events().stream().map(EngineEvent::type).toList();
        String social = parsed == null || parsed.socialAction() == null ? null : parsed.socialAction().type();
        Map<String,Object> contextSummary = new LinkedHashMap<>();
        contextSummary.put("visiblePlayers", context.observation().visiblePlayers().stream().map(v -> v.playerId()).toList());
        contextSummary.put("selfHealth", context.observation().self().health());
        contextSummary.put("selfRegion", context.observation().self().region());
        contextSummary.put("availableSocialObjects", context.social().alliances().size() + context.social().promises().size()
                + context.social().tradeProposals().size() + context.social().informationTradeProposals().size());
        contextSummary.put("visibleDowned", context.observation().visiblePlayers().stream()
                .filter(v -> "DOWNED".equals(v.lifeStatus().name())).map(v -> v.playerId()).toList());
        if (experimentMetadata != null) contextSummary.putAll(experimentMetadata);
        return new BattleDecisionPipelineTrace(scenarioId, seed, runIndex, state.getRoundNo(), actor.getPlayerId(),
                config == null ? null : config.modelName(), config == null || config.provider() == null ? null : config.provider().name(),
                Map.copyOf(contextSummary), prompt, raw, repair, parsedAction,
                parsed == null ? null : parsed.targetPlayerId(), parsed == null ? null : parsed.targetRegion(),
                social, parsed == null || parsed.communication() == null ? null : parsed.communication().channel(),
                parsed == null ? validationReason : parsed.reasonSummary(),
                parsed == null ? Integer.valueOf(0) : parsed.confidence(),
                sanitized.type().name(), sanitized.targetPlayerId(), sanitized.targetRegion(), changed, reason,
                true, validationReason, sanitized.type().name(), sanitized.targetPlayerId(),
                parsed == null || parsed.socialAction() == null ? null : parsed.socialAction().type(), eventTypes,
                source, fallbackCode);
    }

    /** 正式 Candidate 生产链的一次决策诊断；候选详情只进入内部 trace。 */
    private BattleDecisionPipelineTrace runCandidateDiagnosticTurn(BattleRoyaleGameState state,
                                                                     BattlePlayerState actor,
                                                                     ModelRuntimeConfig config,
                                                                     String scenarioId,
                                                                     long seed,
                                                                     int runIndex,
                                                                     Map<String, Object> metadata) {
        int decisionRound = state.getRoundNo();
        BattleAgentContext context = createContext(state, actor);
        BattleDecisionCandidateSet candidateSet = candidateGenerator.generate(state, actor, context);
        BattleSocialDecisionCandidateSet socialCandidateSet = socialCandidateGenerator.generate(state, actor, context);
        String prompt = contextBuilder.build(context, candidateSet, socialCandidateSet);
        BattleCandidateDecision parsed = null;
        BattleCandidateResolution resolution = null;
        String source = "MODEL";
        String fallbackCode = null;
        String raw = null;
        boolean repair = false;
        try {
            parsed = modelGateway.generateStructured(config, prompt, BattleCandidateDecision.class);
            if (modelGateway instanceof StructuredModelDiagnostics gateway) {
                raw = gateway.lastRawResponse();
                repair = gateway.lastParserRepair();
            }
            resolution = candidateResolver.resolve(candidateSet,
                    parsed == null ? null : parsed.tacticalCandidateId(), state, actor);
            if (!resolution.selected()) fallbackCode = resolution.code().name();
        } catch (RuntimeException exception) {
            source = "MODEL_FALLBACK";
            fallbackCode = ModelFailureInfo.from(exception).code();
            BattleDecisionCandidate selected = heuristicCandidateSelector.select(candidateSet, context);
            BattleRoyaleAction action = selected == null
                    ? new BattleRoyaleAction(actor.getPlayerId(), BattleActionType.SKIP, null, null)
                    : selected.toAction();
            resolution = new BattleCandidateResolution(BattleCandidateResolutionCode.SELECTED,
                    selected, action, true, "模型或解析失败后使用独立 LOCAL 候选策略");
        }

        String selectedCandidateId = parsed == null ? null : parsed.tacticalCandidateId();
        BattleRoyaleAction diagnosticAction = resolution.action();
        if (!resolution.selected()) {
            var fallback = heuristicCandidateSelector.select(candidateSet, context);
            diagnosticAction = fallback == null ? new BattleRoyaleAction(actor.getPlayerId(), BattleActionType.SKIP, null, null)
                    : fallback.toAction();
            source = "MODEL_FALLBACK";
        }
        BattleSocialCandidateResolution socialResolution = socialCandidateResolver.resolve(socialCandidateSet,
                parsed == null ? null : parsed.socialCandidateId(), state, actor);
        BattleSocialDecision diagnosticSocial = socialResolution.selected() ? socialResolution.decision() : null;
        BattleCommunicationDecision diagnosticCommunication = socialResolution.coupledCommunication() == null
                ? parsed == null ? null : parsed.communication() : socialResolution.coupledCommunication();
        if (parsed == null) {
            var localSocial = localSocialSelector.select(socialCandidateSet, context, diagnosticAction);
            diagnosticSocial = localSocial.decision();
            diagnosticCommunication = localCommunication(localSocial);
        } else if (diagnosticSocial != null) {
            var socialCandidate = socialCandidateSet.candidates().stream()
                    .filter(c -> Objects.equals(c.decision(), socialResolution.decision())).findFirst().orElseThrow();
            if (!com.zn.agentarena.modules.battle.decision.BattleSocialCandidateCompatibility
                    .allows(socialCandidate, context, diagnosticAction)) {
                diagnosticSocial = null;
                diagnosticCommunication = parsed.communication();
            }
        }
        EngineResult<BattleRoyaleGameState> result = executeWithDecision(state, actor, diagnosticAction,
                diagnosticSocial, diagnosticCommunication,
                parsed == null ? resolution.validationReason() : parsed.reasonSummary(),
                parsed == null ? Integer.valueOf(0) : parsed.confidence(), source,
                "MODEL_FALLBACK".equals(source) ? fallbackCode : null,
                selectedCandidateId, resolution.code().name());

        BattleDecisionCandidate selected = resolution.selectedCandidate();
        Map<String, Object> contextSummary = new LinkedHashMap<>();
        contextSummary.put("visiblePlayers", context.observation().visiblePlayers().stream()
                .map(ObservedBattlePlayer::playerId).toList());
        contextSummary.put("selfHealth", context.observation().self().health());
        contextSummary.put("selfRegion", context.observation().self().region());
        contextSummary.put("candidateCount", candidateSet.candidates().size());
        contextSummary.put("candidates", candidateSet.candidates());
        contextSummary.put("selectedCandidateId", selectedCandidateId);
        contextSummary.put("candidateResolution", resolution.code().name());
        contextSummary.put("resolvedAction", diagnosticAction.type().name());
        contextSummary.put("legalityResult", resolution.validationReason());
        contextSummary.putAll(metadata);
        String parsedAction = selected == null ? null : selected.actionType().name();
        String social = diagnosticSocial == null ? null : diagnosticSocial.type();
        List<String> eventTypes = result.events().stream().map(EngineEvent::type).toList();
        boolean changed = resolution.code() != BattleCandidateResolutionCode.SELECTED;
        return new BattleDecisionPipelineTrace(scenarioId, seed, runIndex, decisionRound, actor.getPlayerId(),
                config == null ? null : config.modelName(),
                config == null || config.provider() == null ? null : config.provider().name(),
                Collections.unmodifiableMap(contextSummary), prompt, raw, repair, parsedAction,
                selected == null ? null : selected.targetPlayerId(), selected == null ? null : selected.targetRegion(),
                social, diagnosticCommunication == null ? null : diagnosticCommunication.channel(),
                parsed == null ? resolution.validationReason() : parsed.reasonSummary(),
                parsed == null ? Integer.valueOf(0) : parsed.confidence(), diagnosticAction.type().name(),
                diagnosticAction.targetPlayerId(), diagnosticAction.targetRegion(), changed,
                changed ? resolution.code().name() : null, resolution.valid(), resolution.validationReason(),
                diagnosticAction.type().name(), diagnosticAction.targetPlayerId(), social, eventTypes,
                source, fallbackCode);
    }

    private BattleRoyaleAction sanitizeAction(BattleAgentContext context, BattlePlayerState actor,
                                               BattleAiDecision decision) {
        if (decision == null || decision.action() == null || decision.action().isBlank()) {
            throw CustomException.create(ResultCodeEnum.MODEL_RESPONSE_INVALID, "模型未返回行动类型");
        }
        BattleActionType type;
        try {
            type = BattleActionType.valueOf(decision.action().trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw CustomException.create(ResultCodeEnum.MODEL_RESPONSE_INVALID, "模型返回了未知行动类型");
        }
        BattleAgentObservation observation = context.observation();
        var self = observation.self();
        if (actor.isDowned()) {
            return new BattleRoyaleAction(actor.getPlayerId(), BattleActionType.SKIP, null, null);
        }
        ObservedBattlePlayer target = visibleTarget(observation, decision.targetPlayerId());
        String forcedRegion = null;
        if (type == BattleActionType.TRADE) {
            type = BattleActionType.SEARCH;
            target = null;
        }
        if (type == BattleActionType.HEAL && self.medkits() <= 0) {
            type = self.health() < 60 ? BattleActionType.REST : BattleActionType.SEARCH;
        }
        if (type == BattleActionType.SEARCH && "SEARCH".equals(self.lastAction()) && self.repeatedActions() >= 2) {
            if (observation.publicWorld().supplyDropRegion() != null
                    && !observation.publicWorld().supplyDropRegion().equals(self.region())) {
                type = BattleActionType.MOVE;
                forcedRegion = observation.publicWorld().supplyDropRegion();
            } else if (self.armor() < 30) type = BattleActionType.GUARD;
            else if (self.health() < 85) type = BattleActionType.REST;
        }
        ObservedBattlePlayer bestTarget = bestVisibleTarget(context, actor);
        if (type == BattleActionType.SEARCH
                && Objects.equals(observation.publicWorld().supplyDropRegion(), self.region())
                && observation.publicWorld().supplyDropClaimPlayerId() == null) {
            type = BattleActionType.CAPTURE;
        }
        if (bestTarget != null && self.health() >= 35
                && attackValue(context, actor, bestTarget) >= aggressionThreshold(actor)
                && Set.of(BattleActionType.SEARCH, BattleActionType.REST, BattleActionType.GUARD,
                          BattleActionType.HIDE, BattleActionType.CAPTURE,
                          BattleActionType.SKIP).contains(type)) {
            type = BattleActionType.ATTACK;
            target = bestTarget;
        }
        if (type == BattleActionType.ATTACK) {
            if (target != null && target.lifeStatus() != BattlePlayerLifeStatus.ALIVE) target = null;
            if (target == null) target = bestTarget;
            if (target == null) type = BattleActionType.SEARCH;
        }
        if (type == BattleActionType.REVIVE || type == BattleActionType.EXECUTE) {
            if (target == null || target.lifeStatus() != BattlePlayerLifeStatus.DOWNED) {
                target = bestDownedTarget(context, actor, type == BattleActionType.REVIVE);
            }
            if (target == null || type == BattleActionType.REVIVE && self.medkits() <= 0) {
                type = BattleActionType.SEARCH;
            }
        }
        if (type == BattleActionType.CAPTURE
                && observation.publicWorld().supplyDropClaimPlayerId() != null) type = BattleActionType.SEARCH;
        if (type == BattleActionType.CAPTURE
                && !Objects.equals(observation.publicWorld().supplyDropRegion(), self.region())) {
            if (observation.publicWorld().supplyDropRegion() == null) {
                type = BattleActionType.SEARCH;
            } else {
                type = BattleActionType.MOVE;
                forcedRegion = nextStep(observation, self.region(), observation.publicWorld().supplyDropRegion());
            }
        }
        String region = forcedRegion == null ? decision.targetRegion() : forcedRegion;
        if (type == BattleActionType.MOVE && !adjacentRegions(observation, self.region()).contains(region)) {
            region = nextStep(observation, self.region(), region);
            if (region == null) {
                region = adjacentRegions(observation, self.region()).stream().findFirst().orElse(self.region());
            }
        }
        Long targetId = (type == BattleActionType.ATTACK || type == BattleActionType.REVIVE
                || type == BattleActionType.EXECUTE) && target != null
                ? target.playerId() : null;
        return new BattleRoyaleAction(actor.getPlayerId(), type, targetId, region);
    }

    private BattleRoyaleAction fallbackAction(BattleAgentContext context, BattlePlayerState actor) {
        BattleAgentObservation observation = context.observation();
        var self = observation.self();
        if (actor.isDowned()) {
            return new BattleRoyaleAction(actor.getPlayerId(), BattleActionType.SKIP, null, null);
        }
        ObservedBattlePlayer target = bestVisibleTarget(context, actor);
        ObservedBattlePlayer reviveTarget = self.medkits() > 0 ? bestDownedTarget(context, actor, true) : null;
        ObservedBattlePlayer executeTarget = bestDownedTarget(context, actor, false);
        BattleActionType type = reviveTarget != null ? BattleActionType.REVIVE
                : executeTarget != null ? BattleActionType.EXECUTE
                : self.health() < 45 && self.medkits() > 0 ? BattleActionType.HEAL
                : self.health() < 30 ? BattleActionType.REST
                : !observation.publicWorld().safeRegions().contains(self.region()) ? BattleActionType.MOVE
                : target != null && attackValue(context, actor, target) >= aggressionThreshold(actor) ? BattleActionType.ATTACK
                : Objects.equals(observation.publicWorld().supplyDropRegion(), self.region())
                        && observation.publicWorld().supplyDropClaimPlayerId() == null ? BattleActionType.CAPTURE
                : self.armor() < 20 && self.armed() ? BattleActionType.GUARD : BattleActionType.SEARCH;
        String targetRegion = null;
        if (type == BattleActionType.MOVE) {
            String destination = !observation.publicWorld().safeRegions().contains(self.region())
                    ? nearestRegion(observation, self.region(), observation.publicWorld().safeRegions())
                    : observation.publicWorld().supplyDropRegion();
            targetRegion = nextStep(observation, self.region(), destination);
            if (targetRegion == null) {
                targetRegion = adjacentRegions(observation, self.region()).stream().findFirst().orElse(self.region());
            }
        }
        Long targetId = type == BattleActionType.REVIVE ? reviveTarget.playerId()
                : type == BattleActionType.EXECUTE ? executeTarget.playerId()
                : type == BattleActionType.ATTACK && target != null ? target.playerId() : null;
        return new BattleRoyaleAction(actor.getPlayerId(), type, targetId, targetRegion);
    }

    private ObservedBattlePlayer visibleTarget(BattleAgentObservation observation, Long targetPlayerId) {
        if (targetPlayerId == null) return null;
        return observation.visiblePlayers().stream()
                .filter(player -> targetPlayerId.equals(player.playerId())).findFirst().orElse(null);
    }

    private ObservedBattlePlayer bestVisibleTarget(BattleAgentContext context, BattlePlayerState actor) {
        return context.observation().visiblePlayers().stream()
                .filter(target -> target.lifeStatus() == BattlePlayerLifeStatus.ALIVE)
                .max(Comparator.comparingInt(target -> attackValue(context, actor, target))).orElse(null);
    }

    private ObservedBattlePlayer bestDownedTarget(BattleAgentContext context, BattlePlayerState actor,
                                                   boolean forRevive) {
        return context.observation().visiblePlayers().stream()
                .filter(target -> target.lifeStatus() == BattlePlayerLifeStatus.DOWNED)
                .filter(target -> forRevive ? shouldRevive(context, target) : shouldExecute(context, actor, target))
                .max(Comparator.comparingInt(target -> downedTargetValue(context, target, forRevive)))
                .orElse(null);
    }

    private boolean shouldRevive(BattleAgentContext context, ObservedBattlePlayer target) {
        BattleRelationshipState relationship = context.relationships().get(target.playerId());
        boolean allied = context.social().alliances().stream()
                .anyMatch(alliance -> alliance.getStatus() == BattleAllianceStatus.ACTIVE
                        && alliance.includes(context.observation().self().playerId())
                        && alliance.includes(target.playerId()));
        int trust = relationship == null ? 50 : relationship.getTrust();
        int gratitude = relationship == null ? 0 : relationship.getGratitude();
        int resentment = relationship == null ? 0 : relationship.getResentment();
        return allied || trust + gratitude - resentment >= 65;
    }

    private boolean shouldExecute(BattleAgentContext context, BattlePlayerState actor,
                                  ObservedBattlePlayer target) {
        BattleRelationshipState relationship = context.relationships().get(target.playerId());
        int resentment = relationship == null ? 0 : relationship.getResentment();
        int suspicion = relationship == null ? 0 : relationship.getSuspicion();
        return resentment + suspicion >= 55 || Objects.equals(actor.getLastAttackerPlayerId(), target.playerId());
    }

    private int downedTargetValue(BattleAgentContext context, ObservedBattlePlayer target, boolean forRevive) {
        BattleRelationshipState relationship = context.relationships().get(target.playerId());
        if (relationship == null) return forRevive ? 50 : 0;
        return forRevive
                ? relationship.getTrust() + relationship.getGratitude() - relationship.getResentment()
                : relationship.getResentment() + relationship.getSuspicion() - relationship.getGratitude();
    }

    private int attackValue(BattleAgentContext context, BattlePlayerState actor, ObservedBattlePlayer target) {
        int value = 20 + switch (target.healthStatus()) {
            case HEALTHY -> 10;
            case INJURED -> 45;
            case CRITICAL -> 75;
        } + Math.min(15, context.observation().self().ammo() * 2)
                + context.observation().publicWorld().roundNo() * 3;
        if (target.obviousWeapon()) value -= 18;
        if (target.exposed()) value += 24;
        BattleRelationshipState relationship = context.relationships().get(target.playerId());
        if (relationship != null) {
            value += relationship.getResentment() / 4 + relationship.getSuspicion() / 8;
            value -= relationship.getFear() / 5 + relationship.getGratitude() / 5;
        }
        if (Objects.equals(actor.getLastAttackerPlayerId(), target.playerId())) value += 22;
        if (!context.observation().self().armed() || context.observation().self().ammo() <= 0) {
            value -= context.observation().publicWorld().roundNo() < 4 ? 25 : 5;
        }
        if (context.observation().self().health() < 50) value -= 20;
        return value;
    }

    private int aggressionThreshold(BattlePlayerState actor) {
        String risk = actor.getPersona() == null ? null : actor.getPersona().getRiskPreference();
        if ("BOLD".equalsIgnoreCase(risk)) return 38;
        if ("CAUTIOUS".equalsIgnoreCase(risk)) return 68;
        return 52;
    }

    private String nearestRegion(BattleAgentObservation observation, String origin, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        return candidates.stream().min(Comparator.comparingInt(region -> path(observation, origin, region).size()))
                .orElse(null);
    }

    private String nextStep(BattleAgentObservation observation, String origin, String destination) {
        List<String> path = path(observation, origin, destination);
        return path.size() < 2 ? null : path.get(1);
    }

    private List<String> path(BattleAgentObservation observation, String origin, String destination) {
        if (origin == null || destination == null) return List.of();
        if (origin.equals(destination)) return List.of(origin);
        ArrayDeque<String> queue = new ArrayDeque<>();
        Map<String, String> previous = new HashMap<>();
        queue.add(origin);
        previous.put(origin, null);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (String next : adjacentRegions(observation, current)) {
                if (previous.containsKey(next)) continue;
                previous.put(next, current);
                if (next.equals(destination)) {
                    ArrayList<String> result = new ArrayList<>();
                    for (String item = destination; item != null; item = previous.get(item)) result.add(item);
                    Collections.reverse(result);
                    return result;
                }
                queue.addLast(next);
            }
        }
        return List.of();
    }

    private List<String> adjacentRegions(BattleAgentObservation observation, String region) {
        return observation.publicWorld().topology().getOrDefault(region, List.of());
    }

    private EngineResult<BattleRoyaleGameState> executeWithDecision(BattleRoyaleGameState state,
                                                                     BattlePlayerState actor,
                                                                     BattleRoyaleAction action,
                                                                     BattleSocialDecision socialDecision,
                                                                     BattleCommunicationDecision communication,
                                                                     String summary,
                                                                     Integer confidence,
                                                                     String source,
                                                                     String fallbackCode) {
        return executeWithDecision(state, actor, action, socialDecision, communication, summary, confidence,
                source, fallbackCode, null, null);
    }

    private EngineResult<BattleRoyaleGameState> executeWithDecision(BattleRoyaleGameState state,
                                                                     BattlePlayerState actor,
                                                                     BattleRoyaleAction action,
                                                                     BattleSocialDecision socialDecision,
                                                                     BattleCommunicationDecision communication,
                                                                     String summary,
                                                                     Integer confidence,
                                                                     String source,
                                                                     String fallbackCode,
                                                                     String selectedCandidateId,
                                                                      String candidateResolution) {
        int decisionRound = state.getRoundNo();
        state.setSequenceNo(state.getSequenceNo() + 1);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action.type().name());
        payload.put("targetPlayerId", action.targetPlayerId());
        payload.put("targetRegion", action.targetRegion());
        payload.put("summary", summary == null ? "模型未提供决策摘要" : summary);
        payload.put("confidence", confidence == null ? 0 : confidence);
        payload.put("source", source);
        // Internal-only diagnostic fields. Public projection intentionally omits them.
        payload.put("socialAction", socialDecision == null ? null : socialDecision.type());
        payload.put("socialTargetPlayerId", socialDecision == null ? null : socialDecision.targetPlayerId());
        payload.put("socialReferenceId", socialDecision == null ? null : socialDecision.referenceId());
        payload.put("communicationChannel", communication == null ? null : communication.channel());
        payload.put("communicationTargetPlayerId", communication == null ? null : communication.targetPlayerId());
        if (fallbackCode != null) payload.put("fallbackCode", fallbackCode);
        if (selectedCandidateId != null) payload.put("selectedCandidateId", selectedCandidateId);
        if (candidateResolution != null) payload.put("candidateResolution", candidateResolution);
        Map<String, Object> publicPayload = new LinkedHashMap<>();
        publicPayload.put("action", action.type().name());
        publicPayload.put("targetPlayerId", action.targetPlayerId());
        publicPayload.put("targetRegion", action.targetRegion());
        publicPayload.put("summary", "智能体已选择并执行一个公开战术动作");
        publicPayload.put("confidence", confidence == null ? 0 : confidence);
        publicPayload.put("source", source);
        if (fallbackCode != null) publicPayload.put("fallbackCode", fallbackCode);
        if (action.type() == BattleActionType.HIDE) {
            publicPayload = new LinkedHashMap<>();
            publicPayload.put("action", action.type().name());
            publicPayload.put("confidence", confidence == null ? 0 : confidence);
            publicPayload.put("source", source);
            if (fallbackCode != null) publicPayload.put("fallbackCode", fallbackCode);
        }
        EngineResult<BattleRoyaleGameState> result = gameEngine.executeDeferredFinish(state, action);
        ArrayList<EngineEvent> events = new ArrayList<>();
        events.add(new EngineEvent("AI_DECISION", actor.getPlayerId(), payload, publicPayload));
        events.addAll(result.events());

        memorySystem.rememberAction(state, action, result.events(), false);
        if (action.type() == BattleActionType.ATTACK && action.targetPlayerId() != null) {
            int damage = result.events().stream().filter(event -> "BATTLE_ACTION_EXECUTED".equals(event.type()))
                    .map(event -> event.payload().get("damage"))
                    .filter(Number.class::isInstance).map(Number.class::cast).mapToInt(Number::intValue).findFirst().orElse(0);
            BattleBetrayalResult betrayal = betrayalDetector.detect(state, action, actor.getRegion(), damage);
            events.addAll(betrayal.events());
            if (betrayal.betrayed()) {
                appendRelationshipEvents(state, events, betrayal.relationshipChanges());
            } else {
                appendRelationshipEvent(state, events,
                        relationshipSystem.attacked(state, action.targetPlayerId(), action.actorPlayerId(), damage));
            }
        } else if (action.type() == BattleActionType.EXECUTE && action.targetPlayerId() != null) {
            rememberExecution(state, action, actor.getRegion());
            BattleBetrayalResult betrayal = betrayalDetector.detect(state, action, actor.getRegion(), 100);
            events.addAll(betrayal.events());
            appendRelationshipEvents(state, events, betrayal.relationshipChanges());
        } else if (action.type() == BattleActionType.REVIVE && action.targetPlayerId() != null) {
            memorySystem.rememberFact(state, action.targetPlayerId(), BattleMemoryType.PLAYER_REVIVED_ME,
                    action.actorPlayerId(), action.targetPlayerId(), "一名玩家救起了我", 100, actor.getRegion());
            memorySystem.rememberFact(state, action.actorPlayerId(), BattleMemoryType.I_REVIVED_PLAYER,
                    action.actorPlayerId(), action.targetPlayerId(), "我救起了一名倒地玩家", 95, actor.getRegion());
            appendRelationshipEvent(state, events,
                    relationshipSystem.revived(state, action.targetPlayerId(), action.actorPlayerId()));
        }

        BattleCommunicationResult communicationResult = communicationSystem.deliver(state, actor.getPlayerId(), communication);
        List<Long> informedPlayers = List.of();
        if (communicationResult.delivered()) {
            memorySystem.rememberMessage(state, communicationResult.message());
            events.add(communicationResult.event());
            informedPlayers = List.copyOf(communicationResult.message().getAudiencePlayerIds());
        } else if (communication != null) {
            memorySystem.rememberFact(state, actor.getPlayerId(), BattleMemoryType.COMMUNICATION_REJECTED,
                    actor.getPlayerId(), communication.targetPlayerId(),
                    "上一条消息未送达；请检查频道、目标和战术动作后的当前位置", 100, actor.getRegion());
        }
        BattleSocialActionResult socialResult = socialActionSystem.execute(
                state, actor.getPlayerId(), socialDecision, informedPlayers);
        if (socialDecision != null && socialResult.events().isEmpty()) {
            memorySystem.rememberFact(state, actor.getPlayerId(), BattleMemoryType.SOCIAL_ACTION_REJECTED,
                    actor.getPlayerId(), socialDecision.targetPlayerId(),
                    "社会动作 " + socialDecision.type() + " 未执行；可能因移动后的距离、资源、期限或引用失效",
                    100, actor.getRegion());
            state.setSequenceNo(state.getSequenceNo() + 1);
            events.add(new EngineEvent("BATTLE_SOCIAL_ACTION_REJECTED", actor.getPlayerId(),
                    Map.of("socialAction", socialDecision.type(), "reason", "EXECUTION_PRECONDITION_CHANGED"),
                    Map.of("summary", "一项社会动作未满足执行条件")));
        }
        events.addAll(socialResult.events());
        appendRelationshipEvents(state, events, socialResult.relationshipChanges());
        String goalTrigger = action.type().name();
        if (socialResult.events().stream().anyMatch(e -> "BATTLE_ALLIANCE_FORMED".equals(e.type()))) goalTrigger = "ALLIANCE_FORMED";
        events.addAll(hiddenGoalSystem.evaluate(state, actor.getPlayerId(), goalTrigger));
        socialResult.events().stream()
                .filter(e -> "BATTLE_INFORMATION_TRADE_COMPLETED".equals(e.type()))
                .map(e -> e.payload().get("sellerPlayerId"))
                .filter(Number.class::isInstance).map(Number.class::cast).map(Number::longValue).distinct()
                .forEach(sellerId -> events.addAll(hiddenGoalSystem.evaluate(
                        state, sellerId, "INFORMATION_TRADE_COMPLETED")));
        int roundEventStart = events.size();
        List<EngineEvent> roundEvents = new ArrayList<>();
        gameEngine.completeRoundIfNeeded(state, roundEvents);
        events.addAll(roundEvents);
        memorySystem.rememberPublicEvents(state, roundEvents);
        BattleSocialActionResult roundResult = socialActionSystem.evaluateRoundState(state);
        events.addAll(roundResult.events());
        appendRelationshipEvents(state, events, roundResult.relationshipChanges());
        gameEngine.finishIfNeeded(state, events);
        for (int i = 0; i < events.size(); i++) {
            EngineEvent event = events.get(i);
            int eventRound = i < roundEventStart ? decisionRound : state.getRoundNo();
            Map<String, Object> eventPayload = new LinkedHashMap<>(event.payload());
            Map<String, Object> eventPublicPayload = new LinkedHashMap<>(event.publicPayload());
            eventPayload.put("roundNo", eventRound);
            eventPublicPayload.put("roundNo", eventRound);
            events.set(i, new EngineEvent(event.type(), event.actorPlayerId(), eventPayload, eventPublicPayload));
        }
        return new EngineResult<>(state, events);
    }

    private void rememberExecution(BattleRoyaleGameState state, BattleRoyaleAction action, String region) {
        memorySystem.rememberFact(state, action.actorPlayerId(), BattleMemoryType.PLAYER_EXECUTED,
                action.actorPlayerId(), action.targetPlayerId(), "我处决了一名倒地玩家", 95, region);
        memorySystem.rememberFact(state, action.targetPlayerId(), BattleMemoryType.PLAYER_EXECUTED,
                action.actorPlayerId(), action.targetPlayerId(), "我在倒地时被处决", 100, region);
        state.contendingPlayers().stream()
                .filter(player -> !Objects.equals(player.getPlayerId(), action.actorPlayerId()))
                .filter(player -> Objects.equals(player.getRegion(), region))
                .forEach(player -> memorySystem.rememberFact(state, player.getPlayerId(),
                        BattleMemoryType.PLAYER_EXECUTED, action.actorPlayerId(), action.targetPlayerId(),
                        "我目击了一次倒地处决", 90, region));
    }

    private void appendRelationshipEvents(BattleRoyaleGameState state, List<EngineEvent> events,
                                          List<BattleRelationshipChange> changes) {
        for (BattleRelationshipChange change : changes) appendRelationshipEvent(state, events, change);
    }

    private void appendRelationshipEvent(BattleRoyaleGameState state, List<EngineEvent> events,
                                         BattleRelationshipChange change) {
        state.setSequenceNo(state.getSequenceNo() + 1);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("observerPlayerId", change.observerPlayerId());
        payload.put("subjectPlayerId", change.subjectPlayerId());
        payload.put("reason", change.reason());
        payload.put("before", change.before());
        payload.put("after", change.after());
        Map<String, Object> publicPayload = Map.of(
                "observerPlayerId", change.observerPlayerId(),
                "subjectPlayerId", change.subjectPlayerId(),
                "reason", change.reason(),
                "summary", "关系发生明显变化");
        events.add(new EngineEvent("BATTLE_RELATIONSHIP_CHANGED", change.observerPlayerId(), payload, publicPayload));
    }
}
