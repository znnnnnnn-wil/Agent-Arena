package com.zn.agentarena.modules.lineup.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 从现有对局保存阵容模板。 */
@Data
public class CreateLineupTemplateRequest {
    /** 名称 */
    @NotBlank
    @Size(max = 64)
    private String name;
}
