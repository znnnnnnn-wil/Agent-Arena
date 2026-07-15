package com.zn.agentarena.modules.spectator.service;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.config.AgentArenaProperties;
import com.zn.agentarena.modules.spectator.dto.response.GameEventView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 游戏事件SSE推送服务。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameEventStreamService {

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final AgentArenaProperties properties;
    private final AtomicInteger totalSubscribers = new AtomicInteger();

    /**
     * 订阅指定游戏的事件流。
     *
     * @param gameId 游戏ID
     * @return SSE发射器
     */
    public SseEmitter subscribe(Long gameId) {
        if (totalSubscribers.incrementAndGet() > properties.stream().maxTotalSubscribers()) {
            totalSubscribers.decrementAndGet();
            throw CustomException.create(ResultCodeEnum.TOO_MANY_REQUESTS, "实时观战连接数已满");
        }
        SseEmitter emitter = new SseEmitter(0L);
        CopyOnWriteArrayList<SseEmitter> gameEmitters =
                emitters.computeIfAbsent(gameId, ignored -> new CopyOnWriteArrayList<>());
        synchronized (gameEmitters) {
            if (gameEmitters.size() >= properties.stream().maxSubscribersPerGame()) {
                totalSubscribers.decrementAndGet();
                throw CustomException.create(ResultCodeEnum.TOO_MANY_REQUESTS, "该对局观战连接数已满");
            }
            gameEmitters.add(emitter);
        }
        emitter.onCompletion(() -> remove(gameId, emitter));
        emitter.onTimeout(() -> remove(gameId, emitter));
        emitter.onError(ignored -> remove(gameId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(
                    Map.of("gameId", gameId, "message", "观战连接已建立"), MediaType.APPLICATION_JSON));
        } catch (IOException exception) {
            remove(gameId, emitter);
        }
        return emitter;
    }

    /**
     * 向指定游戏的全部观众推送事件。
     *
     * @param event 公开事件
     */
    public void publish(GameEventView event) {
        List<SseEmitter> gameEmitters = emitters.getOrDefault(event.gameId(), new CopyOnWriteArrayList<>());
        for (SseEmitter emitter : gameEmitters) {
            try {
                emitter.send(SseEmitter.event().id(String.valueOf(event.sequenceNo()))
                        .name("game-event").data(event, MediaType.APPLICATION_JSON));
            } catch (Exception exception) {
                remove(event.gameId(), emitter);
            }
        }
    }

    /** Keeps proxies from silently dropping otherwise idle SSE connections. */
    @Scheduled(fixedRateString = "${agent-arena.stream.heartbeat-interval:20s}")
    public void heartbeat() {
        emitters.forEach((gameId, gameEmitters) -> gameEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception exception) {
                remove(gameId, emitter);
            }
        }));
    }

    private void remove(Long gameId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> gameEmitters = emitters.get(gameId);
        if (gameEmitters != null) {
            if (gameEmitters.remove(emitter)) {
                totalSubscribers.decrementAndGet();
            }
            if (gameEmitters.isEmpty()) {
                emitters.remove(gameId);
            }
        }
    }
}
