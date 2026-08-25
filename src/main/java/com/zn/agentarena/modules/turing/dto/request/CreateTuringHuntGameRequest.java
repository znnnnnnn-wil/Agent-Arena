package com.zn.agentarena.modules.turing.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/** 创建一场固定为一名真人和五名 AI 的图灵潜伏局。 */
@Data
public class CreateTuringHuntGameRequest {

    /** 名称 */
    @NotBlank
    @Size(max = 128)
    private String name;

    /** 是否公开游戏 */
    @NotNull
    private Boolean publicGame;

    /** humanSeatNo 字段 */
    @NotNull
    @Min(1)
    @Max(6)
    private Integer humanSeatNo;

    @Valid
    @Size(max = 6)
    /** 按座位号配置的玩家显示名称 */
    private Map<@Min(1) @Max(6) Integer, @Size(max = 64) String> playerDisplayNames;

    @Valid
    @Size(max = 6)
    /** 按座位号配置的模型ID */
    private Map<@Min(1) @Max(6) Integer, @Positive Long> seatModelConfigIds;

    @Valid
    @Size(max = 6)
    /** 按座位号配置的 Agent 人格档案ID */
    private Map<@Min(1) @Max(6) Integer, @Positive Long> seatAgentProfileIds;
}
