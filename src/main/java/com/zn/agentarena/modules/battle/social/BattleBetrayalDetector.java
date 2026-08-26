package com.zn.agentarena.modules.battle.social;

import com.zn.agentarena.modules.battle.alliance.BattleAlliance;
import com.zn.agentarena.modules.battle.alliance.BattleAllianceSystem;
import com.zn.agentarena.modules.battle.domain.BattleActionType;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleAction;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.memory.BattleMemorySystem;
import com.zn.agentarena.modules.battle.memory.BattleMemoryType;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 根据已存在的 ACTIVE 联盟和真实成功行为推导背叛，不提供 BETRAY 动作。 */
@Component
@RequiredArgsConstructor
public class BattleBetrayalDetector {
    private final BattleAllianceSystem allianceSystem;
    private final BattleMemorySystem memorySystem;
    private final BattleRelationshipSystem relationshipSystem;

    public BattleBetrayalResult detect(BattleRoyaleGameState state, BattleRoyaleAction action,
                                       String eventRegion, int damage) {
        if (action == null || action.targetPlayerId() == null
                || action.type() != BattleActionType.ATTACK && action.type() != BattleActionType.EXECUTE) {
            return BattleBetrayalResult.none();
        }
        if (action.type() == BattleActionType.ATTACK && damage <= 0) return BattleBetrayalResult.none();
        BattleAlliance alliance = allianceSystem.activeBetween(state, action.actorPlayerId(), action.targetPlayerId());
        if (alliance == null) return BattleBetrayalResult.none();
        allianceSystem.end(alliance, state.getRoundNo(), "BETRAYAL");
        List<Long> witnesses = state.contendingPlayers().stream()
                .filter(player -> !Objects.equals(player.getPlayerId(), action.actorPlayerId()))
                .filter(player -> !Objects.equals(player.getPlayerId(), action.targetPlayerId()))
                .filter(player -> Objects.equals(player.getRegion(), eventRegion))
                .map(player -> player.getPlayerId()).toList();
        memorySystem.rememberFact(state, action.targetPlayerId(), BattleMemoryType.ALLY_BETRAYED_ME,
                action.actorPlayerId(), action.targetPlayerId(), "联盟伙伴" + action.actorPlayerId() + "背叛并攻击了我", 100, eventRegion);
        memorySystem.rememberFact(state, action.actorPlayerId(), BattleMemoryType.I_BETRAYED_ALLY,
                action.actorPlayerId(), action.targetPlayerId(), "我主动攻击了联盟伙伴" + action.targetPlayerId(), 100, eventRegion);
        for (Long witnessId : witnesses) {
            memorySystem.rememberFact(state, witnessId, BattleMemoryType.ALLIANCE_BETRAYAL_WITNESSED,
                    action.actorPlayerId(), action.targetPlayerId(), "我亲眼看见玩家" + action.actorPlayerId()
                            + "攻击其联盟伙伴" + action.targetPlayerId(), 90, eventRegion);
        }
        List<BattleRelationshipChange> changes = new ArrayList<>();
        changes.add(relationshipSystem.betrayed(state, action.targetPlayerId(), action.actorPlayerId(), damage));
        for (Long witnessId : witnesses) {
            changes.add(relationshipSystem.witnessedBetrayal(state, witnessId, action.actorPlayerId()));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("allianceId", alliance.getAllianceId());
        payload.put("betrayerPlayerId", action.actorPlayerId());
        payload.put("victimPlayerId", action.targetPlayerId());
        payload.put("witnessPlayerIds", witnesses);
        payload.put("triggerAction", action.type().name());
        state.setSequenceNo(state.getSequenceNo() + 1);
        EngineEvent event = new EngineEvent("BATTLE_ALLIANCE_BETRAYED", action.actorPlayerId(), payload,
                Map.of("allianceId", alliance.getAllianceId(), "betrayerPlayerId", action.actorPlayerId(),
                        "victimPlayerId", action.targetPlayerId(), "summary", "一名玩家主动攻击了联盟伙伴，联盟破裂"));
        return new BattleBetrayalResult(true, witnesses, List.of(event), changes);
    }
}
