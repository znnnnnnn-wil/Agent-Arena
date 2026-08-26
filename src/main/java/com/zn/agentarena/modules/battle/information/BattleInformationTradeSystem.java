package com.zn.agentarena.modules.battle.information;

import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.memory.*;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component @RequiredArgsConstructor
public class BattleInformationTradeSystem {
    private final BattleRumorSystem rumorSystem;
    private final BattleMemorySystem memorySystem;

    public List<EngineEvent> propose(BattleRoyaleGameState state, Long seller, Long buyer, BattleInformationTradeDecision decision) {
        if (decision == null || decision.rumorId() == null || decision.resourceType() == null || decision.amount() == null || decision.amount() <= 0 || decision.amount() > 99 || Objects.equals(seller,buyer)) return List.of();
        BattleRumor rumor=rumorSystem.find(state,decision.rumorId()); BattlePlayerState s=state.findPlayer(seller), b=state.findPlayer(buyer);
        if (rumor==null || !rumor.getKnownByPlayerIds().contains(seller) || s==null || b==null || !s.isActive() || !b.isActive()) return List.of();
        BattleResourceType type; try { type=BattleResourceType.valueOf(decision.resourceType().trim().toUpperCase(Locale.ROOT)); } catch(IllegalArgumentException e){return List.of();}
        BattleInformationTradeProposal p=BattleInformationTradeProposal.builder().tradeId("info-trade-"+state.getGameId()+"-"+(state.getSequenceNo()+1)).sellerPlayerId(seller).buyerPlayerId(buyer).rumorId(rumor.getRumorId()).price(new BattleResourceAmount(type,decision.amount())).createdRound(state.getRoundNo()).expiresRound(state.getRoundNo()+3).status(BattleInformationTradeStatus.PROPOSED).knownByPlayerIds(new ArrayList<>(List.of(seller,buyer))).build();
        state.getInformationTradeProposals().add(p); memorySystem.rememberFact(state,buyer,BattleMemoryType.INFORMATION_TRADE_PROPOSED,seller,buyer,"有人向我出售一条传闻",80,null);
        return List.of(event(state,"BATTLE_INFORMATION_TRADE_PROPOSED",seller,p,"一项私密情报交易被提出"));
    }

    public List<EngineEvent> accept(BattleRoyaleGameState state, Long buyer, Long seller, String id) {
        BattleInformationTradeProposal p=pending(state,buyer,seller,id); if(p==null)return List.of(); BattlePlayerState b=state.findPlayer(buyer),s=state.findPlayer(p.getSellerPlayerId()); BattleRumor r=rumorSystem.find(state,p.getRumorId());
        String failure=null; if(b==null||s==null||!b.isActive()||!s.isActive())failure="PLAYER_UNAVAILABLE"; else if(!Objects.equals(b.getRegion(),s.getRegion()))failure="NOT_SAME_REGION"; else if(r==null||!r.getKnownByPlayerIds().contains(s.getPlayerId()))failure="INFORMATION_UNAVAILABLE"; else if(!BattleResourceInventory.has(b,p.getPrice()))failure="BUYER_RESOURCE_MISSING";
        if(failure!=null){p.setStatus(BattleInformationTradeStatus.FAILED);p.setResolvedRound(state.getRoundNo());p.setFailureReason(failure);return List.of(event(state,"BATTLE_INFORMATION_TRADE_FAILED",buyer,p,"情报交易因条件变化失败"));}
        BattleResourceInventory.remove(b,p.getPrice()); BattleResourceInventory.add(s,p.getPrice()); if(!r.getKnownByPlayerIds().contains(buyer))r.getKnownByPlayerIds().add(buyer); p.setStatus(BattleInformationTradeStatus.COMPLETED);p.setResolvedRound(state.getRoundNo()); memorySystem.rememberFact(state,buyer,BattleMemoryType.INFORMATION_TRADE_COMPLETED,s.getPlayerId(),buyer,"购买了未经证实的情报："+r.getClaim(),r.getConfidence(),null); return List.of(event(state,"BATTLE_INFORMATION_TRADE_COMPLETED",buyer,p,"一项私密情报交易已原子完成"));
    }
    public List<EngineEvent> reject(BattleRoyaleGameState state,Long buyer,Long seller,String id){BattleInformationTradeProposal p=pending(state,buyer,seller,id);if(p==null)return List.of();p.setStatus(BattleInformationTradeStatus.REJECTED);p.setResolvedRound(state.getRoundNo());return List.of(event(state,"BATTLE_INFORMATION_TRADE_REJECTED",buyer,p,"一项私密情报交易被拒绝"));}
    public List<EngineEvent> expire(BattleRoyaleGameState state){List<EngineEvent> e=new ArrayList<>();for(var p:state.getInformationTradeProposals())if(p.getStatus()==BattleInformationTradeStatus.PROPOSED&&state.getRoundNo()>=p.getExpiresRound()){p.setStatus(BattleInformationTradeStatus.EXPIRED);p.setResolvedRound(state.getRoundNo());e.add(event(state,"BATTLE_INFORMATION_TRADE_EXPIRED",null,p,"一项私密情报交易已过期"));}return e;}
    public List<BattleInformationTradeProposal> visibleTo(BattleRoyaleGameState state,Long id){return state.getInformationTradeProposals().stream().filter(p->p.getKnownByPlayerIds().contains(id)).toList();}
    private BattleInformationTradeProposal pending(BattleRoyaleGameState s,Long b,Long seller,String id){return s.getInformationTradeProposals().stream().filter(p->p.getStatus()==BattleInformationTradeStatus.PROPOSED&&Objects.equals(p.getBuyerPlayerId(),b)).filter(p->seller==null||Objects.equals(p.getSellerPlayerId(),seller)).filter(p->id==null||Objects.equals(p.getTradeId(),id)).findFirst().orElse(null);}
    private EngineEvent event(BattleRoyaleGameState s,String type,Long actor,BattleInformationTradeProposal p,String summary){s.setSequenceNo(s.getSequenceNo()+1);Map<String,Object> full=new LinkedHashMap<>();full.put("tradeId",p.getTradeId());full.put("sellerPlayerId",p.getSellerPlayerId());full.put("buyerPlayerId",p.getBuyerPlayerId());full.put("rumorId",p.getRumorId());full.put("price",p.getPrice());full.put("status",p.getStatus().name());full.put("failureReason",p.getFailureReason());return new EngineEvent(type,actor,full,Map.of("sellerPlayerId",p.getSellerPlayerId(),"buyerPlayerId",p.getBuyerPlayerId(),"summary",summary));}
}
