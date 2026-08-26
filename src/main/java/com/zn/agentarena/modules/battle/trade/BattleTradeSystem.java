package com.zn.agentarena.modules.battle.trade;

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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 负责交易协商，并在接受时执行全成功或全不变的资源交换。 */
@Component
@RequiredArgsConstructor
public class BattleTradeSystem {
    private static final int PROPOSAL_LIFETIME_ROUNDS = 3;

    private final BattleSocialStateInitializer initializer;
    private final BattleMemorySystem memorySystem;
    private final BattleRelationshipSystem relationshipSystem;

    public List<EngineEvent> propose(BattleRoyaleGameState state, Long proposerId, Long targetId,
                                     BattleTradeDecision decision, Collection<Long> informedPlayers) {
        initializer.ensure(state);
        BattlePlayerState proposer = state.findPlayer(proposerId);
        BattlePlayerState target = state.findPlayer(targetId);
        ParsedTrade parsed = parse(decision);
        if (!eligible(proposer) || !eligible(target) || Objects.equals(proposerId, targetId)
                || parsed == null || !hasAll(proposer, parsed.offer())) return List.of();
        long eventSequence = state.getSequenceNo() + 1;
        BattleTradeProposal proposal = BattleTradeProposal.builder()
                .tradeId("battle-trade-" + state.getGameId() + "-" + eventSequence)
                .proposerPlayerId(proposerId).targetPlayerId(targetId)
                .offer(parsed.offer()).request(parsed.request()).createdRound(state.getRoundNo())
                .expiresRound(state.getRoundNo() + PROPOSAL_LIFETIME_ROUNDS)
                .status(BattleTradeStatus.PROPOSED)
                .knownByPlayerIds(knowledge(proposerId, targetId, informedPlayers)).build();
        state.getTradeProposals().add(proposal);
        memorySystem.rememberFact(state, proposerId, BattleMemoryType.TRADE_PROPOSED,
                proposerId, targetId, "我向玩家" + targetId + "提出交易：" + describe(proposal), 65, null);
        memorySystem.rememberFact(state, targetId, BattleMemoryType.TRADE_PROPOSED,
                proposerId, targetId, "玩家" + proposerId + "向我提出交易：" + describe(proposal), 70, null);
        return List.of(event(state, "BATTLE_TRADE_PROPOSED", proposerId, payload(proposal),
                Map.of("proposerPlayerId", proposerId, "targetPlayerId", targetId,
                        "summary", "提出了一项私密资源交易")));
    }

    public List<EngineEvent> accept(BattleRoyaleGameState state, Long actorId, Long proposerId, String tradeId,
                                    List<BattleRelationshipChange> relationshipChanges) {
        BattleTradeProposal proposal = findPending(state, actorId, proposerId, tradeId);
        if (proposal == null) return List.of();
        BattlePlayerState proposer = state.findPlayer(proposal.getProposerPlayerId());
        BattlePlayerState target = state.findPlayer(proposal.getTargetPlayerId());
        String failure = validateExecution(proposer, target, proposal);
        if (failure != null) {
            proposal.setStatus(BattleTradeStatus.FAILED);
            proposal.setResolvedRound(state.getRoundNo());
            proposal.setFailureReason(failure);
            rememberFailed(state, proposal, failure);
            return List.of(event(state, "BATTLE_TRADE_FAILED", actorId, payload(proposal),
                    Map.of("proposerPlayerId", proposal.getProposerPlayerId(),
                            "targetPlayerId", proposal.getTargetPlayerId(), "summary", "交易因条件变化未能执行")));
        }

        // 所有条件先验证完毕，以下四步不会失败，因此不会出现半完成状态。
        removeAll(proposer, proposal.getOffer());
        removeAll(target, proposal.getRequest());
        addAll(target, proposal.getOffer());
        addAll(proposer, proposal.getRequest());
        proposal.setStatus(BattleTradeStatus.COMPLETED);
        proposal.setResolvedRound(state.getRoundNo());
        rememberCompleted(state, proposal);
        if (proposal.getRequest().isEmpty() && !proposal.getOffer().isEmpty()) {
            relationshipChanges.add(relationshipSystem.resourceReceived(state,
                    proposal.getTargetPlayerId(), proposal.getProposerPlayerId()));
            rememberGift(state, proposal.getProposerPlayerId(), proposal.getTargetPlayerId(), proposal.getOffer());
        } else if (proposal.getOffer().isEmpty() && !proposal.getRequest().isEmpty()) {
            relationshipChanges.add(relationshipSystem.resourceReceived(state,
                    proposal.getProposerPlayerId(), proposal.getTargetPlayerId()));
            rememberGift(state, proposal.getTargetPlayerId(), proposal.getProposerPlayerId(), proposal.getRequest());
        }
        return List.of(event(state, "BATTLE_TRADE_COMPLETED", actorId, payload(proposal),
                Map.of("proposerPlayerId", proposal.getProposerPlayerId(),
                        "targetPlayerId", proposal.getTargetPlayerId(), "summary", "双方完成了原子资源交换")));
    }

    public List<EngineEvent> reject(BattleRoyaleGameState state, Long actorId, Long proposerId, String tradeId) {
        BattleTradeProposal proposal = findPending(state, actorId, proposerId, tradeId);
        if (proposal == null) return List.of();
        proposal.setStatus(BattleTradeStatus.REJECTED);
        proposal.setResolvedRound(state.getRoundNo());
        return List.of(event(state, "BATTLE_TRADE_REJECTED", actorId, payload(proposal),
                Map.of("proposerPlayerId", proposal.getProposerPlayerId(),
                        "targetPlayerId", proposal.getTargetPlayerId(), "summary", "一项资源交易被拒绝")));
    }

    public List<EngineEvent> expireProposals(BattleRoyaleGameState state) {
        List<EngineEvent> events = new ArrayList<>();
        for (BattleTradeProposal proposal : state.getTradeProposals()) {
            if (proposal.getStatus() == BattleTradeStatus.PROPOSED
                    && state.getRoundNo() >= proposal.getExpiresRound()) {
                proposal.setStatus(BattleTradeStatus.EXPIRED);
                proposal.setResolvedRound(state.getRoundNo());
                events.add(event(state, "BATTLE_TRADE_EXPIRED", null, payload(proposal),
                        Map.of("proposerPlayerId", proposal.getProposerPlayerId(),
                                "targetPlayerId", proposal.getTargetPlayerId(), "summary", "一项交易提案已过期")));
            }
        }
        return events;
    }

    public List<BattleTradeProposal> visibleTo(BattleRoyaleGameState state, Long playerId) {
        initializer.ensure(state);
        return state.getTradeProposals().stream()
                .filter(proposal -> proposal.getKnownByPlayerIds().contains(playerId)).toList();
    }

    private String validateExecution(BattlePlayerState proposer, BattlePlayerState target,
                                     BattleTradeProposal proposal) {
        if (!eligible(proposer) || !eligible(target)) return "PLAYER_UNAVAILABLE";
        if (!Objects.equals(proposer.getRegion(), target.getRegion())) return "NOT_SAME_REGION";
        if (!hasAll(proposer, proposal.getOffer())) return "PROPOSER_RESOURCE_MISSING";
        if (!hasAll(target, proposal.getRequest())) return "TARGET_RESOURCE_MISSING";
        return null;
    }

    private boolean hasAll(BattlePlayerState player, List<BattleResourceAmount> resources) {
        return resources.stream().allMatch(resource -> BattleResourceInventory.has(player, resource));
    }

    private void removeAll(BattlePlayerState player, List<BattleResourceAmount> resources) {
        resources.forEach(resource -> BattleResourceInventory.remove(player, resource));
    }

    private void addAll(BattlePlayerState player, List<BattleResourceAmount> resources) {
        resources.forEach(resource -> BattleResourceInventory.add(player, resource));
    }

    private ParsedTrade parse(BattleTradeDecision decision) {
        if (decision == null) return null;
        List<BattleResourceAmount> offer = parseItems(decision.offer());
        List<BattleResourceAmount> request = parseItems(decision.request());
        if (offer == null || request == null || offer.isEmpty() && request.isEmpty()) return null;
        return new ParsedTrade(offer, request);
    }

    private List<BattleResourceAmount> parseItems(List<BattleTradeItemDecision> items) {
        if (items == null || items.isEmpty()) return List.of();
        EnumMap<BattleResourceType, Integer> totals = new EnumMap<>(BattleResourceType.class);
        try {
            for (BattleTradeItemDecision item : items) {
                if (item == null || item.resourceType() == null || item.amount() == null
                        || item.amount() <= 0 || item.amount() > 99) return null;
                BattleResourceType type = BattleResourceType.valueOf(item.resourceType().trim().toUpperCase(Locale.ROOT));
                totals.merge(type, item.amount(), Integer::sum);
                if (totals.get(type) > 99) return null;
            }
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return totals.entrySet().stream().map(entry -> new BattleResourceAmount(entry.getKey(), entry.getValue())).toList();
    }

    private BattleTradeProposal findPending(BattleRoyaleGameState state, Long actorId, Long proposerId, String id) {
        return state.getTradeProposals().stream().filter(proposal -> proposal.getStatus() == BattleTradeStatus.PROPOSED)
                .filter(proposal -> id == null || id.equals(proposal.getTradeId()))
                .filter(proposal -> Objects.equals(proposal.getTargetPlayerId(), actorId))
                .filter(proposal -> proposerId == null || Objects.equals(proposal.getProposerPlayerId(), proposerId))
                .findFirst().orElse(null);
    }

    private boolean eligible(BattlePlayerState player) {
        return player != null && player.isActive();
    }

    private void rememberCompleted(BattleRoyaleGameState state, BattleTradeProposal proposal) {
        String description = describe(proposal);
        memorySystem.rememberFact(state, proposal.getProposerPlayerId(), BattleMemoryType.TRADE_COMPLETED,
                proposal.getProposerPlayerId(), proposal.getTargetPlayerId(), "交易完成：" + description, 85, null);
        memorySystem.rememberFact(state, proposal.getTargetPlayerId(), BattleMemoryType.TRADE_COMPLETED,
                proposal.getProposerPlayerId(), proposal.getTargetPlayerId(), "交易完成：" + description, 85, null);
    }

    private void rememberFailed(BattleRoyaleGameState state, BattleTradeProposal proposal, String failure) {
        memorySystem.rememberFact(state, proposal.getProposerPlayerId(), BattleMemoryType.TRADE_FAILED,
                proposal.getProposerPlayerId(), proposal.getTargetPlayerId(), "交易失败：" + failure, 70, null);
        memorySystem.rememberFact(state, proposal.getTargetPlayerId(), BattleMemoryType.TRADE_FAILED,
                proposal.getProposerPlayerId(), proposal.getTargetPlayerId(), "交易失败：" + failure, 70, null);
    }

    private void rememberGift(BattleRoyaleGameState state, Long giver, Long receiver,
                              List<BattleResourceAmount> resources) {
        memorySystem.rememberFact(state, giver, BattleMemoryType.RESOURCE_GIVEN,
                giver, receiver, "我无偿赠送资源：" + resources, 80, null);
        memorySystem.rememberFact(state, receiver, BattleMemoryType.RESOURCE_RECEIVED,
                giver, receiver, "我收到无偿资源：" + resources, 85, null);
    }

    private List<Long> knowledge(Long first, Long second, Collection<Long> informed) {
        List<Long> result = new ArrayList<>(List.of(first, second));
        if (informed != null) for (Long id : informed) if (id != null && !result.contains(id)) result.add(id);
        return result;
    }

    private String describe(BattleTradeProposal proposal) {
        return "offer=" + proposal.getOffer() + ", request=" + proposal.getRequest();
    }

    private Map<String, Object> payload(BattleTradeProposal proposal) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tradeId", proposal.getTradeId());
        payload.put("proposerPlayerId", proposal.getProposerPlayerId());
        payload.put("targetPlayerId", proposal.getTargetPlayerId());
        payload.put("offer", proposal.getOffer());
        payload.put("request", proposal.getRequest());
        payload.put("status", proposal.getStatus().name());
        payload.put("failureReason", proposal.getFailureReason());
        return payload;
    }

    private EngineEvent event(BattleRoyaleGameState state, String type, Long actorId,
                              Map<String, Object> payload, Map<String, Object> publicPayload) {
        state.setSequenceNo(state.getSequenceNo() + 1);
        return new EngineEvent(type, actorId, payload, publicPayload);
    }

    private record ParsedTrade(List<BattleResourceAmount> offer, List<BattleResourceAmount> request) {
    }
}
