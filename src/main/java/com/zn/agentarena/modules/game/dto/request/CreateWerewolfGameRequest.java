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
}
