package com.zn.agentarena.modules.model.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.config.AgentArenaProperties;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * LangChain4j模型调用网关单元测试。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@DisplayName("LangChain4j模型调用网关单元测试")
class LangChain4jModelGatewayTest {

    private ModelFactory modelFactory;
    private ChatModel chatModel;
    private LangChain4jModelGateway modelGateway;
    private ModelRuntimeConfig config;

    @BeforeEach
    void setUp() {
        modelFactory = mock(ModelFactory.class);
        chatModel = mock(ChatModel.class);
        modelGateway = new LangChain4jModelGateway(modelFactory, new ObjectMapper(),
                new ModelCallConcurrencyLimiter(new AgentArenaProperties(null, null, null)));
        config = new ModelRuntimeConfig(ModelProvider.OPENAI, "test-model", null,
                "test-key", 0.2, 512, Duration.ofSeconds(10), 1);
        when(modelFactory.create(config)).thenReturn(chatModel);
    }

    @Test
    @DisplayName("generateStructured: 应将模型JSON转换为指定类型")
    void testGenerateStructuredSuccess() {
        when(chatModel.chat("请选择行动")).thenReturn("""
                ```json
                {"action":"VOTE","targetPlayerId":5,"reasonSummary":"5号发言矛盾"}
                ```
                """);

        TestDecision decision = modelGateway.generateStructured(config, "请选择行动", TestDecision.class);

        assertEquals("VOTE", decision.action());
        assertEquals(5L, decision.targetPlayerId());
        assertEquals("5号发言矛盾", decision.reasonSummary());
    }

    @Test
    @DisplayName("generateStructured: 非法JSON应抛出模型响应异常")
    void testGenerateStructuredInvalidJsonShouldThrowException() {
        when(chatModel.chat("请选择行动")).thenReturn("我选择投票5号");

        CustomException exception = assertThrows(CustomException.class,
                () -> modelGateway.generateStructured(config, "请选择行动", TestDecision.class));

        assertEquals(ResultCodeEnum.MODEL_RESPONSE_INVALID, exception.getResultCodeEnum());
    }

    private record TestDecision(String action, Long targetPlayerId, String reasonSummary) {
    }
}
