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

    /** 当前白天投票 */
    @Builder.Default
    private Map<Long, Long> votes = new LinkedHashMap<>();

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
