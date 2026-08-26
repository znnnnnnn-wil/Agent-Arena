package com.zn.agentarena.modules.agent.domain;

import java.util.List;

/**
 * 狼人杀内置轻量人格定义。
 *
 * @param code code 字段
 * @param name name 字段
 * @param description description 字段
 * @param speakingStyle speakingStyle 字段
 * @param riskPreference riskPreference 字段
 * @param reasoningPreference reasoningPreference 字段
 * @param publicTags publicTags 字段
 * @param promptInstruction promptInstruction 字段
 * @param avatarEmoji avatarEmoji 字段
 * @param accentColor accentColor 字段
 */
public record AgentPersonaPreset(
        String code,
        String name,
        String description,
        String speakingStyle,
        String riskPreference,
        String reasoningPreference,
        List<String> publicTags,
        String promptInstruction,
        String avatarEmoji,
        String accentColor
) {
    /**
     * 将预设定义转换为可写入对局快照的人格信息。
     *
     * @param profileId 关联的人格档案标识
     * @return 不可变的人格快照
     */
    public AgentPersonaSnapshot snapshot(Long profileId) {
        return AgentPersonaSnapshot.builder().profileId(profileId).code(code).name(name)
                .description(description).traits(publicTags).speakingStyle(speakingStyle)
                .riskPreference(riskPreference).reasoningPreference(reasoningPreference)
                .behaviorGuidance(promptInstruction).promptInstruction(promptInstruction)
                .avatarEmoji(avatarEmoji).accentColor(accentColor).builtIn(true).build();
    }
}
