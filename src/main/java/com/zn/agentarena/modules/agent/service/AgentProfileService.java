package com.zn.agentarena.modules.agent.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.dao.entity.AgentProfile;
import com.zn.agentarena.dao.mapper.AgentProfileMapper;
import com.zn.agentarena.dao.mapper.UserMapper;
import com.zn.agentarena.modules.agent.domain.AgentPersonaSnapshot;
import com.zn.agentarena.modules.agent.domain.AgentPersonaPreset;
import com.zn.agentarena.modules.agent.domain.AgentPersonaPresets;
import com.zn.agentarena.modules.agent.dto.request.UpsertAgentProfileRequest;
import com.zn.agentarena.modules.agent.dto.response.AgentProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Agent 人格的用户级管理服务。 */
@Service
@RequiredArgsConstructor
public class AgentProfileService {
    private final AgentProfileMapper profileMapper;
    private final UserMapper userMapper;

    @Transactional(rollbackFor = Exception.class)
    /**
     * 为用户创建自定义的人格档案。
     *
     * @param userId 档案所属用户的标识
     * @param request 人格档案内容
     * @return 已创建的人格档案
     */
    public AgentProfileResponse create(Long userId, UpsertAgentProfileRequest request) {
        ensureUser(userId);
        AgentProfile profile = AgentProfile.builder().userId(userId)
                .name(request.getName().trim()).description(normalize(request.getDescription()))
                .personality(toPersonality(request)).deleted(false).build();
        profileMapper.insertSelective(profile);
        return toResponse(profile);
    }

    /**
     * 查询用户可用的人格档案，并按需初始化内置人格。
     *
     * @param userId 用户标识
     * @return 人格档案列表
     */
    public List<AgentProfileResponse> list(Long userId) {
        ensureBuiltInProfiles(userId);
        return profileMapper.selectListByQuery(QueryWrapper.create()
                        .eq(AgentProfile::getUserId, userId).eq(AgentProfile::getDeleted, false)
                        .orderBy(AgentProfile::getUpdateTime, false))
                .stream().map(this::toResponse).toList();
    }

    /**
     * 为当前用户懒加载八种内置人格。表的 user_id 外键非空，因此按用户创建比全局伪用户数据更安全，
     * 且人格会像自定义人格一样被快照进对局，保证复盘一致性。
     */
    @Transactional(rollbackFor = Exception.class)
    public synchronized List<AgentProfile> ensureBuiltInProfiles(Long userId) {
        ensureUser(userId);
        List<AgentProfile> queried = profileMapper.selectListByQuery(QueryWrapper.create()
                .eq(AgentProfile::getUserId, userId).eq(AgentProfile::getDeleted, false));
        List<AgentProfile> existing = queried == null ? new ArrayList<>() : new ArrayList<>(queried);
        Map<String, AgentProfile> byCode = existing.stream()
                .filter(profile -> StringUtils.hasText(personalityString(profile, "code", null)))
                .collect(java.util.stream.Collectors.toMap(profile -> personalityString(profile, "code", null),
                        profile -> profile, (left, right) -> left, LinkedHashMap::new));
        for (AgentPersonaPreset preset : AgentPersonaPresets.ALL) {
            if (byCode.containsKey(preset.code())) continue;
            AgentProfile profile = AgentProfile.builder().userId(userId).name(preset.name())
                    .description(preset.description()).personality(presetPersonality(preset))
                    .deleted(false).build();
            profileMapper.insertSelective(profile);
            byCode.put(preset.code(), profile);
        }
        return AgentPersonaPresets.ALL.stream().map(preset -> byCode.get(preset.code())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新用户拥有的人格档案。
     *
     * @param userId 用户标识
     * @param profileId 人格档案标识
     * @param request 更新内容
     * @return 更新后的人格档案
     */
    public AgentProfileResponse update(Long userId, Long profileId, UpsertAgentProfileRequest request) {
        AgentProfile profile = getOwned(userId, profileId);
        profile.setName(request.getName().trim());
        profile.setDescription(normalize(request.getDescription()));
        profile.setPersonality(toPersonality(request));
        if (profileMapper.update(profile) != 1) {
            throw CustomException.create(ResultCodeEnum.FAIL, "人格更新失败，请重试");
        }
        return toResponse(profile);
    }

    @Transactional(rollbackFor = Exception.class)
    /**
     * 逻辑删除用户拥有的人格档案。
     *
     * @param userId 用户标识
     * @param profileId 人格档案标识
     */
    public void delete(Long userId, Long profileId) {
        AgentProfile profile = getOwned(userId, profileId);
        profile.setDeleted(true);
        if (profileMapper.update(profile) != 1) {
            throw CustomException.create(ResultCodeEnum.FAIL, "人格删除失败，请重试");
        }
    }

    /**
     * 获取并校验归属于指定用户的人格档案。
     *
     * @param userId 用户标识
     * @param profileId 人格档案标识
     * @return 用户拥有的人格档案
     */
    public AgentProfile getOwned(Long userId, Long profileId) {
        AgentProfile profile = profileId == null ? null : profileMapper.selectOneById(profileId);
        if (profile == null || userId == null || Boolean.TRUE.equals(profile.getDeleted())
                || !userId.equals(profile.getUserId())) {
            throw CustomException.create(ResultCodeEnum.RESOURCE_NOT_FOUND, "Agent 人格不存在");
        }
        return profile;
    }

    /**
     * 将持久化人格档案转换为对局中使用的不可变快照。
     *
     * @param profile 人格档案实体
     * @return 人格快照
     */
    public AgentPersonaSnapshot snapshot(AgentProfile profile) {
        Map<String, Object> personality = profile.getPersonality() == null ? Map.of() : profile.getPersonality();
        return AgentPersonaSnapshot.builder().profileId(profile.getId()).name(profile.getName())
                .code(stringValue(personality.get("code"), null))
                .description(profile.getDescription()).traits(stringList(personality.get("traits")))
                .speakingStyle(stringValue(personality.get("speakingStyle"), "自然、直接"))
                .riskPreference(stringValue(personality.get("riskPreference"), "BALANCED"))
                .reasoningPreference(stringValue(personality.get("reasoningPreference"), "结合公开发言与投票"))
                .behaviorGuidance(stringValue(personality.get("behaviorGuidance"), null))
                .promptInstruction(stringValue(personality.get("promptInstruction"),
                        stringValue(personality.get("behaviorGuidance"), null)))
                .builtIn(Boolean.TRUE.equals(personality.get("builtIn")))
                .avatarEmoji(stringValue(personality.get("avatarEmoji"), "AI"))
                .accentColor(stringValue(personality.get("accentColor"), "#8B7CFF")).build();
    }

    /**
     * 生成可安全暴露给游戏状态与客户端的人格字段。
     *
     * @param profile 人格档案实体
     * @return 公开人格字段映射
     */
    public Map<String, Object> publicSnapshot(AgentProfile profile) {
        AgentPersonaSnapshot snapshot = snapshot(profile);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentProfileCode", snapshot.getCode());
        result.put("agentProfileName", snapshot.getName());
        result.put("agentDescription", snapshot.getDescription());
        result.put("agentTraits", snapshot.getTraits());
        result.put("agentSpeakingStyle", snapshot.getSpeakingStyle());
        result.put("agentRiskPreference", snapshot.getRiskPreference());
        result.put("agentReasoningPreference", snapshot.getReasoningPreference());
        result.put("agentBehaviorGuidance", snapshot.getBehaviorGuidance());
        result.put("agentBuiltIn", snapshot.isBuiltIn());
        result.put("agentAvatarEmoji", snapshot.getAvatarEmoji());
        result.put("agentAccentColor", snapshot.getAccentColor());
        return result;
    }

    private Map<String, Object> toPersonality(UpsertAgentProfileRequest request) {
        Map<String, Object> personality = new LinkedHashMap<>();
        personality.put("traits", request.getTraits() == null ? List.of() : request.getTraits().stream().map(String::trim).toList());
        personality.put("speakingStyle", request.getSpeakingStyle().trim());
        personality.put("riskPreference", request.getRiskPreference());
        personality.put("behaviorGuidance", normalize(request.getBehaviorGuidance()));
        personality.put("avatarEmoji", StringUtils.hasText(request.getAvatarEmoji()) ? request.getAvatarEmoji().trim() : "AI");
        personality.put("accentColor", StringUtils.hasText(request.getAccentColor()) ? request.getAccentColor().toUpperCase() : "#8B7CFF");
        return personality;
    }

    private Map<String, Object> presetPersonality(AgentPersonaPreset preset) {
        Map<String, Object> personality = new LinkedHashMap<>();
        personality.put("code", preset.code());
        personality.put("builtIn", true);
        personality.put("traits", preset.publicTags());
        personality.put("publicTags", preset.publicTags());
        personality.put("speakingStyle", preset.speakingStyle());
        personality.put("riskPreference", preset.riskPreference());
        personality.put("reasoningPreference", preset.reasoningPreference());
        personality.put("promptInstruction", preset.promptInstruction());
        personality.put("behaviorGuidance", preset.promptInstruction());
        personality.put("avatarEmoji", preset.avatarEmoji());
        personality.put("accentColor", preset.accentColor());
        return personality;
    }

    private AgentProfileResponse toResponse(AgentProfile profile) {
        AgentPersonaSnapshot snapshot = snapshot(profile);
        return AgentProfileResponse.builder().id(profile.getId()).code(snapshot.getCode()).name(profile.getName())
                .description(profile.getDescription()).traits(snapshot.getTraits())
                .speakingStyle(snapshot.getSpeakingStyle()).riskPreference(snapshot.getRiskPreference())
                .reasoningPreference(snapshot.getReasoningPreference()).builtIn(snapshot.isBuiltIn())
                .behaviorGuidance(snapshot.getBehaviorGuidance()).avatarEmoji(snapshot.getAvatarEmoji())
                .accentColor(snapshot.getAccentColor()).createTime(profile.getCreateTime()).updateTime(profile.getUpdateTime()).build();
    }

    private void ensureUser(Long userId) {
        if (userId == null || userMapper.selectOneById(userId) == null) {
            throw CustomException.create(ResultCodeEnum.USER_NOT_FOUND);
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String stringValue(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private String personalityString(AgentProfile profile, String key, String fallback) {
        return profile.getPersonality() == null ? fallback : stringValue(profile.getPersonality().get(key), fallback);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        return list.stream().map(String::valueOf).toList();
    }
}
