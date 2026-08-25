package com.zn.agentarena.modules.battle.promise;

import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleResourceAmount;
import com.zn.agentarena.modules.battle.domain.BattleResourceInventory;
import com.zn.agentarena.modules.battle.domain.BattleResourceType;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.memory.BattleMemorySystem;
import com.zn.agentarena.modules.battle.memory.BattleMemoryType;
import com.zn.agentarena.modules.battle.social.BattleRelationshipChange;
import com.zn.agentarena.modules.battle.social.BattleRelationshipSystem;
import com.zn.agentarena.modules.battle.social.BattleSocialStateInitializer;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 管理显式资源承诺的提出、接受、主动履行和确定性违约判定。 */
@Component
@RequiredArgsConstructor
public class BattlePromiseSystem {
    private final BattleSocialStateInitializer initializer;
    private final BattleMemorySystem memorySystem;
    private final BattleRelationshipSystem relationshipSystem;

    public List<EngineEvent> propose(BattleRoyaleGameState state, Long promiserId, Long promiseeId,
                                     BattlePromiseDecision decision, Collection<Long> informedPlayers) {
        initializer.ensure(state);
        BattlePlayerState promiser = state.findPlayer(promiserId);
        BattlePlayerState promisee = state.findPlayer(promiseeId);
        ParsedPromise parsed = parse(decision);
        if (!eligible(promiser) || !eligible(promisee) || Objects.equals(promiserId, promiseeId)
                || parsed == null || parsed.deadlineRound() <= state.getRoundNo()
                || parsed.deadlineRound() > state.getMaxRounds()) return List.of();
        long eventSequence = state.getSequenceNo() + 1;
        BattlePromise promise = BattlePromise.builder()
                .promiseId("battle-promise-" + state.getGameId() + "-" + eventSequence)
                .promiserPlayerId(promiserId).promiseePlayerId(promiseeId)
                .promiseType(BattlePromiseType.GIVE_RESOURCE).resourceType(parsed.resourceType())
                .amount(parsed.amount()).createdRound(state.getRoundNo()).deadlineRound(parsed.deadlineRound())
                .status(BattlePromiseStatus.PROPOSED)
                .knownByPlayerIds(knowledge(promiserId, promiseeId, informedPlayers)).build();
        state.getPromises().add(promise);
        memorySystem.rememberFact(state, promiserId, BattleMemoryType.PROMISE_MADE,
                promiserId, promiseeId, describe(promise, "我提出承诺："), 75, null);
        memorySystem.rememberFact(state, promiseeId, BattleMemoryType.PROMISE_RECEIVED,
                promiserId, promiseeId, describe(promise, "玩家" + promiserId + "向我承诺："), 80, null);
        return List.of(event(state, "BATTLE_PROMISE_PROPOSED", promiserId, payload(promise),
                Map.of("promiserPlayerId", promiserId, "promiseePlayerId", promiseeId,
                        "summary", "提出了一项私密资源承诺")));
    }

    public List<EngineEvent> accept(BattleRoyaleGameState state, Long actorId, Long promiserId,
                                    String promiseId, Collection<Long> informedPlayers) {
        BattlePromise promise = findPending(state, actorId, promiserId, promiseId);
        if (promise == null || state.getRoundNo() >= promise.getDeadlineRound()) return List.of();
        promise.setStatus(BattlePromiseStatus.ACCEPTED);
        promise.setAcceptedRound(state.getRoundNo());
        mergeKnowledge(promise.getKnownByPlayerIds(), informedPlayers);
        return List.of(event(state, "BATTLE_PROMISE_ACCEPTED", actorId, payload(promise),
                Map.of("promiserPlayerId", promise.getPromiserPlayerId(),
                        "promiseePlayerId", promise.getPromiseePlayerId(),
                        "summary", "双方达成一项私密承诺")));
    }

    public List<EngineEvent> reject(BattleRoyaleGameState state, Long actorId, Long promiserId, String promiseId) {
        BattlePromise promise = findPending(state, actorId, promiserId, promiseId);
        if (promise == null) return List.of();
        promise.setStatus(BattlePromiseStatus.CANCELLED);
        promise.setResolvedRound(state.getRoundNo());
        return List.of(event(state, "BATTLE_PROMISE_REJECTED", actorId, payload(promise),
                Map.of("promiserPlayerId", promise.getPromiserPlayerId(),
                        "promiseePlayerId", promise.getPromiseePlayerId(), "summary", "一项承诺提议被拒绝")));
    }

    public List<EngineEvent> fulfill(BattleRoyaleGameState state, Long actorId, Long promiseeId,
                                     String promiseId, List<BattleRelationshipChange> relationshipChanges) {
        BattlePromise promise = findAcceptedByPromiser(state, actorId, promiseeId, promiseId);
        if (promise == null) return List.of();
        BattlePlayerState promiser = state.findPlayer(actorId);
        BattlePlayerState promisee = state.findPlayer(promise.getPromiseePlayerId());
        BattleResourceAmount resource = new BattleResourceAmount(promise.getResourceType(), promise.getAmount());
        if (!eligible(promiser) || !eligible(promisee)
                || !Objects.equals(promiser.getRegion(), promisee.getRegion())
                || !BattleResourceInventory.has(promiser, resource)) return List.of();
        BattleResourceInventory.remove(promiser, resource);
        BattleResourceInventory.add(promisee, resource);
        promise.setStatus(BattlePromiseStatus.FULFILLED);
        promise.setResolvedRound(state.getRoundNo());
        memorySystem.rememberFact(state, actorId, BattleMemoryType.PROMISE_FULFILLED,
                actorId, promisee.getPlayerId(), describe(promise, "我兑现了承诺："), 90, promiser.getRegion());
        memorySystem.rememberFact(state, promisee.getPlayerId(), BattleMemoryType.PROMISE_FULFILLED,
                actorId, promisee.getPlayerId(), describe(promise, "对方向我兑现了承诺："), 95, promiser.getRegion());
        memorySystem.rememberFact(state, actorId, BattleMemoryType.RESOURCE_GIVEN,
                actorId, promisee.getPlayerId(), "我给予了承诺资源" + promise.getResourceType() + " x" + promise.getAmount(), 80, promiser.getRegion());
        memorySystem.rememberFact(state, promisee.getPlayerId(), BattleMemoryType.RESOURCE_RECEIVED,
                actorId, promisee.getPlayerId(), "我收到了承诺资源" + promise.getResourceType() + " x" + promise.getAmount(), 85, promiser.getRegion());
        relationshipChanges.add(relationshipSystem.promiseFulfilled(state, promisee.getPlayerId(), actorId));
        return List.of(event(state, "BATTLE_PROMISE_FULFILLED", actorId, payload(promise),
                Map.of("promiserPlayerId", actorId, "promiseePlayerId", promisee.getPlayerId(),
                        "summary", "一项资源承诺已经兑现")));
    }

    public List<EngineEvent> evaluateDeadlines(BattleRoyaleGameState state,
                                               List<BattleRelationshipChange> relationshipChanges) {
        List<EngineEvent> events = new ArrayList<>();
        for (BattlePromise promise : state.getPromises()) {
            BattlePlayerState promiser = state.findPlayer(promise.getPromiserPlayerId());
            BattlePlayerState promisee = state.findPlayer(promise.getPromiseePlayerId());
            if ((promise.getStatus() == BattlePromiseStatus.PROPOSED
                    || promise.getStatus() == BattlePromiseStatus.ACCEPTED)
                    && (promiser == null || promisee == null
                    || promiser.isEliminated() || promisee.isEliminated())) {
                promise.setStatus(BattlePromiseStatus.CANCELLED);
                promise.setResolvedRound(state.getRoundNo());
                events.add(event(state, "BATTLE_PROMISE_CANCELLED", null, payload(promise),
                        Map.of("summary", "一项承诺因参与者淘汰而终止")));
                continue;
            }
            if (promise.getStatus() == BattlePromiseStatus.PROPOSED
                    && state.getRoundNo() >= promise.getDeadlineRound()) {
                promise.setStatus(BattlePromiseStatus.EXPIRED);
                promise.setResolvedRound(state.getRoundNo());
                events.add(event(state, "BATTLE_PROMISE_EXPIRED", null, payload(promise),
                        Map.of("promiserPlayerId", promise.getPromiserPlayerId(),
                                "promiseePlayerId", promise.getPromiseePlayerId(), "summary", "一项未接受的承诺已过期")));
            } else if (promise.getStatus() == BattlePromiseStatus.ACCEPTED
                    && state.getRoundNo() >= promise.getDeadlineRound()) {
                promise.setStatus(BattlePromiseStatus.BROKEN);
                promise.setResolvedRound(state.getRoundNo());
                memorySystem.rememberFact(state, promise.getPromiserPlayerId(), BattleMemoryType.I_BROKE_PROMISE,
                        promise.getPromiserPlayerId(), promise.getPromiseePlayerId(), describe(promise, "我未能兑现承诺："), 95, null);
                memorySystem.rememberFact(state, promise.getPromiseePlayerId(), BattleMemoryType.PROMISE_BROKEN_TO_ME,
                        promise.getPromiserPlayerId(), promise.getPromiseePlayerId(), describe(promise, "对方违背了承诺："), 100, null);
                relationshipChanges.add(relationshipSystem.promiseBroken(state,
                        promise.getPromiseePlayerId(), promise.getPromiserPlayerId()));
                events.add(event(state, "BATTLE_PROMISE_BROKEN", promise.getPromiserPlayerId(), payload(promise),
                        Map.of("promiserPlayerId", promise.getPromiserPlayerId(),
                                "promiseePlayerId", promise.getPromiseePlayerId(), "summary", "一项私密承诺已经违约")));
            }
        }
        return events;
    }

    public List<BattlePromise> visibleTo(BattleRoyaleGameState state, Long playerId) {
        initializer.ensure(state);
        return state.getPromises().stream().filter(promise -> promise.getKnownByPlayerIds().contains(playerId)).toList();
    }

    private BattlePromise findPending(BattleRoyaleGameState state, Long actorId, Long promiserId, String id) {
        return state.getPromises().stream().filter(promise -> promise.getStatus() == BattlePromiseStatus.PROPOSED)
                .filter(promise -> id == null || id.equals(promise.getPromiseId()))
                .filter(promise -> Objects.equals(promise.getPromiseePlayerId(), actorId))
                .filter(promise -> promiserId == null || Objects.equals(promise.getPromiserPlayerId(), promiserId))
                .findFirst().orElse(null);
    }

    private BattlePromise findAcceptedByPromiser(BattleRoyaleGameState state, Long actorId, Long promiseeId, String id) {
        return state.getPromises().stream().filter(promise -> promise.getStatus() == BattlePromiseStatus.ACCEPTED)
                .filter(promise -> id == null || id.equals(promise.getPromiseId()))
                .filter(promise -> Objects.equals(promise.getPromiserPlayerId(), actorId))
                .filter(promise -> promiseeId == null || Objects.equals(promise.getPromiseePlayerId(), promiseeId))
                .findFirst().orElse(null);
    }

    private ParsedPromise parse(BattlePromiseDecision decision) {
        if (decision == null || decision.promiseType() == null || decision.resourceType() == null
                || decision.amount() == null || decision.deadlineRound() == null) return null;
        try {
            BattlePromiseType type = BattlePromiseType.valueOf(decision.promiseType().trim().toUpperCase(Locale.ROOT));
            BattleResourceType resource = BattleResourceType.valueOf(decision.resourceType().trim().toUpperCase(Locale.ROOT));
            if (type != BattlePromiseType.GIVE_RESOURCE || decision.amount() <= 0 || decision.amount() > 99) return null;
            return new ParsedPromise(resource, decision.amount(), decision.deadlineRound());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean eligible(BattlePlayerState player) {
        return player != null && player.isActive();
    }

    private List<Long> knowledge(Long first, Long second, Collection<Long> informed) {
        List<Long> result = new ArrayList<>(List.of(first, second));
        mergeKnowledge(result, informed);
        return result;
    }

    private void mergeKnowledge(List<Long> target, Collection<Long> informed) {
        if (informed == null) return;
        for (Long playerId : informed) if (playerId != null && !target.contains(playerId)) target.add(playerId);
    }

    private String describe(BattlePromise promise, String prefix) {
        return prefix + "在第" + promise.getDeadlineRound() + "轮前给予玩家" + promise.getPromiseePlayerId()
                + " " + promise.getResourceType() + " x" + promise.getAmount();
    }

    private Map<String, Object> payload(BattlePromise promise) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("promiseId", promise.getPromiseId());
        payload.put("promiserPlayerId", promise.getPromiserPlayerId());
        payload.put("promiseePlayerId", promise.getPromiseePlayerId());
        payload.put("promiseType", promise.getPromiseType().name());
        payload.put("resourceType", promise.getResourceType().name());
        payload.put("amount", promise.getAmount());
        payload.put("deadlineRound", promise.getDeadlineRound());
        payload.put("status", promise.getStatus().name());
        return payload;
    }

    private EngineEvent event(BattleRoyaleGameState state, String type, Long actorId,
                              Map<String, Object> payload, Map<String, Object> publicPayload) {
        state.setSequenceNo(state.getSequenceNo() + 1);
        return new EngineEvent(type, actorId, payload, publicPayload);
    }

    private record ParsedPromise(BattleResourceType resourceType, int amount, int deadlineRound) {
    }
}
