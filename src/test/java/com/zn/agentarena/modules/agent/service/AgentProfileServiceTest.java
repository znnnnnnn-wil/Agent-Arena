package com.zn.agentarena.modules.agent.service;

import com.zn.agentarena.dao.entity.AgentProfile;
import com.zn.agentarena.dao.entity.User;
import com.zn.agentarena.dao.mapper.AgentProfileMapper;
import com.zn.agentarena.dao.mapper.UserMapper;
import com.zn.agentarena.modules.agent.dto.request.UpsertAgentProfileRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentProfileServiceTest {

    @Test
    void createsTypedPersonaSnapshot() {
        AgentProfileMapper mapper = mock(AgentProfileMapper.class);
        UserMapper users = mock(UserMapper.class);
        when(users.selectOneById(1L)).thenReturn(User.builder().id(1L).build());
        doAnswer(invocation -> {
            AgentProfile profile = invocation.getArgument(0);
            profile.setId(7L);
            return 1;
        }).when(mapper).insertSelective(any());
        UpsertAgentProfileRequest request = new UpsertAgentProfileRequest();
        request.setName("冒险家");
        request.setDescription("偏爱主动制造冲突");
        request.setTraits(List.of("果断", "外向"));
        request.setSpeakingStyle("短句、直接");
        request.setRiskPreference("BOLD");
        request.setBehaviorGuidance("优势时主动施压");
        request.setAvatarEmoji("⚡");
        request.setAccentColor("#ff8844");

        var response = new AgentProfileService(mapper, users).create(1L, request);

        assertEquals(7L, response.getId());
        assertEquals("BOLD", response.getRiskPreference());
        assertEquals("#FF8844", response.getAccentColor());
        ArgumentCaptor<AgentProfile> captor = ArgumentCaptor.forClass(AgentProfile.class);
        verify(mapper).insertSelective(captor.capture());
        assertEquals(List.of("果断", "外向"), captor.getValue().getPersonality().get("traits"));
    }

    @Test
    void lazilyCreatesEightDistinctBuiltInPersonasForUser() {
        AgentProfileMapper mapper = mock(AgentProfileMapper.class);
        UserMapper users = mock(UserMapper.class);
        when(users.selectOneById(1L)).thenReturn(User.builder().id(1L).build());
        when(mapper.selectListByQuery(any())).thenReturn(List.of());
        List<AgentProfile> inserted = new ArrayList<>();
        doAnswer(invocation -> {
            AgentProfile profile = invocation.getArgument(0);
            profile.setId(100L + inserted.size());
            inserted.add(profile);
            return 1;
        }).when(mapper).insertSelective(any());

        var profiles = new AgentProfileService(mapper, users).ensureBuiltInProfiles(1L);

        assertEquals(8, profiles.size());
        assertEquals(8, inserted.size());
        assertEquals(8, profiles.stream().map(profile -> profile.getPersonality().get("code")).distinct().count());
        assertTrue(inserted.stream().allMatch(profile -> Boolean.TRUE.equals(profile.getPersonality().get("builtIn"))));
        assertTrue(inserted.stream().allMatch(profile -> profile.getPersonality().containsKey("reasoningPreference")));
        assertTrue(inserted.stream().allMatch(profile -> profile.getPersonality().containsKey("promptInstruction")));
    }
}
