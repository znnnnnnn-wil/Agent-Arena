package com.zn.agentarena.modules.agent.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
/**
 * 面向客户端返回的 Agent 人格档案信息。
 */
public class AgentProfileResponse {
    private Long id;
    /** 人格代码 */
    private String code;
    /** 名称 */
    private String name;
    /** 描述 */
    private String description;
    /** 人格特质标签 */
    private List<String> traits;
    /** 表达风格 */
    private String speakingStyle;
    /** 风险偏好 */
    private String riskPreference;
    /** 推理偏好 */
    private String reasoningPreference;
    /** builtIn 字段 */
    private Boolean builtIn;
    /** 行为指引 */
    private String behaviorGuidance;
    /** 头像表情 */
    private String avatarEmoji;
    /** 主题颜色 */
    private String accentColor;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
