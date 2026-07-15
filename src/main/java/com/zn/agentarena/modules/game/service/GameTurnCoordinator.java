package com.zn.agentarena.modules.game.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Serializes mutations of one game inside this service instance. Database versions provide the cross-instance guard. */
@Component
public class GameTurnCoordinator {

    private final ConcurrentHashMap<Long, LockHolder> locks = new ConcurrentHashMap<>();

    public <T> T withGameLock(Long gameId, Supplier<T> operation) {
        LockHolder holder = locks.compute(gameId, (ignored, current) -> {
            LockHolder selected = current == null ? new LockHolder() : current;
            selected.references++;
            return selected;
        });
        holder.lock.lock();
        try {
            return operation.get();
        } finally {
            holder.lock.unlock();
            locks.computeIfPresent(gameId, (ignored, current) -> {
                if (current != holder) {
                    return current;
                }
                current.references--;
                return current.references == 0 ? null : current;
            });
        }
    }

    private static final class LockHolder {
        private final ReentrantLock lock = new ReentrantLock(true);
        /** Accessed only while holding the ConcurrentHashMap key-computation lock. */
        private int references;
    }
}
