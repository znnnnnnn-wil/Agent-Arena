package com.zn.agentarena.config.security;

import com.zn.agentarena.modules.user.service.IUserService;
import com.zn.agentarena.modules.user.dto.response.AuthSessionResponse;
import com.zn.agentarena.modules.user.dto.response.CurrentUserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IUserService userService;

    @Test
    void privateApiRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/users/visitor/verify"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A0230"));
    }

    @Test
    void visitorTokenBecomesTheOnlyAuthenticatedIdentity() throws Exception {
        when(userService.getUserIdByVisitorToken("valid-token")).thenReturn(42L);
        when(userService.getCurrentUser(42L)).thenReturn(CurrentUserResponse.builder()
                .userId(42L).nickname("测试用户").registered(false).build());

        mockMvc.perform(get("/api/users/visitor/verify")
                        .header(VisitorTokenAuthenticationFilter.HEADER, "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(42));
    }

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void anonymousUserCanOpenLoginAndRegistrationEndpoints() throws Exception {
        var session = AuthSessionResponse.builder().userId(8L).nickname("Arena").username("arena")
                .visitorToken("new-token").registered(true).build();
        when(userService.login(any())).thenReturn(session);
        when(userService.registerAccount(isNull(), any())).thenReturn(session);

        mockMvc.perform(post("/api/users/login")
                        .contentType("application/json")
                        .content("{\"username\":\"arena\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registered").value(true));

        mockMvc.perform(post("/api/users/register")
                        .contentType("application/json")
                        .content("{\"username\":\"arena\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(8));
    }
}
