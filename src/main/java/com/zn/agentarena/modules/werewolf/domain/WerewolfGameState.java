package com.zn.agentarena.modules.werewolf.domain;

import com.zn.agentarena.modules.game.engine.GameState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * 狼人杀完整游戏状态。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WerewolfGameState implements GameState {

    /** 游戏ID */
    private Long gameId;

    /** 当前事件序号 */
    private long sequenceNo;

    /** 当前轮次 */
    private int roundNo;

    /** 当前游戏阶段 */
    private WerewolfPhase phase;

    /** 全部玩家状态 */
    @Builder.Default
    private List<WerewolfPlayerState> players = new ArrayList<>();

    /** 白天发言顺序中的当前位置 */
    private int discussionIndex;

    /** 0/1 表示旧版顺序发言流程，2 表示启用质询与回应。 */
    private int discussionVersion;

    /** 当天由确定性规则选出的质询队列。 */
    @Builder.Default
    private List<WerewolfQuestionPair> questionQueue = new ArrayList<>();

    private int questionIndex;

    /** finalStatementIndex 字段 */
    private int finalStatementIndex;

    /** 当前白天投票 */
    @Builder.Default
    private Map<Long, Long> votes = new LinkedHashMap<>();

    /** 白天公开输出形成的最新立场，旧快照缺少该字段时默认为空。 */
    @Builder.Default
    private Map<Long, WerewolfPublicStance> publicStances = new LinkedHashMap<>();

    /** 上一轮正式投票，用于识别投票意向变化。 */
    @Builder.Default
    private Map<Long, Long> previousVotes = new LinkedHashMap<>();

    /** 公开身份声明或投票意向发生变化的玩家。 */
    @Builder.Default
    private Set<Long> claimChangedPlayerIds = new LinkedHashSet<>();
    @Builder.Default
    private Set<Long> voteIntentChangedPlayerIds = new LinkedHashSet<>();

    /** 狼人夜间选择的目标 */
    private Long nightKillTargetId;

    /** 女巫毒杀目标 */
    private Long poisonTargetId;

    /** 本夜守卫保护目标。 */
    private Long guardedTargetId;

    /** 上一夜守卫保护目标。 */
    private Long lastGuardTargetId;

    /** 女巫是否救人 */
    private boolean nightSaved;

    /** 公开发言、投票和出局历史。 */
    @Builder.Default
    private List<String> publicHistory = new ArrayList<>();

    /** 获胜阵营 */
    private WerewolfWinner winner;

    /** 根据玩家ID查找玩家。 */
    public WerewolfPlayerState findPlayer(Long playerId) {
        if (playerId == null) {
            return null;
        }
        return players.stream()
                .filter(player -> playerId.equals(player.getPlayerId()))
                .findFirst()
                .orElse(null);
    }

    /** 获取存活玩家。 */
    public List<WerewolfPlayerState> alivePlayers() {
        return players.stream().filter(WerewolfPlayerState::isAlive).toList();
    }
}
