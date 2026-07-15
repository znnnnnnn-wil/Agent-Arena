package com.zn.agentarena.modules.replay.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 对局总结响应。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Data
@Builder
public class GameSummaryResponse {
    /** 对局 ID。 */
    private Long gameId;
    /** 对局结束时的状态。 */
    private String status;
    /** 获胜方或获胜者标识。 */
    private String winner;
    /** 对局记录的事件总数。 */
    private Integer eventCount;
    /** 对局中的淘汰次数。 */
    private Integer eliminationCount;
    /** 用于展示的关键事件摘要。 */
    private List<String> keyEvents;
}
