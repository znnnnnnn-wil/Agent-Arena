package com.zn.agentarena.modules.replay.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 可稳定分享和定位回放区间的精彩片段。 */
@Data
@Builder
public class HighlightClipResponse {
    private String clipId;
    /** 游戏ID */
    private Long gameId;
    /** 内容分类 */
    private String category;
    /** 标题 */
    private String title;
    /** 解说文本 */
    private String narration;
    /** 重要程度 */
    private Integer importance;
    /** startSequenceNo 字段 */
    private Long startSequenceNo;
    /** endSequenceNo 字段 */
    private Long endSequenceNo;
    /** 回合号 */
    private Integer roundNo;
    /** 游戏阶段 */
    private String phase;
    /** 行动玩家ID */
    private Long actorPlayerId;
    /** 行动玩家名称 */
    private String actorName;
    /** 目标玩家ID */
    private Long targetPlayerId;
    /** 目标玩家名称 */
    private String targetName;
    /** 关联事件类型 */
    private List<String> eventTypes;
    /** 指标数据 */
    private Map<String, Object> metrics;
}
