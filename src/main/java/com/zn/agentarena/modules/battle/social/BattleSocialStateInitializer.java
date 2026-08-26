package com.zn.agentarena.modules.battle.social;

import com.zn.agentarena.modules.battle.communication.BattleMessage;
import com.zn.agentarena.modules.battle.communication.BattleCommunicationSystem;
import com.zn.agentarena.modules.battle.domain.BattlePlayerLifeStatus;
import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleRules;
import com.zn.agentarena.modules.battle.memory.BattleAgentMemory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/** 为新对局和缺少社会字段的旧快照补齐确定性默认状态。 */
@Component
public class BattleSocialStateInitializer {

    public BattleRoyaleGameState ensure(BattleRoyaleGameState state) {
        if (state == null) return null;
        if (state.getRegions() == null || state.getRegions().isEmpty()) {
            state.setRegions(new ArrayList<>(java.util.List.of("A", "B", "C", "D", "E")));
        }
        if (state.getSafeRegions() == null) state.setSafeRegions(new ArrayList<>(state.getRegions()));
        if (state.getRingOrder() == null || state.getRingOrder().size() != state.getRegions().size()) {
            state.setRingOrder(new ArrayList<>(BattleRoyaleRules.ringOrder(state.getRegions(), state.getRandomSeed())));
        }
        if (state.getRoundTurnOrder() == null) state.setRoundTurnOrder(new ArrayList<>());
        if (state.getRegionSearchCounts() == null) state.setRegionSearchCounts(new LinkedHashMap<>());
        if (state.getSocialStates() == null) state.setSocialStates(new LinkedHashMap<>());
        if (state.getCommunicationHistory() == null) state.setCommunicationHistory(new ArrayList<BattleMessage>());
        while (state.getCommunicationHistory().size() > BattleCommunicationSystem.MAX_HISTORY) {
            state.getCommunicationHistory().remove(0);
        }
        if (state.getAlliances() == null) state.setAlliances(new ArrayList<>());
        if (state.getPromises() == null) state.setPromises(new ArrayList<>());
        if (state.getTradeProposals() == null) state.setTradeProposals(new ArrayList<>());
        if (state.getHiddenGoals() == null) state.setHiddenGoals(new ArrayList<>());
        if (state.getCooperativeObjectives() == null) state.setCooperativeObjectives(new ArrayList<>());
        if (state.getRumors() == null) state.setRumors(new ArrayList<>());
        if (state.getInformationTradeProposals() == null) state.setInformationTradeProposals(new ArrayList<>());
        state.getAlliances().forEach(item -> {
            if (item.getKnownByPlayerIds() == null) item.setKnownByPlayerIds(new ArrayList<>());
            addKnown(item.getKnownByPlayerIds(), item.getMemberAPlayerId(), item.getMemberBPlayerId());
        });
        state.getPromises().forEach(item -> {
            if (item.getKnownByPlayerIds() == null) item.setKnownByPlayerIds(new ArrayList<>());
            addKnown(item.getKnownByPlayerIds(), item.getPromiserPlayerId(), item.getPromiseePlayerId());
        });
        state.getTradeProposals().forEach(item -> {
            if (item.getKnownByPlayerIds() == null) item.setKnownByPlayerIds(new ArrayList<>());
            addKnown(item.getKnownByPlayerIds(), item.getProposerPlayerId(), item.getTargetPlayerId());
            if (item.getOffer() == null) item.setOffer(new ArrayList<>());
            if (item.getRequest() == null) item.setRequest(new ArrayList<>());
        });
        state.getRumors().forEach(item -> {
            if (item.getKnownByPlayerIds() == null) item.setKnownByPlayerIds(new ArrayList<>());
            addKnown(item.getKnownByPlayerIds(), item.getOriginatorPlayerId(), item.getSpeakerPlayerId());
        });
        state.getInformationTradeProposals().forEach(item -> {
            if (item.getKnownByPlayerIds() == null) item.setKnownByPlayerIds(new ArrayList<>());
            addKnown(item.getKnownByPlayerIds(), item.getSellerPlayerId(), item.getBuyerPlayerId());
        });
        state.getCooperativeObjectives().forEach(item -> {
            if (item.getRequired() == null) item.setRequired(new java.util.EnumMap<>(com.zn.agentarena.modules.battle.domain.BattleResourceType.class));
            if (item.getContributed() == null) item.setContributed(new java.util.EnumMap<>(com.zn.agentarena.modules.battle.domain.BattleResourceType.class));
            if (item.getContributionByPlayer() == null) item.setContributionByPlayer(new LinkedHashMap<>());
            if (item.getMinimumContributors() <= 0) item.setMinimumContributors(2);
        });
        if (state.getPlayers() == null) return state;
        for (BattlePlayerState observer : state.getPlayers()) {
            if (observer.getLifeStatus() == null) {
                observer.setLifeStatus(observer.isAlive()
                        ? BattlePlayerLifeStatus.ALIVE : BattlePlayerLifeStatus.ELIMINATED);
            }
            observer.setAlive(observer.getLifeStatus() == BattlePlayerLifeStatus.ALIVE);
            BattleAgentSocialState social = state.getSocialStates().computeIfAbsent(observer.getPlayerId(),
                    ignored -> new BattleAgentSocialState());
            if (social.getMemory() == null) social.setMemory(new BattleAgentMemory());
            if (social.getMemory().getEntries() == null) social.getMemory().setEntries(new ArrayList<>());
            if (social.getRelationships() == null) social.setRelationships(new LinkedHashMap<>());
            for (BattlePlayerState subject : state.getPlayers()) {
                if (!observer.getPlayerId().equals(subject.getPlayerId())) {
                    social.getRelationships().computeIfAbsent(subject.getPlayerId(),
                            ignored -> BattleRelationshipState.builder().build());
                }
            }
        }
        return state;
    }

    private void addKnown(java.util.List<Long> known, Long... playerIds) {
        for (Long id : playerIds) if (id != null && !known.contains(id)) known.add(id);
    }

    public BattleAgentSocialState socialState(BattleRoyaleGameState state, Long playerId) {
        ensure(state);
        return state.getSocialStates().computeIfAbsent(playerId, ignored -> new BattleAgentSocialState());
    }
}
