package com.zn.agentarena.modules.battle.simulation.trace;

import com.zn.agentarena.modules.battle.agent.BattleAgentContext;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.memory.BattleMemoryEntry;
import com.zn.agentarena.modules.battle.objective.BattleCooperativeObjectiveStatus;
import com.zn.agentarena.modules.battle.promise.BattlePromiseStatus;
import com.zn.agentarena.modules.battle.social.BattleRelationshipState;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import java.util.*;
import java.util.stream.Collectors;

public final class BattleDecisionTraceFactory {
    private static final int MEMORY_SUMMARY_LIMIT = 5;

    public BattleDecisionTraceDraft capture(BattleAgentContext context, BattleRoyaleGameState state,
                                            BattlePlayerState actor, long seed) {
        var observation = context.observation();
        var self = observation.self();
        List<Long> visible = observation.visiblePlayers().stream().map(p -> p.playerId()).toList();
        List<Long> downed = observation.visiblePlayers().stream()
                .filter(p -> p.lifeStatus() == BattlePlayerLifeStatus.DOWNED).map(p -> p.playerId()).toList();
        Map<String,Integer> resources = Map.of("health", self.health(), "food", self.food(), "water", self.water(),
                "ammo", self.ammo(), "armor", self.armor(), "medkits", self.medkits(),
                "armed", self.armed() ? 1 : 0,
                "visibleActiveNonAllies", (int) observation.visiblePlayers().stream()
                    .filter(p -> p.lifeStatus() == BattlePlayerLifeStatus.ALIVE)
                    .filter(p -> context.social().alliances().stream().noneMatch(a ->
                        a.getStatus() == com.zn.agentarena.modules.battle.alliance.BattleAllianceStatus.ACTIVE
                        && a.includes(actor.getPlayerId()) && a.includes(p.playerId())))
                    .count());

        List<String> objectives = context.social().cooperativeObjectives().stream()
                .map(o -> o.getObjectiveId() + "@" + o.getRegion() + ":" + o.getStatus() + ":deadline=" + o.getDeadlineRound())
                .toList();
        List<String> proposals = new ArrayList<>();
        context.social().alliances().stream().filter(a -> "PROPOSED".equals(String.valueOf(a.getStatus())))
                .forEach(a -> proposals.add("ALLIANCE:" + a.getAllianceId()));
        context.social().promises().stream().filter(p -> p.getStatus() == BattlePromiseStatus.PROPOSED)
                .forEach(p -> proposals.add("PROMISE:" + p.getPromiseId()));
        context.social().tradeProposals().stream().filter(t -> "PROPOSED".equals(String.valueOf(t.getStatus())))
                .forEach(t -> proposals.add("TRADE:" + t.getTradeId()));
        context.social().informationTradeProposals().stream().filter(t -> "PROPOSED".equals(String.valueOf(t.getStatus())))
                .forEach(t -> proposals.add("INFORMATION_TRADE:" + t.getTradeId()));

        List<BattleMemoryEntry> injected = context.memories().stream()
                .sorted(Comparator.comparingInt(BattleMemoryEntry::getImportance).reversed()
                        .thenComparing(Comparator.comparingLong(BattleMemoryEntry::getSequenceNo).reversed()))
                .limit(30).toList();
        List<String> memoryTypes = injected.stream().map(m -> String.valueOf(m.getType())).distinct().limit(6).toList();
        List<String> memorySummaries = injected.stream().filter(m -> m.getImportance() >= 70)
                .limit(MEMORY_SUMMARY_LIMIT).map(BattleMemoryEntry::getSummary).filter(Objects::nonNull).toList();
        List<String> rumors = context.social().rumors().stream()
                .map(r -> "CLAIM:" + r.getRumorId() + ":" + r.getClaimType() + ":confidence=" + r.getConfidence()
                        + ":generation=" + r.getGeneration() + ":subject=" + r.getSubjectPlayerId()).toList();

        BattleAgentKnownState known = new BattleAgentKnownState(visible, downed, resources, objectives,
                List.copyOf(proposals),
                context.social().alliances().stream().filter(a -> "ACTIVE".equals(String.valueOf(a.getStatus())))
                        .map(a -> a.getAllianceId()).toList(),
                context.social().promises().stream().filter(p -> p.getStatus() == BattlePromiseStatus.ACCEPTED)
                        .map(p -> p.getPromiseId()).toList(),
                context.social().promises().stream().filter(p -> p.getStatus() == BattlePromiseStatus.PROPOSED)
                        .map(p -> p.getPromiseId()).toList(),
                context.social().tradeProposals().stream().filter(t -> "PROPOSED".equals(String.valueOf(t.getStatus())))
                        .map(t -> t.getTradeId()).toList(),
                context.social().informationTradeProposals().stream().filter(t -> "PROPOSED".equals(String.valueOf(t.getStatus())))
                        .map(t -> t.getTradeId()).toList(), rumors,
                context.social().hiddenGoals().stream().map(g -> g.getGoalId() + ":" + g.getType() + ":"
                        + g.getStatus() + ":" + g.getProgress() + "/" + g.getTargetValue()).toList(),
                injected.size(), memoryTypes, memorySummaries, Map.of());

        Map<Long,BattleTraceRelationship> relationships = context.relationships().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> relationship(e.getValue()), (a,b) -> a, LinkedHashMap::new));
        BattleServerTruth truth = truth(context, state, actor);
        String persona = actor.getPersona() == null ? "UNKNOWN" : actor.getPersona().getRiskPreference();
        return new BattleDecisionTraceDraft(state.getGameId(), seed, state.getRoundNo(), actor.getPlayerId(), persona,
                actor.effectiveLifeStatus(), actor.getRegion(), known, Map.copyOf(relationships), truth);
    }

    public BattleAgentDecisionTrace finish(BattleDecisionTraceDraft draft, List<EngineEvent> events) {
        EngineEvent event = events.stream().filter(e -> "AI_DECISION".equals(e.type())).findFirst()
                .orElseThrow(() -> new IllegalStateException("AI_DECISION missing from traced turn"));
        Map<String,Object> p = event.payload();
        BattleTraceDecision decision = new BattleTraceDecision(text(p.get("action")), number(p.get("targetPlayerId")),
                text(p.get("targetRegion")), text(p.get("socialAction")), number(p.get("socialTargetPlayerId")),
                text(p.get("socialReferenceId")), text(p.get("communicationChannel")),
                number(p.get("communicationTargetPlayerId")), text(p.get("summary")), intValue(p.get("confidence")),
                text(p.get("source")));
        Set<Long> involved = new LinkedHashSet<>();
        if (decision.targetPlayerId() != null) involved.add(decision.targetPlayerId());
        if (decision.socialTargetPlayerId() != null) involved.add(decision.socialTargetPlayerId());
        if (decision.communicationTargetPlayerId() != null) involved.add(decision.communicationTargetPlayerId());
        Map<Long,BattleTraceRelationship> filtered = draft.relationshipCandidates().entrySet().stream()
                .filter(e -> involved.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a,b) -> a, LinkedHashMap::new));
        BattleAgentKnownState k = draft.knownStateWithoutRelationships();
        BattleAgentKnownState known = new BattleAgentKnownState(k.visiblePlayerIds(), k.visibleDownedPlayerIds(),
                k.selfResources(), k.publicObjectives(), k.socialProposals(), k.activeAlliances(), k.acceptedPromises(),
                k.pendingPromises(), k.pendingTrades(), k.informationTradeOpportunities(), k.rumors(), k.hiddenGoals(),
                k.injectedMemoryCount(), k.importantMemoryTypes(), k.relevantMemorySummaries(), Map.copyOf(filtered));
        return new BattleAgentDecisionTrace(draft.simulationId(), draft.seed(), draft.round(), draft.actorPlayerId(),
                draft.persona(), draft.lifeStatus(), draft.region(), known, draft.serverTruth(), decision);
    }

    private BattleServerTruth truth(BattleAgentContext context, BattleRoyaleGameState state, BattlePlayerState actor) {
        List<BattlePromiseOpportunityTruth> promises = state.getPromises().stream()
                .filter(p -> p.getStatus() == BattlePromiseStatus.ACCEPTED)
                .filter(p -> Objects.equals(p.getPromiserPlayerId(), actor.getPlayerId()))
                .map(p -> {
                    BattlePlayerState promisee = state.findPlayer(p.getPromiseePlayerId());
                    boolean has = BattleResourceInventory.amount(actor, p.getResourceType()) >= p.getAmount();
                    boolean same = promisee != null && Objects.equals(actor.getRegion(), promisee.getRegion());
                    boolean active = actor.isActive() && promisee != null && promisee.isActive();
                    return new BattlePromiseOpportunityTruth(p.getPromiseId(), p.getPromiseePlayerId(), p.getDeadlineRound(),
                            actor.isActive(), promisee != null && promisee.isActive(), has, same,
                            active && has && same && state.getRoundNo() <= p.getDeadlineRound());
                }).toList();
        List<BattleObjectiveOpportunityTruth> objectives = state.getCooperativeObjectives().stream()
                .filter(o -> o.getStatus() == BattleCooperativeObjectiveStatus.ACTIVE)
                .map(o -> {
                    int required = o.getRequired().values().stream().mapToInt(Integer::intValue).sum();
                    int contributed = o.getContributed().values().stream().mapToInt(Integer::intValue).sum();
                    boolean has = o.getRequired().entrySet().stream().anyMatch(e ->
                            o.getContributed().getOrDefault(e.getKey(), 0) < e.getValue()
                                    && BattleResourceInventory.amount(actor, e.getKey()) > 0);
                    boolean known = context.social().cooperativeObjectives().stream()
                            .anyMatch(x -> Objects.equals(x.getObjectiveId(), o.getObjectiveId()));
                    boolean inRegion = Objects.equals(actor.getRegion(), o.getRegion());
                    return new BattleObjectiveOpportunityTruth(o.getObjectiveId(), o.getRegion(), o.getDeadlineRound(),
                            known, inRegion, has, actor.isActive() && known && inRegion && has, contributed, required);
                }).toList();
        List<Long> downed = context.observation().visiblePlayers().stream()
                .filter(p -> p.lifeStatus() == BattlePlayerLifeStatus.DOWNED).map(p -> p.playerId()).toList();
        List<Long> revive = actor.getMedkits() <= 0 || !actor.isActive() ? List.of() : downed;
        List<String> rumorIds = context.social().rumors().stream().map(r -> r.getRumorId()).toList();
        List<Long> buyers = context.observation().visiblePlayers().stream()
                .filter(p -> p.lifeStatus() == BattlePlayerLifeStatus.ALIVE).map(p -> p.playerId()).toList();
        return new BattleServerTruth(promises, objectives,
                new BattleMedicOpportunityTruth(downed, actor.getMedkits() > 0, revive),
                new BattleInformationBrokerOpportunityTruth(rumorIds, buyers, !rumorIds.isEmpty(),
                        !rumorIds.isEmpty() && !buyers.isEmpty()), state.alivePlayers().size(),
                actor.getFood() + actor.getWater() <= 4, Objects.equals(actor.getRegion(), state.getSupplyDropRegion())
                        && !context.observation().visiblePlayers().isEmpty(),
                state.getRoundNo() >= Math.max(1, (int)Math.ceil(state.getMaxRounds() * 0.7)));
    }

    private BattleTraceRelationship relationship(BattleRelationshipState r) {
        return new BattleTraceRelationship(r.getTrust(), r.getFear(), r.getGratitude(), r.getResentment(),
                r.getSuspicion(), r.getPerceivedStrength());
    }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private Long number(Object value) { return value instanceof Number n ? n.longValue() : null; }
    private int intValue(Object value) { return value instanceof Number n ? n.intValue() : 0; }
}
