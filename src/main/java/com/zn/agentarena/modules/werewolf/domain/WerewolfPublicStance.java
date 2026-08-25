package com.zn.agentarena.modules.werewolf.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 可公开恢复的玩家最新立场；不包含真实身份、夜间信息或模型内部推理。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WerewolfPublicStance {
    /** 玩家ID */
    private Long playerId;
    /** 座位号 */
    private Integer seatNo;
    /** 公开声明 */
    @Builder.Default
    private List<String> claims = new ArrayList<>();
    /** 怀疑目标列表 */
    @Builder.Default
    private List<Integer> suspects = new ArrayList<>();
    /** 支持目标列表 */
    @Builder.Default
    private List<Integer> supports = new ArrayList<>();
    /** voteIntentSeatNo 字段 */
    private Integer voteIntentSeatNo;
    /** 理由标签 */
    @Builder.Default
    private List<String> reasonTags = new ArrayList<>();
    /** 理由摘要 */
    private String reasonSummary;
    /** 最近动作 */
    private String lastAction;
    /** updatedRound 字段 */
    private int updatedRound;
    /** confidence 字段 */
    private double confidence;
}
