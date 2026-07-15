package com.zn.agentarena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Agent Arena 应用上下文测试。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@ActiveProfiles("test")
@SpringBootTest
@DisplayName("Agent Arena应用上下文测试")
class AgentArenaApplicationTests {

    @Test
    @DisplayName("contextLoads: 应成功加载应用上下文")
    void contextLoads() {
    }
}
