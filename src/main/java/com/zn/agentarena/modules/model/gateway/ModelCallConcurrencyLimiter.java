package com.zn.agentarena.modules.model.gateway;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.config.AgentArenaProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/** Global per-instance guard that prevents slow providers from exhausting every servlet thread. */
@Component
public class ModelCallConcurrencyLimiter {

    private final Semaphore permits;

    public ModelCallConcurrencyLimiter(AgentArenaProperties properties) {
        this.permits = new Semaphore(properties.model().maxConcurrentCalls(), true);
    }

    public <T> T execute(Supplier<T> operation) {
        if (!permits.tryAcquire()) {
            throw CustomException.create(ResultCodeEnum.TOO_MANY_REQUESTS,
                    "模型调用并发已满，请稍后重试");
        }
        try {
            return operation.get();
        } finally {
            permits.release();
        }
    }
}
