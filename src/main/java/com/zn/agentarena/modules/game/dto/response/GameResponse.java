package com.zn.agentarena.modules.game.dto.response;

import com.zn.agentarena.common.enums.GameMode;
import com.zn.agentarena.common.enums.GameStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 游戏基础信息响应。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Data
@Builder
public class GameResponse {
    /** 对局 ID。 */
    private Long id;
    /** 对局名称。 */
    private String name;
    /** 游戏模式。 */
    private GameMode mode;
    /** 对局当前状态。 */
    private GameStatus status;
    /** 当前游戏阶段。 */
    private String currentPhase;
    /** 当前回合数。 */
    private Integer currentRound;
    /** 对局允许的最大玩家数。 */
    private Integer maxPlayers;
    /** 是否为公开对局。 */
    private Boolean publicGame;
    /** 对局开始时间。 */
    private LocalDateTime startTime;
    /** 对局结束时间；未结束时为空。 */
    private LocalDateTime finishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
