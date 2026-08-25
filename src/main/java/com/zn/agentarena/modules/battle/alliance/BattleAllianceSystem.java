package com.zn.agentarena.modules.battle.alliance;

import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
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
import java.util.Map;
import java.util.Objects;

/** 维护需要双方同意的临时双人联盟；联盟不会禁止成员互相攻击。 */
@Component
@RequiredArgsConstructor
public class BattleAllianceSystem {
    private static final int PROPOSAL_LIFETIME_ROUNDS = 3;

    private final BattleSocialStateInitializer initializer;
    private final BattleMemorySystem memorySystem;
    private final BattleRelationshipSystem relationshipSystem;

    public List<EngineEvent> propose(BattleRoyaleGameState state, Long proposerId, Long targetId,
                                     Collection<Long> informedPlayers) {
        initializer.ensure(state);
        BattlePlayerState proposer = state.findPlayer(proposerId);
        BattlePlayerState target = state.findPlayer(targetId);
        if (!eligible(proposer) || !eligible(target) || Objects.equals(proposerId, targetId)
                || findOpenBetween(state, proposerId, targetId) != null) return List.of();
        long eventSequence = state.getSequenceNo() + 1;
        BattleAlliance alliance = BattleAlliance.builder()
                .allianceId("battle-alliance-" + state.getGameId() + "-" + eventSequence)
                .proposerPlayerId(proposerId).memberAPlayerId(proposerId).memberBPlayerId(targetId)
                .createdRound(state.getRoundNo()).expiresRound(state.getRoundNo() + PROPOSAL_LIFETIME_ROUNDS)
                .status(BattleAllianceStatus.PROPOSED)
                .knownByPlayerIds(knowledge(proposerId, targetId, informedPlayers)).build();
        state.getAlliances().add(alliance);
        memorySystem.rememberFact(state, targetId, BattleMemoryType.ALLIANCE_PROPOSED_TO_ME,
                proposerId, targetId, "玩家" + proposerId + "邀请我建立正式联盟", 75, null);
        Map<String, Object> payload = alliancePayload(alliance);
        return List.of(event(state, "BATTLE_ALLIANCE_PROPOSED", proposerId, payload,
                Map.of("proposerPlayerId", proposerId, "targetPlayerId", targetId,
                        "summary", "发出了一项联盟邀请")));
    }

    public List<EngineEvent> accept(BattleRoyaleGameState state, Long actorId, Long proposerId,
                                    String allianceId, Collection<Long> informedPlayers,
                                    List<BattleRelationshipChange> relationshipChanges) {
        BattleAlliance alliance = findPending(state, actorId, proposerId, allianceId);
        if (alliance == null || state.getRoundNo() >= alliance.getExpiresRound()) return List.of();
        alliance.setStatus(BattleAllianceStatus.ACTIVE);
        alliance.setActivatedRound(state.getRoundNo());
        mergeKnowledge(alliance.getKnownByPlayerIds(), informedPlayers);
        memorySystem.rememberFact(state, alliance.getMemberAPlayerId(), BattleMemoryType.ALLIANCE_FORMED,
                alliance.getMemberAPlayerId(), alliance.getMemberBPlayerId(), "我与玩家" + alliance.getMemberBPlayerId() + "建立了联盟", 85, null);
        memorySystem.rememberFact(state, alliance.getMemberBPlayerId(), BattleMemoryType.ALLIANCE_FORMED,
                alliance.getMemberAPlayerId(), alliance.getMemberBPlayerId(), "我与玩家" + alliance.getMemberAPlayerId() + "建立了联盟", 85, null);
        relationshipChanges.add(relationshipSystem.allianceFormed(state, alliance.getMemberAPlayerId(), alliance.getMemberBPlayerId()));
        relationshipChanges.add(relationshipSystem.allianceFormed(state, alliance.getMemberBPlayerId(), alliance.getMemberAPlayerId()));
        Map<String, Object> payload = alliancePayload(alliance);
        return List.of(event(state, "BATTLE_ALLIANCE_FORMED", actorId, payload,
                Map.of("allianceId", alliance.getAllianceId(), "memberAPlayerId", alliance.getMemberAPlayerId(),
                        "memberBPlayerId", alliance.getMemberBPlayerId(), "summary", "正式建立临时联盟")));
    }

    public List<EngineEvent> reject(BattleRoyaleGameState state, Long actorId, Long proposerId, String allianceId) {
        BattleAlliance alliance = findPending(state, actorId, proposerId, allianceId);
        if (alliance == null) return List.of();
        alliance.setStatus(BattleAllianceStatus.REJECTED);
        alliance.setEndedRound(state.getRoundNo());
        alliance.setEndReason("REJECTED");
        return List.of(event(state, "BATTLE_ALLIANCE_REJECTED", actorId, alliancePayload(alliance),
                Map.of("proposerPlayerId", alliance.getProposerPlayerId(), "targetPlayerId", actorId,
                        "summary", "联盟邀请被拒绝")));
    }

    public List<EngineEvent> leave(BattleRoyaleGameState state, Long actorId, Long otherMemberId,
                                   String allianceId) {
        BattleAlliance alliance = findActive(state, actorId, otherMemberId, allianceId);
        if (alliance == null) return List.of();
        end(alliance, state.getRoundNo(), "LEFT");
        Long other = alliance.otherMember(actorId);
        memorySystem.rememberFact(state, actorId, BattleMemoryType.ALLIANCE_ENDED,
                actorId, other, "我主动结束了与玩家" + other + "的联盟", 70, null);
        memorySystem.rememberFact(state, other, BattleMemoryType.ALLIANCE_ENDED,
                actorId, other, "玩家" + actorId + "退出了与我的联盟", 80, null);
        return List.of(event(state, "BATTLE_ALLIANCE_LEFT", actorId, alliancePayload(alliance),
                Map.of("allianceId", alliance.getAllianceId(), "leaverPlayerId", actorId,
                        "otherPlayerId", other, "summary", "主动退出联盟")));
    }

    public List<EngineEvent> expireProposals(BattleRoyaleGameState state) {
        List<EngineEvent> events = new ArrayList<>();
        for (BattleAlliance alliance : state.getAlliances()) {
            if (alliance.getStatus() == BattleAllianceStatus.PROPOSED
                    && state.getRoundNo() >= alliance.getExpiresRound()) {
                alliance.setStatus(BattleAllianceStatus.EXPIRED);
                alliance.setEndedRound(state.getRoundNo());
                alliance.setEndReason("EXPIRED");
                events.add(event(state, "BATTLE_ALLIANCE_EXPIRED", null, alliancePayload(alliance),
                        Map.of("allianceId", alliance.getAllianceId(), "summary", "一项联盟邀请已过期")));
            }
        }
        return events;
    }

    public BattleAlliance activeBetween(BattleRoyaleGameState state, Long firstId, Long secondId) {
        return state.getAlliances().stream().filter(alliance -> alliance.getStatus() == BattleAllianceStatus.ACTIVE)
                .filter(alliance -> alliance.includes(firstId) && alliance.includes(secondId)).findFirst().orElse(null);
    }

    public List<BattleAlliance> visibleTo(BattleRoyaleGameState state, Long playerId) {
        initializer.ensure(state);
        return state.getAlliances().stream()
                .filter(alliance -> alliance.getKnownByPlayerIds().contains(playerId)).toList();
    }

    public void end(BattleAlliance alliance, int roundNo, String reason) {
        alliance.setStatus(BattleAllianceStatus.ENDED);
        alliance.setEndedRound(roundNo);
        alliance.setEndReason(reason);
    }

    private BattleAlliance findOpenBetween(BattleRoyaleGameState state, Long firstId, Long secondId) {
        return state.getAlliances().stream()
                .filter(alliance -> alliance.getStatus() == BattleAllianceStatus.PROPOSED
                        || alliance.getStatus() == BattleAllianceStatus.ACTIVE)
                .filter(alliance -> alliance.includes(firstId) && alliance.includes(secondId)).findFirst().orElse(null);
    }

    private BattleAlliance findPending(BattleRoyaleGameState state, Long actorId, Long proposerId, String id) {
        return state.getAlliances().stream().filter(alliance -> alliance.getStatus() == BattleAllianceStatus.PROPOSED)
                .filter(alliance -> id == null || id.equals(alliance.getAllianceId()))
                .filter(alliance -> Objects.equals(alliance.getMemberBPlayerId(), actorId))
                .filter(alliance -> proposerId == null || Objects.equals(alliance.getProposerPlayerId(), proposerId))
                .findFirst().orElse(null);
    }

    private BattleAlliance findActive(BattleRoyaleGameState state, Long actorId, Long otherId, String id) {
        return state.getAlliances().stream().filter(alliance -> alliance.getStatus() == BattleAllianceStatus.ACTIVE)
                .filter(alliance -> id == null || id.equals(alliance.getAllianceId()))
                .filter(alliance -> alliance.includes(actorId))
                .filter(alliance -> otherId == null || alliance.includes(otherId)).findFirst().orElse(null);
    }

    private boolean eligible(BattlePlayerState player) {
        return player != null && player.isActive();
    }

    private List<Long> knowledge(Long first, Long second, Collection<Long> informed) {
        List<Long> result = new ArrayList<>();
        result.add(first);
        if (!Objects.equals(first, second)) result.add(second);
        mergeKnowledge(result, informed);
        return result;
    }

    private void mergeKnowledge(List<Long> target, Collection<Long> informed) {
        if (informed == null) return;
        for (Long playerId : informed) if (playerId != null && !target.contains(playerId)) target.add(playerId);
    }

    private Map<String, Object> alliancePayload(BattleAlliance alliance) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("allianceId", alliance.getAllianceId());
        payload.put("proposerPlayerId", alliance.getProposerPlayerId());
        payload.put("memberAPlayerId", alliance.getMemberAPlayerId());
        payload.put("memberBPlayerId", alliance.getMemberBPlayerId());
        payload.put("status", alliance.getStatus().name());
        payload.put("createdRound", alliance.getCreatedRound());
        payload.put("expiresRound", alliance.getExpiresRound());
        payload.put("endedRound", alliance.getEndedRound());
        payload.put("endReason", alliance.getEndReason());
        return payload;
    }

    private EngineEvent event(BattleRoyaleGameState state, String type, Long actorId,
                              Map<String, Object> payload, Map<String, Object> publicPayload) {
        state.setSequenceNo(state.getSequenceNo() + 1);
        return new EngineEvent(type, actorId, payload, publicPayload);
    }
}
