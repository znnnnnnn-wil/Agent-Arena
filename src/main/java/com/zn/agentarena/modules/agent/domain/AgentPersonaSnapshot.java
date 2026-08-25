package com.zn.agentarena.modules.agent.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 写入对局快照的人格副本，避免后续编辑影响已经开始的比赛。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPersonaSnapshot {
    /** 人格档案ID */
    private Long profileId;
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
    /** 行为指引 */
    private String behaviorGuidance;
    /** 仅写入模型上下文，不通过公开玩家 DTO 返回。 */
    private String promptInstruction;
    private boolean builtIn;
    private String avatarEmoji;
    private String accentColor;
}
