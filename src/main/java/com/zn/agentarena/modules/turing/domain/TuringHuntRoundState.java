package com.zn.agentarena.modules.turing.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 赛后复盘所需的确定性单轮快照。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TuringHuntRoundState {
    /** 回合号 */
    private int roundNo;
    /** 问题内容 */
    private String question;
    /** answersByPlayerId 字段 */
    @Builder.Default
    private Map<Long, TuringHuntAnswer> answersByPlayerId = new LinkedHashMap<>();
    /** suspicionsByPlayerId 字段 */
    @Builder.Default
    private Map<Long, TuringHuntSuspicion> suspicionsByPlayerId = new LinkedHashMap<>();
    /** controversyPlayerIds 字段 */
    @Builder.Default
    private List<Long> controversyPlayerIds = new ArrayList<>();
    /** exchanges 字段 */
    @Builder.Default
    private List<TuringHuntQuestionExchange> exchanges = new ArrayList<>();
    /** defenses 字段 */
    @Builder.Default
    private List<TuringHuntDefenseRecord> defenses = new ArrayList<>();
    /** voteRounds 字段 */
    @Builder.Default
    private List<Map<Long, TuringHuntVote>> voteRounds = new ArrayList<>();
    /** voteCountRounds 字段 */
    @Builder.Default
    private List<Map<Long, Integer>> voteCountRounds = new ArrayList<>();
    /** tieCandidatePlayerIds 字段 */
    @Builder.Default
    private List<Long> tieCandidatePlayerIds = new ArrayList<>();
    /** eliminatedPlayerId 字段 */
    private Long eliminatedPlayerId;
    /** eliminatedHuman 字段 */
    private boolean eliminatedHuman;
}
