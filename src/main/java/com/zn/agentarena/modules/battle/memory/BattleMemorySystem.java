package com.zn.agentarena.modules.battle.memory;

import com.zn.agentarena.modules.battle.communication.BattleCommunicationChannel;
import com.zn.agentarena.modules.battle.communication.BattleMessage;
import com.zn.agentarena.modules.battle.domain.BattleActionType;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleAction;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.perception.BattleAgentObservation;
import com.zn.agentarena.modules.battle.perception.ObservedBattlePlayer;
import com.zn.agentarena.modules.battle.social.BattleAgentSocialState;
import com.zn.agentarena.modules.battle.social.BattleSocialStateInitializer;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 只把智能体亲历、可见或收到的事件转为其私有结构化记忆。 */
@Component
@RequiredArgsConstructor
public class BattleMemorySystem {
    public static final int MAX_ENTRIES_PER_AGENT = 80;

    private final BattleSocialStateInitializer initializer;

    public void rememberObservation(BattleRoyaleGameState state, BattleAgentObservation observation) {
        Long observerId = observation.self().playerId();
        for (ObservedBattlePlayer visible : observation.visiblePlayers()) {
            add(state, observerId, BattleMemoryType.PLAYER_SEEN, visible.playerId(), observerId,
                    "看见" + visible.name() + "，状态=" + visible.healthStatus()
                            + "，明显持械=" + visible.obviousWeapon(),
                    25, visible.currentObservedRegion(), 100, true);
            add(state, observerId, BattleMemoryType.LAST_KNOWN_LOCATION, visible.playerId(), observerId,
                    visible.name() + "最后一次在" + visible.currentObservedRegion() + "区被看见",
                    35, visible.currentObservedRegion(), 100, true);
        }
    }

    public void rememberAction(BattleRoyaleGameState state, BattleRoyaleAction action,
                               List<EngineEvent> engineEvents, boolean resourceTransferred) {
        EngineEvent actionEvent = engineEvents.stream()
                .filter(event -> "BATTLE_ACTION_EXECUTED".equals(event.type())).findFirst().orElse(null);
        int damage = actionEvent == null ? 0 : number(actionEvent.payload().get("damage"));
        String region = actionEvent == null ? null : string(actionEvent.payload().get("region"));
        if (action.type() == BattleActionType.ATTACK && action.targetPlayerId() != null) {
            add(state, action.actorPlayerId(), BattleMemoryType.I_ATTACKED_PLAYER,
                    action.actorPlayerId(), action.targetPlayerId(),
                    "我攻击了玩家" + action.targetPlayerId() + "，造成" + damage + "点伤害",
                    85, region, 100, false);
            add(state, action.targetPlayerId(), BattleMemoryType.PLAYER_ATTACKED_ME,
                    action.actorPlayerId(), action.targetPlayerId(),
                    "玩家" + action.actorPlayerId() + "攻击了我，造成" + damage + "点伤害",
                    100, region, 100, false);
        }
        if (action.type() == BattleActionType.TRADE && resourceTransferred && action.targetPlayerId() != null) {
            add(state, action.actorPlayerId(), BattleMemoryType.RESOURCE_GIVEN,
                    action.actorPlayerId(), action.targetPlayerId(), "我向玩家" + action.targetPlayerId() + "赠送了食物",
                    70, region, 100, false);
            add(state, action.targetPlayerId(), BattleMemoryType.RESOURCE_RECEIVED,
                    action.actorPlayerId(), action.targetPlayerId(), "玩家" + action.actorPlayerId() + "向我赠送了食物",
                    75, region, 100, false);
        }
        for (EngineEvent event : engineEvents) {
            if ("BATTLE_PLAYER_ELIMINATED".equals(event.type())) {
                Long eliminatedId = longNumber(event.payload().get("playerId"));
                for (var player : state.contendingPlayers()) {
                    add(state, player.getPlayerId(), BattleMemoryType.PLAYER_ELIMINATED,
                            event.actorPlayerId(), eliminatedId, "玩家" + eliminatedId + "已被淘汰",
                            90, null, 100, false);
                }
            } else if ("BATTLE_SUPPLY_DROP".equals(event.type())) {
                String dropRegion = string(event.payload().get("region"));
                for (var player : state.contendingPlayers()) {
                    add(state, player.getPlayerId(), BattleMemoryType.AIRDROP_EVENT,
                            null, null, "公开空投抵达" + dropRegion + "区", 65, dropRegion, 100, false);
                }
            }
        }
    }

    public void rememberMessage(BattleRoyaleGameState state, BattleMessage message) {
        if (message == null) return;
        String claim = "玩家" + message.getSenderPlayerId() + "说：\"" + message.getContent() + "\"";
        add(state, message.getSenderPlayerId(), BattleMemoryType.MESSAGE_SENT,
                message.getSenderPlayerId(), message.getRecipientPlayerId(), claim,
                60, null, 100, false);
        if (message.getChannel() == BattleCommunicationChannel.PRIVATE) {
            add(state, message.getRecipientPlayerId(), BattleMemoryType.MESSAGE_RECEIVED,
                    message.getSenderPlayerId(), message.getRecipientPlayerId(), claim,
                    70, null, 100, false);
            return;
        }
        List<Long> audience = message.getAudiencePlayerIds() == null || message.getAudiencePlayerIds().isEmpty()
                ? state.contendingPlayers().stream().map(player -> player.getPlayerId()).toList()
                : message.getAudiencePlayerIds();
        for (Long playerId : audience) {
            if (Objects.equals(playerId, message.getSenderPlayerId())) continue;
            add(state, playerId, BattleMemoryType.PUBLIC_MESSAGE_HEARD,
                    message.getSenderPlayerId(), null, claim, 55, null, 100, false);
        }
    }

    public List<BattleMemoryEntry> memoriesFor(BattleRoyaleGameState state, Long playerId) {
        return List.copyOf(initializer.socialState(state, playerId).getMemory().getEntries());
    }

    /** 记录一个已经由权威规则确认、且该玩家确实能够知道的社会事实。 */
    public void rememberFact(BattleRoyaleGameState state, Long ownerId, BattleMemoryType type,
                             Long sourceId, Long targetId, String summary, int importance,
                             String region) {
        add(state, ownerId, type, sourceId, targetId, summary, importance, region, 100, false);
    }

    /** 记录未经验证的主张，保留传播链上的可信度。 */
    public void rememberClaim(BattleRoyaleGameState state, Long ownerId, BattleMemoryType type,
                              Long sourceId, Long targetId, String summary, int importance,
                              String region, int certainty) {
        add(state, ownerId, type, sourceId, targetId, summary, importance, region, certainty, false);
    }

    private void add(BattleRoyaleGameState state, Long ownerId, BattleMemoryType type,
                     Long sourceId, Long targetId, String summary, int importance,
                     String region, int certainty, boolean replaceSameRound) {
        if (ownerId == null) return;
        BattleAgentSocialState social = initializer.socialState(state, ownerId);
        List<BattleMemoryEntry> entries = social.getMemory().getEntries();
        if (replaceSameRound) {
            entries.removeIf(entry -> entry.getRound() == state.getRoundNo() && entry.getType() == type
                    && Objects.equals(entry.getSourcePlayerId(), sourceId));
        }
        long discriminator = entries.stream().mapToLong(BattleMemoryEntry::getSequenceNo).filter(value -> value == state.getSequenceNo()).count();
        entries.add(BattleMemoryEntry.builder()
                .id("battle-memory-" + ownerId + "-" + state.getSequenceNo() + "-" + type + "-" + discriminator)
                .round(state.getRoundNo()).sourcePlayerId(sourceId).targetPlayerId(targetId)
                .type(type).summary(summary).importance(clamp(importance)).sequenceNo(state.getSequenceNo())
                .relatedRegion(region).certainty(clamp(certainty)).build());
        trim(entries);
    }

    private void trim(List<BattleMemoryEntry> entries) {
        while (entries.size() > MAX_ENTRIES_PER_AGENT) {
            BattleMemoryEntry removable = entries.stream().min(Comparator
                    .comparingInt(BattleMemoryEntry::getImportance)
                    .thenComparingLong(BattleMemoryEntry::getSequenceNo)).orElse(entries.get(0));
            entries.remove(removable);
        }
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private Long longNumber(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
