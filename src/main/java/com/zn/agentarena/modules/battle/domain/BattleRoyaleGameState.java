package com.zn.agentarena.modules.battle.domain;

import com.zn.agentarena.modules.game.engine.GameState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 简化大逃杀游戏状态。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleRoyaleGameState implements GameState {
    private Long gameId;
    private long sequenceNo;
    private int roundNo;
    private int actionsInRound;
    private int maxRounds;
    private long randomSeed;
    @Builder.Default
    private List<String> regions = new ArrayList<>(List.of("A", "B", "C", "D", "E"));
    @Builder.Default
    private List<BattlePlayerState> players = new ArrayList<>();
    private String safeRegion;
    @Builder.Default
    private List<String> safeRegions = new ArrayList<>(List.of("A", "B", "C", "D", "E"));
    @Builder.Default
    private int ringStage = 1;
    private String supplyDropRegion;
    private int lastSupplyDropRound;
    private Long winnerPlayerId;

    @Override
    public Long getGameId() {
        return gameId;
    }

    public BattlePlayerState findPlayer(Long playerId) {
        return players.stream().filter(item -> playerId != null && playerId.equals(item.getPlayerId()))
                .findFirst().orElse(null);
    }

    public List<BattlePlayerState> alivePlayers() {
        return players.stream().filter(BattlePlayerState::isAlive).toList();
    }
}
