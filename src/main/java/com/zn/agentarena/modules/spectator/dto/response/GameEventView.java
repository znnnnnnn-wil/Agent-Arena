package com.zn.agentarena.modules.spectator.dto.response;

import com.zn.agentarena.modules.game.engine.EngineEvent;
import com.zn.agentarena.modules.spectator.service.PublicEventPolicy;

import java.time.Instant;

/**
 * 观战端可消费的游戏事件。
 *
 * @param gameId 游戏ID
 * @param sequenceNo 事件序号
 * @param type 事件类型
 * @param actorPlayerId 行动玩家ID
 * @param payload 公开事件数据
 * @param occurredAt 发生时间
 * @author zhangxin
 * @date 2026/07/12
 */
public record GameEventView(
        Long gameId,
        long sequenceNo,
        String type,
        Long actorPlayerId,
        java.util.Map<String, Object> payload,
        Instant occurredAt
) {

    public static GameEventView from(Long gameId, long sequenceNo, EngineEvent event) {
        return new GameEventView(gameId, sequenceNo, event.type(),
                PublicEventPolicy.publicActorPlayerId(event.type(), event.actorPlayerId()),
                event.publicPayload(), Instant.now());
    }
}
