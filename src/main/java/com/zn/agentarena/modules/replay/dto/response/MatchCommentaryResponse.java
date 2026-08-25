package com.zn.agentarena.modules.replay.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 服务端生成的单条赛事解说投影。 */
@Data
@Builder
public class MatchCommentaryResponse {
    private String itemId;
    /** 事件序号 */
    private Long sequenceNo;
    /** endSequenceNo 字段 */
    private Long endSequenceNo;
    /** 回合号 */
    private Integer roundNo;
    /** 游戏阶段 */
    private String phase;
    /** 内容分类 */
    private String category;
    /** 标题 */
    private String title;
    /** 解说文本 */
    private String narration;
    /** decisionSummary 字段 */
    private String decisionSummary;
    /** 重要程度 */
    private Integer importance;
    /** 行动玩家ID */
    private Long actorPlayerId;
    /** 行动玩家名称 */
    private String actorName;
    /** 目标玩家ID */
    private Long targetPlayerId;
    /** 目标玩家名称 */
    private String targetName;
    /** highlightClipId 字段 */
    private String highlightClipId;
    /** 关联事件类型 */
    private List<String> eventTypes;
    /** 指标数据 */
    private Map<String, Object> metrics;
}
