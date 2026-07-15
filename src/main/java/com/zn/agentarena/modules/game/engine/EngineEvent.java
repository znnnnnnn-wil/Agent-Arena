package com.zn.agentarena.modules.game.engine;

import java.util.Map;

/**
 * 游戏引擎产生的领域事件。
 *
 * @param type 事件类型
 * @param actorPlayerId 行动玩家ID
 * @param payload 完整事件数据
 * @param publicPayload 公开事件数据
 * @author zhangxin
 * @date 2026/07/12
 */
public record EngineEvent(
        String type,
        Long actorPlayerId,
        Map<String, Object> payload,
        Map<String, Object> publicPayload
) {
}
