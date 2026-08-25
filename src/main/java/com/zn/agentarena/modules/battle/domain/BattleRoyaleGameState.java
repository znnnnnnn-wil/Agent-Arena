package com.zn.agentarena.modules.battle.domain;

import com.zn.agentarena.modules.game.engine.GameState;
import com.zn.agentarena.modules.battle.communication.BattleMessage;
import com.zn.agentarena.modules.battle.social.BattleAgentSocialState;
import com.zn.agentarena.modules.battle.alliance.BattleAlliance;
import com.zn.agentarena.modules.battle.promise.BattlePromise;
import com.zn.agentarena.modules.battle.trade.BattleTradeProposal;
import com.zn.agentarena.modules.battle.goal.BattleHiddenGoal;
import com.zn.agentarena.modules.battle.objective.BattleCooperativeObjective;
import com.zn.agentarena.modules.battle.information.BattleRumor;
import com.zn.agentarena.modules.battle.information.BattleInformationTradeProposal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    /** 游戏ID */
    private Long gameId;
    /** 事件序号 */
    private long sequenceNo;
    /** 当前回合号 */
    private int roundNo;
    /** 本回合已执行动作数 */
    private int actionsInRound;
    /** 本回合开始时冻结的行动队列，避免淘汰导致列表左移并跳过玩家。 */
    @Builder.Default
    private List<Long> roundTurnOrder = new ArrayList<>();
    /** 最大回合数 */
    private int maxRounds;
    /** 随机种子 */
    private long randomSeed;
    /** 仅随真实战术动作递增，社会事件数量不会再改变后续战斗/搜索随机结果。 */
    private long randomActionNo;
    /** 可用区域列表 */
    @Builder.Default
    private List<String> regions = new ArrayList<>(List.of("A", "B", "C", "D", "E"));
    /** 玩家状态列表 */
    @Builder.Default
    private List<BattlePlayerState> players = new ArrayList<>();
    /** 每个玩家独立的局内记忆与主观关系；键为观察者玩家ID。 */
    @Builder.Default
    private Map<Long, BattleAgentSocialState> socialStates = new LinkedHashMap<>();
    /** 局内通信历史；PRIVATE 内容仅由通信系统按参与者过滤。 */
    @Builder.Default
    private List<BattleMessage> communicationHistory = new ArrayList<>();
    /** 正式联盟及其提案。 */
    @Builder.Default
    private List<BattleAlliance> alliances = new ArrayList<>();
    /** 可验证承诺及其履约状态。 */
    @Builder.Default
    private List<BattlePromise> promises = new ArrayList<>();
    /** 待处理及已完成的协商交易。 */
    @Builder.Default
    private List<BattleTradeProposal> tradeProposals = new ArrayList<>();
    @Builder.Default
    private List<BattleHiddenGoal> hiddenGoals = new ArrayList<>();
    @Builder.Default
    private List<BattleCooperativeObjective> cooperativeObjectives = new ArrayList<>();
    @Builder.Default
    private List<BattleRumor> rumors = new ArrayList<>();
    @Builder.Default
    private List<BattleInformationTradeProposal> informationTradeProposals = new ArrayList<>();
    /** 当前安全区域 */
    private String safeRegion;
    /** 安全区域集合 */
    @Builder.Default
    private List<String> safeRegions = new ArrayList<>(List.of("A", "B", "C", "D", "E"));
    /** 本局公开缩圈顺序；首项为最终安全区，末项最先出圈。 */
    @Builder.Default
    private List<String> ringOrder = new ArrayList<>();
    /** 毒圈阶段 */
    @Builder.Default
    private int ringStage = 1;
    /** 补给投放区域 */
    private String supplyDropRegion;
    /** 最近补给投放回合 */
    private int lastSupplyDropRound;
    /** 各区域累计搜索次数，用于物资枯竭与收益衰减。 */
    @Builder.Default
    private Map<String, Integer> regionSearchCounts = new LinkedHashMap<>();
    /** 当前空投占领者。 */
    private Long supplyDropClaimPlayerId;
    /** 空投占领还需坚持的行动数。 */
    private int supplyDropClaimActionsRemaining;
    /** 本回合是否发生过战斗。 */
    private boolean combatOccurredThisRound;
    /** 连续没有战斗的完整回合数。 */
    private int roundsWithoutCombat;
    /** 获胜玩家ID */
    private Long winnerPlayerId;

    @Override
    /**
     * 获取该状态所属的游戏标识。
     *
     * @return 游戏标识
     */
    public Long getGameId() {
        return gameId;
    }

    /**
     * 按玩家标识查找状态中的玩家。
     *
     * @param playerId 玩家标识
     * @return 匹配的玩家；不存在时返回 {@code null}
     */
    public BattlePlayerState findPlayer(Long playerId) {
        return players.stream().filter(item -> playerId != null && playerId.equals(item.getPlayerId()))
                .findFirst().orElse(null);
    }

    /**
     * 获取仍存活的玩家，并按座位号排序。
     *
     * @return 存活玩家列表
     */
    public List<BattlePlayerState> alivePlayers() {
        return players.stream().filter(BattlePlayerState::isActive).toList();
    }

    /** 仍参与胜负结算的玩家，包括倒地但尚未淘汰者。 */
    public List<BattlePlayerState> contendingPlayers() {
        return players.stream().filter(player -> !player.isEliminated()).toList();
    }

    /** 每轮可获得决策机会的玩家；倒地玩家只能 SKIP 并发送消息。 */
    public List<BattlePlayerState> turnPlayers() {
        return contendingPlayers();
    }
}
