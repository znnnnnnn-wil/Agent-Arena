package com.zn.agentarena.modules.lineup.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 使用阵容模板创建新对局时允许覆盖的少量字段。 */
@Data
public class CreateGameFromTemplateRequest {
    @Size(max = 128)
    private String name;
    /** 是否公开游戏 */
    private Boolean publicGame;
}
