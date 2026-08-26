package com.zn.agentarena.modules.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 创建或更新轻量 Agent 人格。 */
@Data
public class UpsertAgentProfileRequest {
    /** 名称 */
    @NotBlank
    @Size(max = 64)
    private String name;
    /** 描述 */
    @Size(max = 512)
    private String description;
    @Size(max = 5)
    private List<@NotBlank @Size(max = 32) String> traits;
    /** 表达风格 */
    @NotBlank
    @Size(max = 128)
    private String speakingStyle;
    /** 风险偏好 */
    @NotBlank
    @Pattern(regexp = "CAUTIOUS|BALANCED|BOLD")
    private String riskPreference;
    /** 行为指引 */
    @Size(max = 256)
    private String behaviorGuidance;
    /** 头像表情 */
    @Size(max = 8)
    private String avatarEmoji;
    /** 主题颜色 */
    @Pattern(regexp = "#[0-9a-fA-F]{6}")
    private String accentColor;
}
