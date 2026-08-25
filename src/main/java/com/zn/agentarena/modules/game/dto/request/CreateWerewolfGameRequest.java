package com.zn.agentarena.modules.game.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 创建狼人杀游戏请求。 */
@Data
public class CreateWerewolfGameRequest {
    /** 对局名称。 */
    @NotBlank
    @Size(max = 128)
    private String name;
    /** 是否公开该对局。 */
    @NotNull
    private Boolean publicGame;
    /** 分配给 AI 玩家的模型配置 ID 列表。 */
    @Valid
    private List<@Positive Long> modelConfigIds;
    /** 分配给席位的人格配置 ID；允许与模型配置独立组合。 */
    @Valid
    private List<@Positive Long> agentProfileIds;
    /** 每个席位的显示名称；为空时使用默认名称。 */
    @Size(max = 8)
    private List<@Size(max = 64) String> playerDisplayNames;
}
