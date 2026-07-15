package com.zn.agentarena.modules.replay.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 回放事件响应。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Data
@Builder
public class ReplayEventResponse {
    /** 事件在对局内的顺序编号。 */
    private Long sequenceNo;
    /** 事件类型。 */
    private String eventType;
    /** 执行该事件的游戏玩家 ID。 */
    private Long actorPlayerId;
    /** 事件发生的回合数。 */
    private Integer roundNo;
    /** 事件发生时所属的游戏阶段。 */
    private String phase;
    /** 可用于回放展示的事件数据。 */
    private Map<String, Object> payload;
    /** 事件记录创建时间。 */
    private LocalDateTime createTime;
}
