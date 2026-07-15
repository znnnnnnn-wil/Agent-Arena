package com.zn.agentarena.modules.base.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 基础健康检查控制器测试。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@DisplayName("基础健康检查控制器测试")
class BaseHealthControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BaseHealthController()).build();
    }

    @Test
    @DisplayName("health: 应返回统一格式的服务状态")
    void testHealthSuccess() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.service").value("agent-arena"));
    }

    @Test
    @DisplayName("notFound: 不存在的资源应返回统一404响应")
    void testNotFound() throws Exception {
        mockMvc.perform(get("/api/not-exists"))
                .andExpect(status().isNotFound());
    }
}
