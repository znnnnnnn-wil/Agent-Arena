package com.zn.agentarena.modules.battle.simulation;

import com.zn.agentarena.modules.agent.domain.AgentPersonaSnapshot;
import com.zn.agentarena.modules.battle.agent.*;
import com.zn.agentarena.modules.battle.alliance.*;
import com.zn.agentarena.modules.battle.communication.BattleCommunicationDecision;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.information.*;
import com.zn.agentarena.modules.battle.objective.BattleObjectiveContributionDecision;
import com.zn.agentarena.modules.battle.perception.ObservedBattlePlayer;
import com.zn.agentarena.modules.battle.promise.*;
import com.zn.agentarena.modules.battle.social.BattleSocialDecision;
import com.zn.agentarena.modules.battle.simulation.trace.*;
import com.zn.agentarena.modules.battle.trade.*;
import com.zn.agentarena.modules.game.engine.*;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import java.util.*;
import java.util.stream.IntStream;
import java.util.concurrent.ForkJoinPool;

/** 不调用 UI 或在线模型、复用生产执行链的确定性批量仿真器。 */
public class BattleSimulationRunner {
    private static final List<String> REGIONS = List.of("A", "B", "C", "D", "E");
    private static final List<String> RISKS = List.of("BOLD", "BALANCED", "CAUTIOUS");
    private final BattleAgentContextBuilder contextBuilder;
    private final BattleAiGameRunner runner;
    private final ModelRuntimeConfig llmConfig;
    private final BattleStateInvariantChecker invariantChecker = new BattleStateInvariantChecker();
    private final BattleDecisionTraceFactory traceFactory = new BattleDecisionTraceFactory();
    private final com.zn.agentarena.modules.battle.engine.BattleRoyaleGameEngine gameEngine =
            new com.zn.agentarena.modules.battle.engine.BattleRoyaleGameEngine();

    public BattleSimulationRunner() { this(null, null); }

    /** LLM_SAMPLE 只能通过此显式构造启用，避免默认批量任务意外产生费用。 */
    public BattleSimulationRunner(ModelGateway modelGateway, ModelRuntimeConfig llmConfig) {
        this.contextBuilder = new BattleAgentContextBuilder();
        this.runner = new BattleAiGameRunner(modelGateway, contextBuilder);
        this.llmConfig = llmConfig;
    }

    public List<BattleSimulationResult> run(BattleSimulationConfig config) {
        if (config.mode() == BattleSimulationMode.LLM_SAMPLE && llmConfig == null) {
            throw new IllegalArgumentException("LLM_SAMPLE 必须显式配置 ModelGateway/ModelRuntimeConfig");
        }
        if (config.mode() == BattleSimulationMode.FAST_SIMULATION && config.games() >= 100) {
            // Bound concurrency because decision traces are memory-heavy. Encounter order preserves seed order.
            ForkJoinPool pool = new ForkJoinPool(2);
            try {
                return pool.submit(() -> IntStream.range(0, config.games()).parallel()
                        .mapToObj(i -> runOne(config, config.firstSeed() + i)).toList()).join();
            } finally {
                pool.shutdown();
            }
        }
        List<BattleSimulationResult> results = new ArrayList<>(config.games());
        for (int i = 0; i < config.games(); i++) results.add(runOne(config, config.firstSeed() + i));
        return results;
    }

    public BattleSimulationResult runOne(BattleSimulationConfig config, long seed) {
        BattleRoyaleGameState state = initialState(config, seed);
        List<EngineEvent> events = new ArrayList<>();
        Map<Integer,Integer> aliveCurve = new LinkedHashMap<>();
        Map<Integer,BattlePromptSample> promptSamples = new LinkedHashMap<>();
        List<BattleAgentDecisionTrace> decisionTraces = new ArrayList<>();
        aliveCurve.put(state.getRoundNo(), state.contendingPlayers().size());
        int safety = config.maxRounds() * config.players() * 4;
        String failure = null;
        try {
            while (!finished(state) && safety-- > 0) {
                BattlePlayerState actor = gameEngine.currentTurnPlayer(state);
                if (actor == null) break;
                BattleAgentContext context = runner.createContext(state, actor);
                BattleDecisionTraceDraft traceDraft = traceFactory.capture(context, state, actor, seed);
                if ((state.getRoundNo() == 1 || state.getRoundNo() == 10 || state.getRoundNo() == 20
                        || state.getRoundNo() == config.maxRounds()) && !promptSamples.containsKey(state.getRoundNo())) {
                    String prompt = runner.buildProductionPrompt(state, actor, context);
                    promptSamples.put(state.getRoundNo(), new BattlePromptSample(state.getRoundNo(), prompt.length(),
                            (int)Math.ceil(prompt.length()/3.5), context.memories().size(), context.social().rumors().size(),
                            context.receivedMessages().size(), context.relationships().size()));
                }
                EngineResult<BattleRoyaleGameState> result;
                if (config.mode() == BattleSimulationMode.LLM_SAMPLE) {
                    result = runner.runNextTurn(state, actor, llmConfig);
                } else {
                    TurnDecision decision = decide(context, actor, seed, state.getSequenceNo());
                    result = runner.runPreparedTurn(state, actor,
                            decision.action(), decision.social(), decision.communication());
                }
                for (EngineEvent event : result.events()) {
                    Map<String,Object> payload = new LinkedHashMap<>(event.payload());
                    payload.putIfAbsent("simulationRound", state.getRoundNo());
                    events.add(new EngineEvent(event.type(), event.actorPlayerId(), payload, event.publicPayload()));
                }
                decisionTraces.add(traceFactory.finish(traceDraft, result.events()));
                aliveCurve.put(state.getRoundNo(), state.contendingPlayers().size());
                if (config.invariantChecks()) invariantChecker.verify(state, seed);
            }
            if (safety <= 0 && !finished(state)) failure = "TURN_SAFETY_LIMIT";
        } catch (RuntimeException exception) {
            failure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
        if (!promptSamples.containsKey(config.maxRounds()) && state.getRoundNo() >= config.maxRounds()) {
            BattlePlayerState samplePlayer = state.contendingPlayers().stream().findFirst()
                    .orElse(state.getPlayers().stream().findFirst().orElse(null));
            if (samplePlayer != null) {
                BattleAgentContext context = runner.createContext(state, samplePlayer);
                String prompt = runner.buildProductionPrompt(state, samplePlayer, context);
                promptSamples.put(config.maxRounds(), new BattlePromptSample(config.maxRounds(), prompt.length(),
                        (int)Math.ceil(prompt.length()/3.5), context.memories().size(), context.social().rumors().size(),
                        context.receivedMessages().size(), context.relationships().size()));
            }
        }
        String termination = failure != null ? "ABNORMAL"
                : state.contendingPlayers().size() <= 1 ? "LAST_CONTENDER"
                : state.alivePlayers().isEmpty() ? "NO_ALIVE_PLAYER"
                : state.getRoundNo() >= state.getMaxRounds() ? "MAX_ROUNDS" : "NO_TURNS";
        return new BattleSimulationResult(seed, state.getWinnerPlayerId(), state.getRoundNo(), termination,
                failure != null, failure, state, List.copyOf(events), Map.copyOf(aliveCurve), Map.copyOf(promptSamples),
                List.copyOf(decisionTraces));
    }

    private TurnDecision decide(BattleAgentContext context, BattlePlayerState actor, long seed, long sequence) {
        SplittableRandom random = new SplittableRandom(seed ^ (sequence * 0x9E3779B97F4A7C15L) ^ actor.getPlayerId());
        BattleSocialDecision social = social(context, actor, random);
        BattleRoyaleAction action = tactical(context, actor, random);
        if (social != null && "FULFILL_PROMISE".equals(social.type())
                && action.type() == BattleActionType.MOVE) {
            action = action(actor, BattleActionType.GUARD, null, null);
        }
        BattleCommunicationDecision communication = communication(context, random);
        if (social != null && "SHARE_RUMOR".equals(social.type())) {
            communication = new BattleCommunicationDecision("LOCAL", null, "转述一条未经证实的消息");
        }
        return new TurnDecision(action, social, communication);
    }

    private BattleRoyaleAction tactical(BattleAgentContext context, BattlePlayerState actor, SplittableRandom random) {
        var self = context.observation().self();
        if (actor.isDowned()) return action(actor, BattleActionType.SKIP, null, null);
        List<ObservedBattlePlayer> downed = context.observation().visiblePlayers().stream()
                .filter(p -> p.lifeStatus() == BattlePlayerLifeStatus.DOWNED).toList();
        if (!downed.isEmpty() && self.medkits() > 0 && random.nextInt(100) < 42) {
            return action(actor, BattleActionType.REVIVE, downed.get(random.nextInt(downed.size())).playerId(), null);
        }
        if (!downed.isEmpty() && random.nextInt(100) < 22) {
            return action(actor, BattleActionType.EXECUTE, downed.get(random.nextInt(downed.size())).playerId(), null);
        }
        if (self.health() < 48 && self.medkits() > 0) return action(actor, BattleActionType.HEAL, null, null);
        List<ObservedBattlePlayer> enemies = context.observation().visiblePlayers().stream()
                .filter(p -> p.lifeStatus() == BattlePlayerLifeStatus.ALIVE).toList();
        int aggression = "BOLD".equalsIgnoreCase(actor.getPersona().getRiskPreference()) ? 48
                : "CAUTIOUS".equalsIgnoreCase(actor.getPersona().getRiskPreference()) ? 20 : 34;
        if (!enemies.isEmpty() && random.nextInt(100) < aggression) {
            return action(actor, BattleActionType.ATTACK, enemies.get(random.nextInt(enemies.size())).playerId(), null);
        }
        if (Objects.equals(context.observation().publicWorld().supplyDropRegion(), self.region())
                && context.observation().publicWorld().supplyDropClaimPlayerId() == null
                && random.nextInt(100) < 45) {
            return action(actor, BattleActionType.CAPTURE, null, null);
        }
        if (!context.observation().publicWorld().safeRegions().contains(self.region())) {
            String next = adjacentTowardSafe(context, self.region(), random);
            if (next != null) return action(actor, BattleActionType.MOVE, null, next);
        }
        int roll = random.nextInt(100);
        if (roll < 58) return action(actor, BattleActionType.SEARCH, null, null);
        if (roll < 70) return action(actor, BattleActionType.GUARD, null, null);
        if (roll < 80) return action(actor, BattleActionType.REST, null, null);
        if (roll < 88) return action(actor, BattleActionType.HIDE, null, null);
        List<String> adjacent = context.observation().publicWorld().topology().getOrDefault(self.region(), List.of());
        return adjacent.isEmpty() ? action(actor, BattleActionType.SKIP, null, null)
                : action(actor, BattleActionType.MOVE, null, adjacent.get(random.nextInt(adjacent.size())));
    }

    private BattleSocialDecision social(BattleAgentContext c, BattlePlayerState actor, SplittableRandom random) {
        if (actor.isDowned()) return null;
        Long id = actor.getPlayerId();
        var acceptedPromise = c.social().promises().stream().filter(p -> p.getStatus() == BattlePromiseStatus.ACCEPTED
                && Objects.equals(p.getPromiserPlayerId(), id))
                .filter(p -> c.observation().visiblePlayers().stream()
                        .anyMatch(v -> Objects.equals(v.playerId(), p.getPromiseePlayerId())
                                && v.lifeStatus() == BattlePlayerLifeStatus.ALIVE))
                .filter(p -> BattleResourceInventory.amount(actor, p.getResourceType())
                        >= p.getAmount() + BattleRoyaleRules.METABOLISM_COST)
                .findFirst().orElse(null);
        if (acceptedPromise != null && random.nextInt(100) < 90) return social("FULFILL_PROMISE", acceptedPromise.getPromiseePlayerId(), acceptedPromise.getPromiseId(), null, null, null, null);
        var alliance = c.social().alliances().stream().filter(a -> a.getStatus() == BattleAllianceStatus.PROPOSED
                && Objects.equals(a.getMemberBPlayerId(), id)).findFirst().orElse(null);
        if (alliance != null && random.nextInt(100) < 68) return social("ACCEPT_ALLIANCE", alliance.getProposerPlayerId(), alliance.getAllianceId(), null, null, null, null);
        var promise = c.social().promises().stream().filter(p -> p.getStatus() == BattlePromiseStatus.PROPOSED
                && Objects.equals(p.getPromiseePlayerId(), id)).findFirst().orElse(null);
        if (promise != null && random.nextInt(100) < 60) return social("ACCEPT_PROMISE", promise.getPromiserPlayerId(), promise.getPromiseId(), null, null, null, null);
        var trade = c.social().tradeProposals().stream().filter(t -> t.getStatus() == BattleTradeStatus.PROPOSED
                && Objects.equals(t.getTargetPlayerId(), id)).findFirst().orElse(null);
        if (trade != null && random.nextInt(100) < 58) return social("ACCEPT_TRADE", trade.getProposerPlayerId(), trade.getTradeId(), null, null, null, null);
        var info = c.social().informationTradeProposals().stream().filter(t -> t.getStatus() == BattleInformationTradeStatus.PROPOSED
                && Objects.equals(t.getBuyerPlayerId(), id)).findFirst().orElse(null);
        if (info != null && random.nextInt(100) < 52) return social("ACCEPT_INFORMATION_TRADE", info.getSellerPlayerId(), info.getTradeId(), null, null, null, null);
        var activeAlliance = c.social().alliances().stream().filter(a -> a.getStatus() == BattleAllianceStatus.ACTIVE && a.includes(id)).findFirst().orElse(null);
        if (activeAlliance != null && random.nextInt(100) < 3) return social("LEAVE_ALLIANCE", activeAlliance.otherMember(id), activeAlliance.getAllianceId(), null, null, null, null);
        var objective = c.social().cooperativeObjectives().stream().filter(o -> "ACTIVE".equals(String.valueOf(o.getStatus())) && Objects.equals(o.getRegion(), c.observation().self().region())).findFirst().orElse(null);
        if (objective != null && random.nextInt(100) < 28) {
            String resource = c.observation().self().food() >= c.observation().self().water() ? "FOOD" : "WATER";
            return new BattleSocialDecision("CONTRIBUTE_OBJECTIVE", null, null, null, null,
                    new BattleObjectiveContributionDecision(objective.getObjectiveId(), resource, 1), null, null);
        }
        List<ObservedBattlePlayer> visible = c.observation().visiblePlayers().stream().filter(p -> p.lifeStatus() == BattlePlayerLifeStatus.ALIVE).toList();
        if (visible.isEmpty()) return maybeRumor(c, random);
        Long target = visible.get(random.nextInt(visible.size())).playerId();
        boolean hasOpenPromise = c.social().promises().stream()
                .anyMatch(p -> Objects.equals(p.getPromiserPlayerId(), id)
                        && (p.getStatus() == BattlePromiseStatus.PROPOSED
                        || p.getStatus() == BattlePromiseStatus.ACCEPTED));
        int roll = random.nextInt(100);
        if (roll < 13 && activeAlliance == null) return social("PROPOSE_ALLIANCE", target, null, null, null, null, null);
        if (roll < 20 && !hasOpenPromise) return social("PROPOSE_PROMISE", target, null,
                new BattlePromiseDecision("GIVE_RESOURCE", "FOOD", 1, Math.min(c.observation().publicWorld().maxRounds(), c.observation().publicWorld().roundNo()+5)), null, null, null);
        if (roll < 29) return social("PROPOSE_TRADE", target, null, null,
                new BattleTradeDecision(List.of(new BattleTradeItemDecision("FOOD",1)), List.of(new BattleTradeItemDecision("WATER",1))), null, null);
        if (roll < 36 && !c.social().rumors().isEmpty()) {
            var rumor = c.social().rumors().get(random.nextInt(c.social().rumors().size()));
            return new BattleSocialDecision("PROPOSE_INFORMATION_TRADE", target, null, null, null, null,
                    new BattleInformationTradeDecision(rumor.getRumorId(), "FOOD", 1), null);
        }
        return maybeRumor(c, random);
    }

    private BattleSocialDecision maybeRumor(BattleAgentContext c, SplittableRandom random) {
        if (random.nextInt(100) >= 12) return null;
        if (!c.social().rumors().isEmpty() && random.nextBoolean()) {
            var rumor = c.social().rumors().get(random.nextInt(c.social().rumors().size()));
            return new BattleSocialDecision("SHARE_RUMOR", null, null, null, null, null, null,
                    new BattleRumorDecision(rumor.getRumorId(), null, null, null, null));
        }
        return new BattleSocialDecision("SHARE_RUMOR", null, null, null, null, null, null,
                new BattleRumorDecision(null, null, "RESOURCE_CACHE", "某个区域可能仍有补给", 55));
    }

    private BattleCommunicationDecision communication(BattleAgentContext c, SplittableRandom random) {
        if (random.nextInt(100) >= 24) return null;
        int channel = random.nextInt(100);
        if (channel < 34 && !c.observation().visiblePlayers().isEmpty()) {
            Long target = c.observation().visiblePlayers().get(random.nextInt(c.observation().visiblePlayers().size())).playerId();
            return new BattleCommunicationDecision("PRIVATE", target, "我们可以暂时交换信息");
        }
        return new BattleCommunicationDecision(channel < 72 ? "LOCAL" : "PUBLIC", null, "报告当前局势，但内容未获系统验证");
    }

    private String adjacentTowardSafe(BattleAgentContext c, String region, SplittableRandom random) {
        List<String> adjacent = c.observation().publicWorld().topology().getOrDefault(region, List.of());
        List<String> safe = adjacent.stream().filter(c.observation().publicWorld().safeRegions()::contains).toList();
        List<String> choices = safe.isEmpty() ? adjacent : safe;
        return choices.isEmpty() ? null : choices.get(random.nextInt(choices.size()));
    }

    private BattleRoyaleGameState initialState(BattleSimulationConfig config, long seed) {
        List<BattlePlayerState> players = new ArrayList<>();
        List<String> spawns = BattleRoyaleRules.spawnAssignments(REGIONS, config.players(), seed);
        List<String> ringOrder = BattleRoyaleRules.ringOrder(REGIONS, seed);
        for (int i=0;i<config.players();i++) {
            String risk = RISKS.get(i % RISKS.size());
            AgentPersonaSnapshot persona = AgentPersonaSnapshot.builder().profileId((long)i+1).code("SIM_"+risk)
                    .name(risk).riskPreference(risk).traits(List.of(risk)).behaviorGuidance("deterministic simulation persona").builtIn(true).build();
            players.add(BattlePlayerState.builder().playerId((long)i+1).displayName("P"+(i+1)).persona(persona)
                    .region(spawns.get(i)).health(100).food(7).water(7).alive(true)
                    .lifeStatus(BattlePlayerLifeStatus.ALIVE).build());
        }
        BattleRoyaleGameState state = BattleRoyaleGameState.builder().gameId(seed).roundNo(1).maxRounds(config.maxRounds())
                .randomSeed(seed).regions(new ArrayList<>(REGIONS)).safeRegion(ringOrder.get(0)).safeRegions(new ArrayList<>(REGIONS))
                .ringOrder(new ArrayList<>(ringOrder))
                .ringStage(1).players(players).build();
        runner.createContext(state, players.get(0));
        return state;
    }

    private boolean finished(BattleRoyaleGameState state) { return state.getWinnerPlayerId()!=null || state.contendingPlayers().size()<=1 || state.alivePlayers().isEmpty() || state.getRoundNo()>=state.getMaxRounds(); }
    private BattleRoyaleAction action(BattlePlayerState actor, BattleActionType type, Long target, String region) { return new BattleRoyaleAction(actor.getPlayerId(),type,target,region); }
    private BattleSocialDecision social(String type,Long target,String ref,BattlePromiseDecision promise,BattleTradeDecision trade,BattleInformationTradeDecision info,BattleRumorDecision rumor){return new BattleSocialDecision(type,target,ref,promise,trade,null,info,rumor);}
    private record TurnDecision(BattleRoyaleAction action, BattleSocialDecision social, BattleCommunicationDecision communication) {}
}
