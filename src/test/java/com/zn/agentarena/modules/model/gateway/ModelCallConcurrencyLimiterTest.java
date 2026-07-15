package com.zn.agentarena.modules.model.gateway;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.config.AgentArenaProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCallConcurrencyLimiterTest {

    @Test
    void rejectsCallsBeyondTheConfiguredInstanceLimit() throws Exception {
        AgentArenaProperties.Model model = new AgentArenaProperties.Model(
                null, null, null, List.of(), 1);
        ModelCallConcurrencyLimiter limiter = new ModelCallConcurrencyLimiter(
                new AgentArenaProperties(model, null, null));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> limiter.execute(() -> {
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return "done";
        }));

        assertTrue(entered.await(2, TimeUnit.SECONDS));
        CustomException exception = assertThrows(CustomException.class,
                () -> limiter.execute(() -> "second"));
        assertEquals(ResultCodeEnum.TOO_MANY_REQUESTS, exception.getResultCodeEnum());

        release.countDown();
        assertEquals("done", first.get(2, TimeUnit.SECONDS));
    }
}
