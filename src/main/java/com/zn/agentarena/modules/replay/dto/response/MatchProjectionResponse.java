package com.zn.agentarena.modules.replay.dto.response;

import com.zn.agentarena.common.enums.GameMode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 由服务端事件日志确定性生成的赛事内容投影。 */
@Data
@Builder
public class MatchProjectionResponse {
    private Integer schemaVersion;
    /** 游戏ID */
    private Long gameId;
    /** 游戏模式 */
    private GameMode mode;
    /** generatedThroughSequence 字段 */
    private Long generatedThroughSequence;
    /** commentary 字段 */
    private List<MatchCommentaryResponse> commentary;
    /** highlights 字段 */
    private List<HighlightClipResponse> highlights;
    /** 狼人杀公开关系投影；大逃杀为空。 */
    private WerewolfSituationResponse werewolfSituation;
}
