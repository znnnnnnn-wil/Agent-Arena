package com.zn.agentarena.modules.battle.simulation;

import com.zn.agentarena.modules.battle.alliance.BattleAllianceStatus;
import com.zn.agentarena.modules.battle.communication.BattleCommunicationChannel;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.information.BattleRumorSystem;
import com.zn.agentarena.modules.battle.promise.BattlePromiseStatus;
import java.util.*;

public class BattleStateInvariantChecker {
    public void verify(BattleRoyaleGameState state, long seed) {
        List<String> violations = violations(state);
        if (!violations.isEmpty()) throw new IllegalStateException("seed=" + seed + ", round="
                + state.getRoundNo() + ", invariants=" + violations);
    }

    public List<String> violations(BattleRoyaleGameState state) {
        List<String> out = new ArrayList<>();
        if (state == null) return List.of("STATE_NULL");
        Set<Long> ids = new HashSet<>();
        for (BattlePlayerState p : state.getPlayers() == null ? List.<BattlePlayerState>of() : state.getPlayers()) {
            if (p.getPlayerId() == null || !ids.add(p.getPlayerId())) out.add("PLAYER_ID_INVALID");
            if (p.getHealth() < 0 || p.getFood() < 0 || p.getWater() < 0 || p.getAmmo() < 0 || p.getArmor() < 0 || p.getMedkits() < 0) out.add("NEGATIVE_INVENTORY:" + p.getPlayerId());
            if (p.isAlive() != (p.effectiveLifeStatus() == BattlePlayerLifeStatus.ALIVE)) out.add("LIFE_STATUS_MISMATCH:" + p.getPlayerId());
            if (p.isDowned() && (p.getHealth() != 0 || p.getBleedOutRound() == null)) out.add("DOWNED_INVALID:" + p.getPlayerId());
            if (p.isEliminated() && state.turnPlayers().contains(p)) out.add("ELIMINATED_IN_TURN_QUEUE:" + p.getPlayerId());
        }
        Set<String> activePairs = new HashSet<>();
        state.getAlliances().forEach(a -> {
            if (a.getStatus() == BattleAllianceStatus.ACTIVE) {
                if (!ids.contains(a.getMemberAPlayerId()) || !ids.contains(a.getMemberBPlayerId())) out.add("ALLIANCE_MEMBER_MISSING:" + a.getAllianceId());
                String pair = List.of(a.getMemberAPlayerId(), a.getMemberBPlayerId()).stream().sorted().toList().toString();
                if (!activePairs.add(pair)) out.add("DUPLICATE_ACTIVE_ALLIANCE:" + pair);
            }
        });
        state.getPromises().forEach(p -> {
            if (!ids.contains(p.getPromiserPlayerId()) || !ids.contains(p.getPromiseePlayerId())) out.add("PROMISE_MEMBER_MISSING:" + p.getPromiseId());
            if ((p.getStatus() == BattlePromiseStatus.FULFILLED || p.getStatus() == BattlePromiseStatus.BROKEN) && p.getResolvedRound() == null) out.add("PROMISE_RESOLUTION_MISSING:" + p.getPromiseId());
        });
        state.getTradeProposals().forEach(t -> { t.getOffer().forEach(r -> { if (!r.valid()) out.add("TRADE_OFFER_INVALID:"+t.getTradeId()); }); t.getRequest().forEach(r -> { if (!r.valid()) out.add("TRADE_REQUEST_INVALID:"+t.getTradeId()); }); });
        state.getCommunicationHistory().forEach(m -> { if (m.getChannel() == BattleCommunicationChannel.PRIVATE && (!ids.contains(m.getSenderPlayerId()) || !ids.contains(m.getRecipientPlayerId()))) out.add("PRIVATE_MESSAGE_PARTICIPANT_MISSING:"+m.getId()); });
        if (state.getSocialStates() == null || !state.getSocialStates().keySet().containsAll(ids)) out.add("SOCIAL_STATE_INCOMPLETE");
        if (state.getRingOrder() != null && !state.getRingOrder().isEmpty()
                && (!new HashSet<>(state.getRingOrder()).equals(new HashSet<>(state.getRegions()))
                || state.getRingOrder().size() != state.getRegions().size())) out.add("RING_ORDER_INVALID");
        if (state.getRoundTurnOrder() != null) {
            if (new HashSet<>(state.getRoundTurnOrder()).size() != state.getRoundTurnOrder().size()) out.add("TURN_ORDER_DUPLICATE");
            if (!ids.containsAll(state.getRoundTurnOrder())) out.add("TURN_ORDER_PLAYER_MISSING");
            if (state.getActionsInRound() < 0 || state.getActionsInRound() > state.getRoundTurnOrder().size()) out.add("TURN_CURSOR_INVALID");
        }
        if (state.getRumors().size() > BattleRumorSystem.MAX_RUMORS_PER_GAME) out.add("RUMOR_LIMIT_EXCEEDED");
        state.getHiddenGoals().forEach(g -> { if (!ids.contains(g.getOwnerPlayerId())) out.add("HIDDEN_GOAL_OWNER_MISSING:"+g.getGoalId()); });
        return out;
    }
}
