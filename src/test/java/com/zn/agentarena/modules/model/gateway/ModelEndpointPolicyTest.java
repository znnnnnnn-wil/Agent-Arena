package com.zn.agentarena.modules.model.gateway;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.config.AgentArenaProperties;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelEndpointPolicyTest {

    private final ModelEndpointPolicy policy = new ModelEndpointPolicy(
            new AgentArenaProperties(null, null, null));

    @Test
    void actualTransportRejectsPrivateDnsBeforeOpeningAConnection() {
        AtomicInteger resolutions = new AtomicInteger();
        ModelEndpointPolicy privatePolicy = new ModelEndpointPolicy(new AgentArenaProperties(null, null, null),
                host -> { resolutions.incrementAndGet(); return new InetAddress[]{InetAddress.getByAddress(new byte[]{127, 0, 0, 1})}; });
        var client = new ValidatedModelHttpClientBuilder(privatePolicy, "https://model.example/v1").build();
        var request = dev.langchain4j.http.client.HttpRequest.builder()
                .method(dev.langchain4j.http.client.HttpMethod.POST)
                .url("https://model.example/v1/chat/completions").body("{}").build();

        assertThrows(CustomException.class, () -> client.execute(request));
        assertEquals(1, resolutions.get());
    }

    @Test
    void transportRejectsDnsThatChangesToLoopbackAfterConfigurationValidation() throws Exception {
        AgentArenaProperties.Model model = new AgentArenaProperties.Model(null, null, null,
                List.of("model.example"), null);
        AtomicInteger resolutions = new AtomicInteger();
        ModelEndpointPolicy changingPolicy = new ModelEndpointPolicy(new AgentArenaProperties(model, null, null),
                host -> new InetAddress[]{InetAddress.getByAddress(resolutions.getAndIncrement() == 0
                        ? new byte[]{8, 8, 8, 8} : new byte[]{127, 0, 0, 1})});
        changingPolicy.validate(ModelProvider.OPENAI_COMPATIBLE, "https://model.example/v1");
        var transport = new ValidatedModelHttpClientBuilder(changingPolicy, "https://model.example/v1");

        CustomException exception = assertThrows(CustomException.class,
                () -> transport.dnsResolver().resolve("model.example"));

        assertEquals(ResultCodeEnum.NO_PERMISSION, exception.getResultCodeEnum());
        assertEquals(2, resolutions.get());
    }

    @Test
    void transportReturnsTheCheckedAddressesWithoutAnotherDnsLookup() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        ModelEndpointPolicy fixedPolicy = new ModelEndpointPolicy(new AgentArenaProperties(null, null, null),
                host -> { resolutions.incrementAndGet(); return new InetAddress[]{InetAddress.getByAddress(new byte[]{8, 8, 8, 8})}; });
        var transport = new ValidatedModelHttpClientBuilder(fixedPolicy, "https://model.example/v1");

        assertEquals("8.8.8.8", transport.dnsResolver().resolve("model.example")[0].getHostAddress());
        assertEquals(1, resolutions.get());
        assertThrows(java.net.UnknownHostException.class, () -> transport.dnsResolver().resolve("other.example"));
    }

    @Test
    void rejectsReservedAddressesEvenWhenAllowlisted() {
        for (String host : List.of("100.64.0.1", "198.18.0.1", "192.0.2.1", "203.0.113.1")) {
            AgentArenaProperties.Model model = new AgentArenaProperties.Model(null, null, null, List.of(host), null);
            ModelEndpointPolicy allowlisted = new ModelEndpointPolicy(new AgentArenaProperties(model, null, null));
            assertThrows(CustomException.class, () -> allowlisted.validate(ModelProvider.OPENAI_COMPATIBLE,
                    "https://" + host + "/v1"));
        }
    }

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
