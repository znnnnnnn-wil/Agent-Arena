package com.zn.agentarena.modules.replay.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 仅由白天公开事件确定性恢复的狼人杀社会关系投影。 */
@Data
@Builder
public class WerewolfSituationResponse {
    private Integer roundNo;
    /** 游戏阶段 */
    private String phase;
    /** 玩家列表 */
    private List<PlayerStance> players;
    /** suspicionRanking 字段 */
    private List<Tally> suspicionRanking;
    /** supportRanking 字段 */
    private List<Tally> supportRanking;
    /** voteIntentDistribution 字段 */
    private List<Tally> voteIntentDistribution;
    /** controversialPlayers 字段 */
    private List<PlayerRef> controversialPlayers;
    /** mostSuspected 字段 */
    private PlayerRef mostSuspected;
    /** relationships 字段 */
    private List<Relationship> relationships;

    @Data
    @Builder
    public static class PlayerRef {
        /** 座位号 */
        private Integer seatNo;
        /** 显示名称 */
        private String displayName;
    }

    @Data
    @Builder
    public static class PlayerStance {
        /** 座位号 */
        private Integer seatNo;
        /** 显示名称 */
        private String displayName;
        /** 公开声明 */
        private List<String> claims;
        /** 怀疑目标列表 */
        private List<PlayerRef> suspects;
        /** 支持目标列表 */
        private List<PlayerRef> supports;
        /** voteIntent 字段 */
        private PlayerRef voteIntent;
        /** 理由标签 */
        private List<String> reasonTags;
        /** 理由摘要 */
        private String reasonSummary;
        /** 最近动作 */
        private String lastAction;
        /** updatedRound 字段 */
        private Integer updatedRound;
    }

    @Data
    @Builder
    public static class Tally {
        /** player 字段 */
        private PlayerRef player;
        /** count 字段 */
        private Integer count;
    }

    @Data
    @Builder
    public static class Relationship {
        /** type 字段 */
        private String type;
        /** from 字段 */
        private PlayerRef from;
        /** to 字段 */
        private PlayerRef to;
    }
}
