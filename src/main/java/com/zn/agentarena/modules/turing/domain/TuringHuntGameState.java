package com.zn.agentarena.modules.turing.domain;

import com.zn.agentarena.modules.game.engine.GameState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 图灵潜伏局完整内部快照。所有新增集合都有旧 JSON 安全默认值。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TuringHuntGameState implements GameState {
    /** 游戏ID */
    private Long gameId;
    /** 事件序号 */
    private long sequenceNo;
    /** 回合号 */
    private int roundNo;
    /** 最大回合数 */
    @Builder.Default
    private int maxRounds = 3;
    /** 游戏阶段 */
    @Builder.Default
    private TuringHuntPhase phase = TuringHuntPhase.WAITING;
    /** humanPlayerId 字段 */
    private Long humanPlayerId;
    /** currentActorPlayerId 字段 */
    private Long currentActorPlayerId;
    /** currentQuestion 字段 */
    private String currentQuestion;
    /** answerSubmissionOrder 字段 */
    @Builder.Default
    private List<Long> answerSubmissionOrder = new ArrayList<>();
    /** answersByPlayerId 字段 */
    @Builder.Default
    private Map<Long, TuringHuntAnswer> answersByPlayerId = new LinkedHashMap<>();
    /** suspicionsByPlayerId 字段 */
    @Builder.Default
    private Map<Long, TuringHuntSuspicion> suspicionsByPlayerId = new LinkedHashMap<>();
    /** primarySuspectByPlayerId 字段 */
    @Builder.Default
    private Map<Long, Long> primarySuspectByPlayerId = new LinkedHashMap<>();
    /** questionQueue 字段 */
    @Builder.Default
    private List<TuringHuntQuestionPair> questionQueue = new ArrayList<>();
    /** questionIndex 字段 */
    private int questionIndex;
    /** currentQuestionerPlayerId 字段 */
    private Long currentQuestionerPlayerId;
    /** currentQuestionTargetPlayerId 字段 */
    private Long currentQuestionTargetPlayerId;
    /** questions 字段 */
    @Builder.Default
    private Map<Long, TuringHuntQuestion> questions = new LinkedHashMap<>();
    /** responses 字段 */
    @Builder.Default
    private Map<Long, TuringHuntResponse> responses = new LinkedHashMap<>();
    /** defenses 字段 */
    @Builder.Default
    private Map<Long, TuringHuntDefense> defenses = new LinkedHashMap<>();
    /** votesByPlayerId 字段 */
    @Builder.Default
    private Map<Long, TuringHuntVote> votesByPlayerId = new LinkedHashMap<>();
    /** revoteCandidates 字段 */
    @Builder.Default
    private List<Long> revoteCandidates = new ArrayList<>();
    /** eliminatedPlayerIds 字段 */
    @Builder.Default
    private List<Long> eliminatedPlayerIds = new ArrayList<>();
    /** wrongAiEliminationCount 字段 */
    private int wrongAiEliminationCount;
    /** 获胜方 */
    private TuringHuntWinner winner;
    /** 结束原因 */
    private TuringHuntFinishedReason finishedReason;
    /** 随机种子 */
    private long randomSeed;
    /** 玩家列表 */
    @Builder.Default
    private List<TuringHuntPlayerState> players = new ArrayList<>();
    /** controversyPlayerIds 字段 */
    @Builder.Default
    private List<Long> controversyPlayerIds = new ArrayList<>();
    /** publicSuspicionCounts 字段 */
    @Builder.Default
    private Map<Long, Integer> publicSuspicionCounts = new LinkedHashMap<>();
    /** publicVoteCounts 字段 */
    @Builder.Default
    private Map<Long, Integer> publicVoteCounts = new LinkedHashMap<>();
    /** revote 字段 */
    private boolean revote;
    /** defenseQueue 字段 */
    @Builder.Default
    private List<Long> defenseQueue = new ArrayList<>();
    /** defenseIndex 字段 */
    private int defenseIndex;
    /** defensePurpose 字段 */
    @Builder.Default
    private TuringHuntDefensePurpose defensePurpose = TuringHuntDefensePurpose.CONTROVERSY;
    /** previousVotes 字段 */
    @Builder.Default
    private Map<Long, Long> previousVotes = new LinkedHashMap<>();
    /** previousTieCandidates 字段 */
    @Builder.Default
    private List<Long> previousTieCandidates = new ArrayList<>();
    /** completedRounds 字段 */
    @Builder.Default
    private List<TuringHuntRoundState> completedRounds = new ArrayList<>();
    /** currentExchanges 字段 */
    @Builder.Default
    private List<TuringHuntQuestionExchange> currentExchanges = new ArrayList<>();
    /** currentDefenseHistory 字段 */
    @Builder.Default
    private List<TuringHuntDefenseRecord> currentDefenseHistory = new ArrayList<>();
    /** currentVoteHistory 字段 */
    @Builder.Default
    private List<Map<Long, TuringHuntVote>> currentVoteHistory = new ArrayList<>();
    /** currentVoteCountHistory 字段 */
    @Builder.Default
    private List<Map<Long, Integer>> currentVoteCountHistory = new ArrayList<>();
    /** pendingVoteCounts 字段 */
    @Builder.Default
    private Map<Long, Integer> pendingVoteCounts = new LinkedHashMap<>();
    /** pendingTopCandidates 字段 */
    @Builder.Default
    private List<Long> pendingTopCandidates = new ArrayList<>();
    /** pendingEliminationPlayerId 字段 */
    private Long pendingEliminationPlayerId;
    /** lastQuestionerPlayerId 字段 */
    private Long lastQuestionerPlayerId;
    /** currentQuestionSubmitted 字段 */
    private boolean currentQuestionSubmitted;
    /** currentResponseSubmitted 字段 */
    private boolean currentResponseSubmitted;

    /**
     * 按玩家标识查找状态中的玩家。
     *
     * @param playerId 玩家标识
     * @return 匹配的玩家；不存在时返回 {@code null}
     */
    public TuringHuntPlayerState findPlayer(Long playerId) {
        if (playerId == null || players == null) return null;
        return players.stream().filter(player -> playerId.equals(player.getPlayerId())).findFirst().orElse(null);
    }

    /**
     * 按座位号查找状态中的玩家。
     *
     * @param seatNo 座位号
     * @return 匹配的玩家；不存在时返回 {@code null}
     */
    public TuringHuntPlayerState findSeat(Integer seatNo) {
        if (seatNo == null || players == null) return null;
        return players.stream().filter(player -> seatNo.equals(player.getSeatNo())).findFirst().orElse(null);
    }

    /**
     * 获取仍存活的玩家，并按座位号排序。
     *
     * @return 存活玩家列表
     */
    public List<TuringHuntPlayerState> alivePlayers() {
        return players == null ? List.of() : players.stream()
                .filter(TuringHuntPlayerState::isAlive)
                .sorted(java.util.Comparator.comparing(TuringHuntPlayerState::getSeatNo))
                .toList();
    }
}
