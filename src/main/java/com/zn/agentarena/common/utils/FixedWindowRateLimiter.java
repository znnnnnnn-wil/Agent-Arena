package com.zn.agentarena.common.utils;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Small per-instance safety limiter; distributed deployments should replace it with Redis. */
@Component
public class FixedWindowRateLimiter {

    private final Clock clock = Clock.systemUTC();
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public void check(Long userId, String operation, int limitPerMinute) {
        check(String.valueOf(userId), operation, limitPerMinute);
    }

    public void check(String subject, String operation, int limitPerMinute) {
        long minute = clock.millis() / 60_000L;
        String key = subject + ":" + operation;
        Window window = windows.compute(key, (ignored, current) ->
                current == null || current.minute != minute ? new Window(minute) : current);
        if (window.count.incrementAndGet() > limitPerMinute) {
            throw CustomException.create(ResultCodeEnum.TOO_MANY_REQUESTS);
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> entry.getValue().minute < minute - 1);
        }
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
