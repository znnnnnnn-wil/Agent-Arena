package com.zn.agentarena.modules.game.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 同配置重赛时允许覆盖的字段。 */
@Data
public class RematchGameRequest {
    @Size(max = 128)
    private String name;
    /** 是否公开游戏 */
    private Boolean publicGame;
}
