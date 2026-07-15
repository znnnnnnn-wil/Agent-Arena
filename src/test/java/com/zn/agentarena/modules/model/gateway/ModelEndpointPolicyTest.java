package com.zn.agentarena.modules.model.gateway;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.config.AgentArenaProperties;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelEndpointPolicyTest {

    private final ModelEndpointPolicy policy = new ModelEndpointPolicy(
            new AgentArenaProperties(null, null, null));

    @Test
    void rejectsLoopbackPrivateAndPlainHttpEndpoints() {
        assertRejected("https://127.0.0.1:8080/v1");
        assertRejected("https://10.10.0.5/v1");
        assertRejected("http://8.8.8.8/v1");
    }

    @Test
    void customProviderRequiresAnExplicitEndpoint() {
        CustomException exception = assertThrows(CustomException.class,
                () -> policy.validate(ModelProvider.OPENAI_COMPATIBLE, null));
        assertEquals(ResultCodeEnum.PARAMS_ERROR, exception.getResultCodeEnum());
    }

    @Test
    void builtInProviderMayUseItsManagedDefaultEndpoint() {
        assertDoesNotThrow(() -> policy.validate(ModelProvider.DEEPSEEK, null));
    }

    @Test
    void explicitlyAllowlistedPublicHostIsAccepted() {
        AgentArenaProperties.Model model = new AgentArenaProperties.Model(null, null, null,
                List.of("8.8.8.8"), null);
        ModelEndpointPolicy allowlistedPolicy = new ModelEndpointPolicy(
                new AgentArenaProperties(model, null, null));

        assertDoesNotThrow(() -> allowlistedPolicy.validate(
                ModelProvider.OPENAI_COMPATIBLE, "https://8.8.8.8/v1"));
    }

    private void assertRejected(String endpoint) {
        assertThrows(CustomException.class,
                () -> policy.validate(ModelProvider.OPENAI_COMPATIBLE, endpoint));
    }
}
