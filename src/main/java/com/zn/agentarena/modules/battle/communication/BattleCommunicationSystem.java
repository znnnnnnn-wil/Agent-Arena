package com.zn.agentarena.modules.battle.communication;

import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.social.BattleSocialStateInitializer;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 校验、投递并持久化公开或私密消息，不判断消息内容真伪。 */
@Component
@RequiredArgsConstructor
public class BattleCommunicationSystem {
    public static final int MAX_CONTENT_LENGTH = 240;
    public static final int MAX_HISTORY = 100;

    private final BattleSocialStateInitializer initializer;

    public BattleCommunicationResult deliver(BattleRoyaleGameState state, Long senderPlayerId,
                                              BattleCommunicationDecision decision) {
        initializer.ensure(state);
        BattlePlayerState sender = state.findPlayer(senderPlayerId);
        if (sender == null || sender.isEliminated() || decision == null || decision.channel() == null
                || decision.content() == null || decision.content().isBlank()
                || decision.content().length() > MAX_CONTENT_LENGTH) {
            return BattleCommunicationResult.ignored();
        }
        BattleCommunicationChannel channel;
        try {
            channel = BattleCommunicationChannel.valueOf(decision.channel().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return BattleCommunicationResult.ignored();
        }
        Long recipientId = decision.targetPlayerId();
        if ((channel == BattleCommunicationChannel.PUBLIC || channel == BattleCommunicationChannel.LOCAL)
                && recipientId != null) {
            return BattleCommunicationResult.ignored();
        }
        if (channel == BattleCommunicationChannel.PRIVATE) {
            BattlePlayerState recipient = state.findPlayer(recipientId);
            if (recipient == null || recipient.isEliminated() || Objects.equals(senderPlayerId, recipientId)) {
                return BattleCommunicationResult.ignored();
            }
        }

        List<Long> audience = switch (channel) {
            case PUBLIC -> state.contendingPlayers().stream().map(BattlePlayerState::getPlayerId).toList();
            case LOCAL -> state.contendingPlayers().stream()
                    .filter(player -> Objects.equals(player.getRegion(), sender.getRegion()))
                    .map(BattlePlayerState::getPlayerId).toList();
            case PRIVATE -> List.of(senderPlayerId, recipientId);
        };

        long eventSequence = state.getSequenceNo() + 1;
        String content = decision.content().trim();
        BattleMessage message = BattleMessage.builder()
                .id("battle-message-" + state.getGameId() + "-" + eventSequence)
                .round(state.getRoundNo()).sequenceNo(eventSequence)
                .senderPlayerId(senderPlayerId).recipientPlayerId(recipientId)
                .channel(channel).content(content).region(sender.getRegion())
                .audiencePlayerIds(new java.util.ArrayList<>(audience)).build();
        state.getCommunicationHistory().add(message);
        while (state.getCommunicationHistory().size() > MAX_HISTORY) state.getCommunicationHistory().remove(0);
        state.setSequenceNo(eventSequence);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", message.getId());
        payload.put("channel", channel.name());
        payload.put("senderPlayerId", senderPlayerId);
        payload.put("targetPlayerId", recipientId);
        payload.put("region", sender.getRegion());
        payload.put("content", content);
        Map<String, Object> publicPayload = payload;
        String eventType = channel == BattleCommunicationChannel.LOCAL
                ? "BATTLE_LOCAL_MESSAGE" : "BATTLE_PUBLIC_MESSAGE";
        if (channel == BattleCommunicationChannel.PRIVATE) {
            eventType = "BATTLE_PRIVATE_MESSAGE";
            publicPayload = new LinkedHashMap<>();
            publicPayload.put("messageId", message.getId());
            publicPayload.put("channel", channel.name());
            publicPayload.put("senderPlayerId", senderPlayerId);
            publicPayload.put("targetPlayerId", recipientId);
            publicPayload.put("summary", sender.getDisplayName() + " 发送了一条私密消息");
        }
        return new BattleCommunicationResult(message,
                new EngineEvent(eventType, senderPlayerId, payload, publicPayload));
    }

    public List<BattleMessage> messagesVisibleTo(BattleRoyaleGameState state, Long playerId) {
        initializer.ensure(state);
        return state.getCommunicationHistory().stream()
                .filter(message -> message.getAudiencePlayerIds() != null && !message.getAudiencePlayerIds().isEmpty()
                        ? message.getAudiencePlayerIds().contains(playerId)
                        : message.getChannel() == BattleCommunicationChannel.PUBLIC
                            || Objects.equals(message.getSenderPlayerId(), playerId)
                            || Objects.equals(message.getRecipientPlayerId(), playerId))
                .toList();
    }
}
