package com.zn.agentarena.modules.game.dto.response;

import com.zn.agentarena.common.enums.GameMode;
import com.zn.agentarena.common.enums.GameStatus;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    /** 下一位应当行动的玩家；公开狼人杀夜间会隐藏。 */
    private Long currentActorPlayerId;
    /** 下一位应当行动的玩家名称；公开狼人杀夜间会使用通用文案。 */
    private String currentActorName;
    /** 当前等待的决策类型。 */
    private String currentActionType;
    /** 当前行动者的公开安全模型供应商。 */
    private ModelProvider currentActorModelProvider;
    /** 当前行动者的公开安全模型名称。 */
    private String currentActorModelName;
    /** 当前行动者模型配置的展示名称。 */
    private String currentActorModelDisplayName;
    /** 当前行动者是否使用规则 AI。 */
    private Boolean currentActorLocalStrategy;
    /** 当前行动者的公开安全人格名称；公开夜间视角隐藏。 */
    private String currentActorPersonaName;
    private String currentActorPersonaCode;

    /** 图灵潜伏局安全投影；其他模式均为 null。 */
    private Integer maxRounds;
    private String currentQuestion;
    private Boolean humanActionRequired;
    private String requiredHumanAction;
    /** 当前用户座位号 */
    private Integer selfSeatNo;
    /** 存活玩家数量 */
    private Integer alivePlayerCount;
    /** 误淘汰AI次数 */
    private Integer wrongAiEliminationCount;
    /** 当前公开阶段的匿名提交进度；绝不包含提交者或提交顺序。 */
    private Integer submissionCompletedCount;
    private Integer submissionRequiredCount;
    private List<Integer> controversySeatNos;
    private Map<Integer, Integer> publicSuspicionCounts;
    /** 公开票数统计 */
    private Map<Integer, Integer> publicVoteCounts;
    /** 重新投票候选座位号 */
    private List<Integer> revoteCandidateSeatNos;
    /** 当前提问者座位号 */
    private Integer currentQuestionerSeatNo;
    /** 当前被提问者座位号 */
    private Integer currentQuestionTargetSeatNo;
    /** 游戏获胜方 */
    private String winner;
    /** 游戏结束原因 */
    private String finishedReason;
    /** 身份是否已公开 */
    private Boolean identitiesRevealed;
    /** 图灵潜伏复盘摘要 */
    private Map<String, Object> turingRecap;
    /** 大逃杀公开环境与规则，其他模式为空。 */
    private BattleWorldResponse battleWorld;
}
