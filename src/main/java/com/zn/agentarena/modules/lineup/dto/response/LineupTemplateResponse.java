package com.zn.agentarena.modules.lineup.dto.response;

import com.zn.agentarena.common.enums.GameMode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
/**
 * 面向客户端返回的阵容模板信息。
 */
public class LineupTemplateResponse {
    private Long id;
    /** 名称 */
    private String name;
    /** 游戏模式 */
    private GameMode mode;
    /** 玩家数量 */
    private Integer playerCount;
    /** 配置模型的席位数 */
    private Integer modelSeatCount;
    /** 配置人格的席位数 */
    private Integer personaSeatCount;
    /** 是否公开游戏 */
    private Boolean publicGame;
    /** 玩家名称列表 */
    private List<String> playerNames;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
