package com.zn.agentarena.modules.battle.information;

import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.memory.*;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component @RequiredArgsConstructor
public class BattleRumorSystem {
    public static final int MAX_RUMORS_PER_GAME = 200;
    private final BattleMemorySystem memorySystem;

    public List<EngineEvent> share(BattleRoyaleGameState state, Long actorId, BattleRumorDecision decision, Collection<Long> audience) {
        if (decision == null || audience == null || audience.isEmpty()
                || state.findPlayer(actorId) == null || !state.findPlayer(actorId).isActive()
                || state.getRumors().size() >= MAX_RUMORS_PER_GAME) return List.of();
        BattleRumor rumor;
        if (decision.rumorId() != null) {
            BattleRumor source = find(state, decision.rumorId());
            if (source == null || !source.getKnownByPlayerIds().contains(actorId)) return List.of();
            if (duplicate(state, actorId, source.getRumorId(), source.getClaim(), audience)) return List.of();
            rumor = BattleRumor.builder().rumorId("rumor-" + state.getGameId() + "-" + (state.getSequenceNo()+1))
                    .originatorPlayerId(source.getOriginatorPlayerId()).speakerPlayerId(actorId)
                    .sourceRumorId(source.getRumorId()).subjectPlayerId(source.getSubjectPlayerId())
                    .claimType(source.getClaimType()).claim(source.getClaim()).confidence(Math.max(5, source.getConfidence()-15))
                    .generation(source.getGeneration()+1).createdRound(state.getRoundNo()).knownByPlayerIds(knowledge(actorId,audience)).build();
        } else {
            if (decision.claimType() == null || decision.claim() == null || decision.claim().isBlank()) return List.of();
            BattleRumorClaimType type;
            try { type = BattleRumorClaimType.valueOf(decision.claimType().trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { return List.of(); }
            String claim = decision.claim().trim().substring(0, Math.min(160, decision.claim().trim().length()));
            int confidence = Math.max(5, Math.min(90, decision.confidence() == null ? 50 : decision.confidence()));
            if (duplicate(state, actorId, null, claim, audience)) return List.of();
            rumor = BattleRumor.builder().rumorId("rumor-" + state.getGameId() + "-" + (state.getSequenceNo()+1))
                    .originatorPlayerId(actorId).speakerPlayerId(actorId).subjectPlayerId(decision.subjectPlayerId()).claimType(type).claim(claim)
                    .confidence(confidence).generation(0).createdRound(state.getRoundNo()).knownByPlayerIds(knowledge(actorId,audience)).build();
        }
        state.getRumors().add(rumor);
        memorySystem.rememberClaim(state, actorId, BattleMemoryType.RUMOR_SHARED, actorId, rumor.getSubjectPlayerId(), "我传播了传闻："+rumor.getClaim(), rumor.getConfidence(), null, rumor.getConfidence());
        for (Long id : audience) if (!Objects.equals(id, actorId)) memorySystem.rememberClaim(state,id,BattleMemoryType.RUMOR_HEARD,actorId,rumor.getSubjectPlayerId(),"听到未经证实的传闻："+rumor.getClaim(),rumor.getConfidence(),null,rumor.getConfidence());
        state.setSequenceNo(state.getSequenceNo()+1);
        Map<String,Object> full = new LinkedHashMap<>(); full.put("rumorId",rumor.getRumorId()); full.put("sourceRumorId",rumor.getSourceRumorId()); full.put("speakerPlayerId",actorId); full.put("originatorPlayerId",rumor.getOriginatorPlayerId()); full.put("claimType",rumor.getClaimType()); full.put("claim",rumor.getClaim()); full.put("confidence",rumor.getConfidence()); full.put("generation",rumor.getGeneration()); full.put("audiencePlayerIds",rumor.getKnownByPlayerIds());
        return List.of(new EngineEvent("BATTLE_RUMOR_SHARED",actorId,full,Map.of("speakerPlayerId",actorId,"audienceSize",rumor.getKnownByPlayerIds().size(),"summary","一条未经证实的传闻正在传播")));
    }

    public List<BattleRumor> visibleTo(BattleRoyaleGameState state, Long id) { return state.getRumors().stream().filter(r -> r.getKnownByPlayerIds().contains(id)).toList(); }
    public BattleRumor find(BattleRoyaleGameState state,String id) { return state.getRumors().stream().filter(r -> Objects.equals(r.getRumorId(),id)).findFirst().orElse(null); }
    private boolean duplicate(BattleRoyaleGameState state, Long speaker, String sourceId, String claim, Collection<Long> audience) {
        Set<Long> expected = new LinkedHashSet<>(audience); expected.add(speaker); expected.remove(null);
        return state.getRumors().stream().anyMatch(r -> r.getCreatedRound() == state.getRoundNo()
                && Objects.equals(r.getSpeakerPlayerId(), speaker) && Objects.equals(r.getSourceRumorId(), sourceId)
                && Objects.equals(r.getClaim(), claim) && new LinkedHashSet<>(r.getKnownByPlayerIds()).equals(expected));
    }
    private List<Long> knowledge(Long actor,Collection<Long> audience) { LinkedHashSet<Long> set=new LinkedHashSet<>(); set.add(actor); set.addAll(audience); set.remove(null); return new ArrayList<>(set); }
}
